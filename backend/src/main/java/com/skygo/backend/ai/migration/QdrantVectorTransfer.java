package com.skygo.backend.ai.migration;

import com.skygo.backend.ai.config.AiProperties;
import com.skygo.backend.ai.config.MariaDbVectorSchema;
import com.skygo.backend.ai.index.KnowledgeDocuments;
import com.skygo.backend.ai.index.StoredKnowledgeDocument;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** One-time REST reader + JDBC importer. No model/embedding dependency, no source mutation or DDL. */
public final class QdrantVectorTransfer {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final HttpClient http;
  private final URI source;
  private final String collection;
  private final String apiKey;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transaction;
  private final AiProperties ai;
  private final int maxDocuments;

  public QdrantVectorTransfer(HttpClient http, URI source, String collection, String apiKey,
      JdbcTemplate jdbc, TransactionTemplate transaction, AiProperties ai, int maxDocuments) {
    if (!List.of("http","https").contains(source.getScheme()) || source.getHost()==null
        || source.getUserInfo()!=null || source.getQuery()!=null || source.getFragment()!=null
        || !(source.getPath().isEmpty() || source.getPath().equals("/"))
        || !collection.matches("[a-zA-Z0-9_-]{1,200}") || maxDocuments<1 || maxDocuments>500000)
      throw new IllegalArgumentException("Invalid transfer source/settings.");
    this.http=http; this.source=URI.create(source.toString().replaceAll("/+$",""));
    this.collection=collection; this.apiKey=apiKey; this.jdbc=jdbc;
    this.transaction=transaction; this.ai=ai; this.maxDocuments=maxDocuments;
  }

  public Report transfer(boolean apply) {
    MariaDbVectorSchema.validate(jdbc,ai);
    var info=request("",null).path("result").path("config").path("params").path("vectors");
    if (info.path("size").asInt()!=ai.embeddingDimensions() || !"Cosine".equalsIgnoreCase(info.path("distance").asText()))
      throw invalid("Source model dimensions/metric are incompatible (named vectors are unsupported).");
    long expected=count();
    if (expected<=0 || expected>maxDocuments) throw invalid("Source is empty or exceeds max-documents; nothing was written.");
    var points=new LinkedHashMap<String,EmbeddedDocument>();
    var offsets=new HashSet<String>();
    JsonNode offset=null;
    do {
      var body=new LinkedHashMap<String,Object>();
      body.put("limit",128); body.put("with_payload",true); body.put("with_vector",true); body.put("filter",filter());
      if (offset!=null) body.put("offset",offset);
      var page=request("/points/scroll",body).path("result");
      if (!page.path("points").isArray()) throw invalid("Incomplete source page.");
      for (var point:page.path("points")) {
        String id=point.path("id").asText();
        var payload=point.path("payload");
        if (!payload.isObject() || !payload.path("doc_content").isTextual()) throw invalid("Invalid source payload.");
        @SuppressWarnings("unchecked")
        var metadata=new LinkedHashMap<String,Object>(JSON.convertValue(payload, Map.class));
        String content=(String)metadata.remove("doc_content");
        var doc=Document.builder().id(id).text(content).metadata(metadata).build();
        StoredKnowledgeDocument.validate(doc,ai);
        var values=point.path("vector");
        if (!values.isArray() || values.size()!=ai.embeddingDimensions()) throw invalid("Invalid source vector.");
        var vector=new float[values.size()];
        for (int i=0;i<vector.length;i++) {
          if (!values.get(i).isNumber()) throw invalid("Non-numeric source vector.");
          vector[i]=(float)values.get(i).asDouble();
        }
        StoredKnowledgeDocument.validateVector(vector,ai.embeddingDimensions());
        if (points.put(id,new EmbeddedDocument(doc,vector))!=null || points.size()>maxDocuments)
          throw invalid("Duplicate source ID or too many source documents.");
      }
      offset=page.get("next_page_offset");
      if (offset!=null && offset.isNull()) offset=null;
      if (offset!=null && !offsets.add(offset.toString())) throw invalid("Repeated source page offset.");
    } while(offset!=null);
    if (points.size()!=expected || count()!=expected)
      throw invalid("Source changed or was only partially read. Stop index writers and retry.");

    // The network scan is complete before opening the SQL write transaction.
    return transaction.execute(status -> {
      var existing=jdbc.query("SELECT CAST(id AS CHAR) AS id,content,metadata,VEC_ToText(embedding) AS vector_text FROM ai_document FOR UPDATE",
          (rs,row) -> {
            var doc=Document.builder().id(rs.getString("id")).text(rs.getString("content"))
                .metadata(readMetadata(rs.getString("metadata"))).build();
            return new EmbeddedDocument(doc,JSON.readValue(rs.getString("vector_text"),float[].class));
          });
      for (var stored:existing) {
        var old=points.get(stored.document().getId());
        if (old==null || !equivalent(old,stored))
          throw invalid("Target has conflicting/extra documents. Existing rows were not overwritten.");
      }
      var existingIds=new HashSet<String>();
      existing.forEach(p -> existingIds.add(p.document().getId()));
      int missing=points.size()-existingIds.size();
      if (!apply) {
        status.setRollbackOnly();
        return new Report(points.size(),missing,existingIds.size(),false);
      }
      for (var point:points.values()) {
        if (existingIds.contains(point.document().getId())) continue;
        jdbc.update("INSERT INTO ai_document (id,content,metadata,embedding) VALUES (?,?,?,VEC_FromText(?))",
            point.document().getId(),point.document().getText(),JSON.writeValueAsString(point.document().getMetadata()),
            JSON.writeValueAsString(point.vector()));
      }
      Long total=jdbc.queryForObject("SELECT COUNT(*) FROM ai_document",Long.class);
      if (total==null || total!=points.size()) throw invalid("Target count verification failed; rolling back.");
      // Verify exact metadata/content and float values, including both newly inserted and resumed rows.
      jdbc.query("SELECT CAST(id AS CHAR) AS id,content,metadata,VEC_ToText(embedding) AS vector_text FROM ai_document", rs -> {
        var doc=Document.builder().id(rs.getString("id")).text(rs.getString("content"))
            .metadata(readMetadata(rs.getString("metadata"))).build();
        var stored=new EmbeddedDocument(doc,JSON.readValue(rs.getString("vector_text"),float[].class));
        if (!equivalent(points.get(doc.getId()),stored)) throw invalid("Target content/vector verification failed; rolling back.");
      });
      return new Report(points.size(),missing,existingIds.size(),true);
    });
  }

