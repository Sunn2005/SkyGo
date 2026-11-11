package com.skygo.backend.ai;

import static com.skygo.backend.ai.AiTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.skygo.backend.ai.chat.*;
import com.skygo.backend.ai.chat.contract.AiChatResponse.Airport;
import com.skygo.backend.ai.config.*;
import com.skygo.backend.ai.index.*;
import com.skygo.backend.ai.migration.QdrantVectorTransfer;
import com.skygo.backend.ai.service.AiException;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.boot.SpringApplication;
import com.skygo.backend.BackendApplication;
import tools.jackson.databind.json.JsonMapper;

/** Creates/drops only its own UUID-named _test DB. Real MariaDB, fake embeddings, local HTTP stub. */
@EnabledIfEnvironmentVariable(named="SKYGO_VECTOR_DB_TEST",matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MariaDbVectorIntegrationTest {
  final JsonMapper json=JsonMapper.builder().build();
  final AtomicInteger embedded=new AtomicInteger();
  final String database="skygo_vector_test_"+UUID.randomUUID().toString().replace("-","");
  JdbcTemplate jdbc,admin;
  DriverManagerDataSource ds;
  MariaDBVectorStore vectors;
  EmbeddingModel embedding;
  boolean created;

  @BeforeAll void createIsolatedDatabaseAndRunAllMigrations() throws Exception {
    String testUrl=System.getenv("SKYGO_TEST_DB_URL");
    if(testUrl==null || !testUrl.matches("jdbc:mariadb://[^/]+/[a-zA-Z0-9_]*_test[a-zA-Z0-9_]*(\\?.*)?"))
      throw new IllegalArgumentException("An explicit _test JDBC URL is required, never the main database.");
    var env=new StandardEnvironment(); var dotenv=new HashMap<String,Object>();
    for(var file:List.of(Path.of("../.env"),Path.of(".env"))) {
      if(!Files.isRegularFile(file)) continue;
      for(String raw:Files.readAllLines(file,StandardCharsets.UTF_8)) {
        var pair=raw.strip().split("=",2);
        if(pair.length!=2 || !pair[0].startsWith("DB_")) continue;
        String value=pair[1].strip();
        if(value.startsWith("\"") || value.startsWith("'")) value=value.substring(1,value.indexOf(value.charAt(0),1));
        else value=value.replaceFirst("\\s+#.*$","").strip();
        dotenv.put(pair[0],value);
      }
    }
    env.getPropertySources().addLast(new MapPropertySource("test-dotenv",dotenv));
    var app=new Properties();
    try(var reader=Files.newBufferedReader(Path.of("src/main/resources/application.properties"),StandardCharsets.UTF_8)) { app.load(reader); }
    env.getPropertySources().addLast(new PropertiesPropertySource("test-application",app));
    String user=System.getenv().getOrDefault("SKYGO_TEST_DB_USERNAME",env.getProperty("spring.datasource.username","root"));
    String password=System.getenv().getOrDefault("SKYGO_TEST_DB_PASSWORD",env.getProperty("spring.datasource.password",""));
    String server=testUrl.substring(0,testUrl.indexOf('/',"jdbc:mariadb://".length())+1);
    admin=new JdbcTemplate(new DriverManagerDataSource(server,user,password));
    if(!database.matches("skygo_vector_test_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test DB name.");
    admin.execute("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
    created=true;
    ds=new DriverManagerDataSource(server+database,user,password); jdbc=new JdbcTemplate(ds);
    Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
  }
  @AfterAll void removeOnlyThisTestsDatabase() {
    if(created && database.matches("skygo_vector_test_[a-f0-9]{32}")) admin.execute("DROP DATABASE `"+database+"`");
  }
  @BeforeEach void resetVectorsOnlyInIsolatedDb() {
    jdbc.update("DELETE FROM ai_document"); embedded.set(0);
    embedding=mock(EmbeddingModel.class);
    when(embedding.dimensions()).thenReturn(768);
    when(embedding.embed(anyString())).thenReturn(vector());
    when(embedding.embed(anyList(),any(EmbeddingOptions.class),any(BatchingStrategy.class))).thenAnswer(call -> {
      List<Document> documents=call.getArgument(0); embedded.addAndGet(documents.size());
      return documents.stream().map(doc -> vector()).toList();
    });
    vectors=MariaDBVectorStore.builder(jdbc,embedding).vectorTableName("ai_document").dimensions(768)
        .initializeSchema(false).schemaValidation(true).build();
    vectors.afterPropertiesSet();
    MariaDbVectorSchema.validate(jdbc,properties());
  }
  Document doc(String type,String key,Long owner) {
    return KnowledgeDocuments.create(properties(),type,key,"Title "+key,"Document "+key,
        type.equals("airport")?Map.of("airport_code",key):Map.of(),owner,Instant.parse("2026-11-10T12:00:00Z"));
  }
  MariaDbKnowledgeIndexStore indexStore() {
    return new MariaDbKnowledgeIndexStore(jdbc,vectors,properties(),
        new AiIndexProperties(true,Duration.ofMinutes(5),8,Duration.ZERO,30,15,100),new DataSourceTransactionManager(ds));
  }

  @Test void cosineSearchPreservesScoresMetadataAndPublicTypeFilters() {
    var airport=doc("airport","PQC",null);
    vectors.add(List.of(airport,doc("booking_summary","owner-73",73L),doc("flight_fare","snapshot-price",null)));
    var result=new AiKnowledgeReader(vectors,properties()).search("island");
    assertThat(result).extracting(AiKnowledgeReader.Hit::id).containsExactly(airport.getId());
    var hits=vectors.similaritySearch(SearchRequest.builder().query("island").similarityThreshold(.99).build());
    assertThat(hits).hasSize(3).allSatisfy(hit -> assertThat(hit.getScore()).isCloseTo(1d,within(.00001)));
    var pqc=new Airport("PQC","Phú Quốc","Phú Quốc","Việt Nam","Asia/Ho_Chi_Minh");
    var session=new AiAirportResolver(vectors,properties(),new AiAirportProperties(5,.65,.85,.1)).newSession();
    assertThat(session.resolve("đảo ngọc","destination",List.of(pqc))).isEqualTo(pqc);
    assertThat(session.sources()).extracting(s -> s.type()).containsOnly("MARIADB_VECTOR");
  }

  @Test void unchangedDocumentsDoNotCallEmbeddingAgainAndStaleDeletionIsOwned() {
    var first=doc("airport","PQC",null); var stale=doc("route","old",null);
    var source=mock(KnowledgeSource.class);
    when(source.snapshot(any())).thenReturn(List.of(first,stale),List.of(first));
    var settings=new AiIndexProperties(true,Duration.ofMinutes(5),8,Duration.ZERO,30,15,100);
    var indexer=new KnowledgeIndexer(source,indexStore(),settings,Clock.systemUTC());
    assertThat(indexer.synchronize().upserted()).isEqualTo(2);
    assertThat(indexer.synchronize().unchanged()).isEqualTo(1);
    assertThat(embedded.get()).isEqualTo(2);
    assertThat(indexStore().hashes()).containsOnlyKeys(first.getId());
    var foreign=doc("route","foreign",null); var metadata=new HashMap<>(foreign.getMetadata()); metadata.put("managed_by","other");
    jdbc.update("INSERT INTO ai_document(id,content,metadata,embedding) VALUES(?,?,?,VEC_FromText(?))",
        foreign.getId(),foreign.getText(),json.writeValueAsString(metadata),json.writeValueAsString(vector()));
    indexStore().delete(List.of(foreign.getId()));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_document WHERE id=?",Integer.class,foreign.getId())).isEqualTo(1);
  }

  @Test void incompatibleConfigurationFailsBeforeEmbeddingAndLeavesExistingData() {
    vectors.add(List.of(doc("airport","PQC",null))); clearInvocations(embedding);
    assertThatThrownBy(() -> MariaDbVectorSchema.validate(jdbc,properties("skygo.ai.embedding-dimensions=1536")))
        .isInstanceOf(AiException.class).hasMessageContaining("không khớp");
    assertThatThrownBy(() -> MariaDbVectorSchema.validate(jdbc,properties("skygo.ai.embedding-model=other")))
        .isInstanceOf(AiException.class);
    verifyNoInteractions(embedding);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_document",Integer.class)).isEqualTo(1);
  }

  @Test void upsertCannotOverwriteARowOutsideTheIndexOwnershipBoundary() {
    var source=doc("airport","PQC",null); var metadata=new HashMap<>(source.getMetadata()); metadata.put("managed_by","other");
    jdbc.update("INSERT INTO ai_document(id,content,metadata,embedding) VALUES(?,?,?,VEC_FromText(?))",
        source.getId(),"foreign content",json.writeValueAsString(metadata),json.writeValueAsString(vector()));
    assertThatThrownBy(() -> indexStore().upsert(List.of(source))).isInstanceOf(AiException.class);
    assertThat(jdbc.queryForObject("SELECT content FROM ai_document WHERE id=?",String.class,source.getId())).isEqualTo("foreign content");
    verifyNoInteractions(embedding);
  }

  @Test void transferDryRunApplyAndResumePreserveVectorsWithoutAnyEmbedding() throws Exception {
    var points=List.of(point(doc("airport","PQC",null)),point(doc("booking_summary","owned",73L)));
    try(var stub=new SourceStub(points,false,false)) {
      var importer=stub.importer();
      assertThat(importer.transfer(false).toInsert()).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_document",Integer.class)).isZero();
      assertThat(importer.transfer(true).toInsert()).isEqualTo(2);
      assertThat(importer.transfer(true).unchanged()).isEqualTo(2);
      assertThat(indexStore().hashes()).hasSize(2);
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_document WHERE JSON_VALUE(metadata,'$.owner_id')='73'",Integer.class)).isEqualTo(1);
      verifyNoInteractions(embedding);
    }
  }

  @Test void malformedLaterSourcePointCannotProducePartialImport() throws Exception {
    var bad=new HashMap<String,Object>(point(doc("airport","SGN",null))); bad.put("vector",List.of(1,2));
    try(var stub=new SourceStub(List.of(point(doc("airport","PQC",null)),bad),false,false)) {
      assertThatThrownBy(() -> stub.importer().transfer(true)).hasMessageContaining("vector");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_document",Integer.class)).isZero();
      verifyNoInteractions(embedding);
    }
  }

  @Test void conflictingTargetIsNotOverwrittenAndCountDriftIsRejected() throws Exception {
    var first=doc("airport","PQC",null);
    try(var stub=new SourceStub(List.of(point(first)),false,false)) {
      vectors.add(List.of(first)); jdbc.update("UPDATE ai_document SET content='newer local content' WHERE id=?",first.getId());
      assertThatThrownBy(() -> stub.importer().transfer(true)).hasMessageContaining("conflicting");
      assertThat(jdbc.queryForObject("SELECT content FROM ai_document WHERE id=?",String.class,first.getId())).isEqualTo("newer local content");
    }
    jdbc.update("DELETE FROM ai_document");
    try(var stub=new SourceStub(List.of(point(first)),true,false)) {
      assertThatThrownBy(() -> stub.importer().transfer(true)).hasMessageContaining("changed");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_document",Integer.class)).isZero();
    }
  }

  @Test void failedSqlVerificationRollsBackEntireTransfer() throws Exception {
    jdbc.execute("CREATE TRIGGER test_corrupt_import BEFORE INSERT ON ai_document FOR EACH ROW SET NEW.content='corrupted'");
    try(var stub=new SourceStub(List.of(point(doc("airport","PQC",null))),false,false)) {
      assertThatThrownBy(() -> stub.importer().transfer(true)).hasMessageContaining("verification failed");
      assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_document",Integer.class)).isZero();
    } finally { jdbc.execute("DROP TRIGGER test_corrupt_import"); }
  }

  @Test void fullApplicationStartsOnMariaDbWithoutQdrantOrGeminiRequests() {
    var environment=new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("isolated-vector-db",Map.ofEntries(
        Map.entry("spring.datasource.url",ds.getUrl()),Map.entry("spring.datasource.username",ds.getUsername()),
        Map.entry("spring.datasource.password",ds.getPassword()),Map.entry("GEMINI_API_KEY","fake-test-key"),
        Map.entry("skygo.ai.enabled","true"),Map.entry("skygo.ai.index.enabled","false"),
        Map.entry("skygo.seat-hold.cleanup-enabled","false"),Map.entry("server.port","0"),
        Map.entry("spring.main.banner-mode","off"))));
    var app=new SpringApplication(BackendApplication.class); app.setEnvironment(environment);
    try(var context=app.run()) {
      assertThat(context.getBean(org.springframework.ai.vectorstore.VectorStore.class)).isInstanceOf(MariaDBVectorStore.class);
      assertThat(context.containsBean("skygoQdrantClient")).isFalse();
      assertThat(context.getBean(JdbcTemplate.class).queryForObject("SELECT DATABASE()",String.class)).isEqualTo(database);
    }
  }

  @Test void invalidVisibilityAndRepeatedSourcePagesFailWithoutWrites() throws Exception {
    var leaked=new HashMap<String,Object>(point(doc("booking_summary","owned",73L)));
    @SuppressWarnings("unchecked") var payload=new HashMap<>((Map<String,Object>)leaked.get("payload"));
    payload.put("scope","public"); leaked.put("payload",payload);
    try(var stub=new SourceStub(List.of(leaked),false,false)) {
      assertThatThrownBy(() -> stub.importer().transfer(true)).isInstanceOf(IllegalArgumentException.class);
    }
    try(var stub=new SourceStub(List.of(point(doc("airport","PQC",null)),point(doc("airport","SGN",null))),false,true)) {
      assertThatThrownBy(() -> stub.importer().transfer(true)).hasMessageContaining("Repeated");
    }
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_document",Integer.class)).isZero();
  }

  @Test @EnabledIfEnvironmentVariable(named="SKYGO_VECTOR_SOURCE_TEST",matches="true")
  void existingQdrantVectorsCanBeCopiedIntoOnlyTheIsolatedDbWithoutEmbedding() {
    var transfer=new QdrantVectorTransfer(HttpClient.newHttpClient(),URI.create("http://127.0.0.1:6333"),
        "skygo_knowledge_v1_gemini-embedding-001_768",System.getenv().getOrDefault("SKYGO_AI_QDRANT_API_KEY",""),
        jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),properties(),50000);
    var check=transfer.transfer(false);
    assertThat(check.sourceDocuments()).isPositive();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_document",Integer.class)).isZero();
    var result=transfer.transfer(true);
    assertThat(result.sourceDocuments()).isEqualTo(check.sourceDocuments());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_document",Integer.class)).isEqualTo(result.sourceDocuments());
    String sample=jdbc.queryForObject("SELECT VEC_ToText(embedding) FROM ai_document ORDER BY id LIMIT 1",String.class);
    Double nearest=jdbc.queryForObject("SELECT VEC_DISTANCE_COSINE(embedding,VEC_FromText(?)) AS d FROM ai_document ORDER BY d LIMIT 1",Double.class,sample);
    assertThat(nearest).isCloseTo(0d,within(.000001));
    verifyNoInteractions(embedding);
    System.out.println("ISOLATED_SOURCE_TRANSFER_OK: documents="+result.sourceDocuments()+"; main database untouched; Gemini calls=0");
  }

  Map<String,Object> point(Document document) {
    var payload=new HashMap<>(document.getMetadata()); payload.put("doc_content",document.getText());
    if(payload.get("owner_id") instanceof Long owner) payload.put("owner_id",owner.toString());
    return Map.of("id",document.getId(),"payload",payload,"vector",vector());
  }
  final class SourceStub implements AutoCloseable {
    final HttpServer server; final AtomicInteger counts=new AtomicInteger();
    SourceStub(List<Map<String,Object>> points,boolean drift,boolean repeated) throws Exception {
      server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
      server.createContext("/collections/source",exchange -> {
        Object result;
        String path=exchange.getRequestURI().getPath();
        if(path.endsWith("/count")) result=Map.of("count",points.size()+(drift&&counts.incrementAndGet()>1?1:0));
        else if(path.endsWith("/scroll")) {
          var request=json.readTree(exchange.getRequestBody().readAllBytes());
          assertThat(request.path("with_vector").asBoolean()).isTrue();
          assertThat(request.path("filter").toString()).contains("managed_by","embedding_model");
          int n=request.has("offset")?1:0;
          var page=new LinkedHashMap<String,Object>();
          page.put("points",List.of(points.get(n)));
          page.put("next_page_offset",repeated || n==0 && points.size()>1?points.get(0).get("id"):null);
          result=page;
        } else result=Map.of("config",Map.of("params",Map.of("vectors",Map.of("size",768,"distance","Cosine"))));
        byte[] bytes=json.writeValueAsBytes(Map.of("result",result));
        exchange.getResponseHeaders().set("Content-Type","application/json"); exchange.sendResponseHeaders(200,bytes.length);
        try(var out=exchange.getResponseBody()) { out.write(bytes); } exchange.close();
      }); server.start();
    }
    QdrantVectorTransfer importer() {
      return new QdrantVectorTransfer(HttpClient.newHttpClient(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()),
          "source","",jdbc,new TransactionTemplate(new DataSourceTransactionManager(ds)),properties(),100);
    }
    public void close() { server.stop(0); }
  }
}