  private long count() {
    var result=request("/points/count",Map.of("exact",true,"filter",filter())).path("result").path("count");
    if (!result.isIntegralNumber()) throw invalid("Missing source count.");
    return result.asLong();
  }
  private Map<String,Object> filter() {
    return Map.of("must",List.of(
        Map.of("key","managed_by","match",Map.of("value",KnowledgeDocuments.MANAGED_BY)),
        Map.of("key","embedding_model","match",Map.of("value",ai.embeddingModel()))));
  }
  private JsonNode request(String path,Object body) {
    try {
      var builder=HttpRequest.newBuilder(URI.create(source+"/collections/"+collection+path)).timeout(Duration.ofSeconds(60));
      if (apiKey!=null && !apiKey.isBlank()) builder.header("api-key",apiKey);
      if (body==null) builder.GET();
      else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
      var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
      if (response.statusCode()!=200) throw invalid("Source read failed (HTTP "+response.statusCode()+").");
      return JSON.readTree(response.body());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt(); throw invalid("Source read interrupted; nothing was written.");
    } catch (java.io.IOException ex) { throw invalid("Cannot read source; nothing was written."); }
  }
  @SuppressWarnings("unchecked")
  private static Map<String,Object> readMetadata(String json) { return JSON.readValue(json,Map.class); }
  private static boolean equivalent(EmbeddedDocument a,EmbeddedDocument b) {
    if(a==null || !a.document().getText().equals(b.document().getText())
        || !JSON.valueToTree(a.document().getMetadata()).equals(JSON.valueToTree(b.document().getMetadata()))
        || a.vector().length!=b.vector().length) return false;
    for(int i=0;i<a.vector().length;i++)
      if(!Float.isFinite(b.vector()[i]) || Math.abs(a.vector()[i]-b.vector()[i])>0.000001) return false;
    return true;
  }
  private static IllegalStateException invalid(String reason) { return new IllegalStateException(reason); }
  private record EmbeddedDocument(Document document,float[] vector) {}
  public record Report(int sourceDocuments,int toInsert,int unchanged,boolean applied) {}
}
