import com.skygo.backend.ai.config.*;
import com.skygo.backend.ai.migration.QdrantVectorTransfer;
import com.skygo.backend.ai.service.AiException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Standalone importer: never boots Spring, Flyway, Gemini or automatic indexing. */
public class VectorTransferMain {
  public static void main(String[] args) {
    try {
      if(args.length!=4 || !java.util.List.of("dry-run","apply").contains(args[0]))
        throw new IllegalArgumentException("Invalid transfer arguments.");
      var env=new StandardEnvironment();
      var config=new Properties();
      try(var reader=Files.newBufferedReader(Path.of("src/main/resources/application.properties"),StandardCharsets.UTF_8)) { config.load(reader); }
      env.getPropertySources().addLast(new PropertiesPropertySource("application",config));
      new AiEnvironmentPostProcessor().postProcessEnvironment(env,new SpringApplication());
      var ai=Binder.get(env).bindOrCreate("skygo.ai",AiProperties.class);
      String url=env.getRequiredProperty("spring.datasource.url").replaceAll("(?i)createDatabaseIfNotExist=true","createDatabaseIfNotExist=false");
      var ds=new DriverManagerDataSource(url,env.getProperty("spring.datasource.username","root"),env.getProperty("spring.datasource.password",""));
      var options=new Properties();
      options.setProperty("createDatabaseIfNotExist","false"); options.setProperty("connectTimeout","5000"); options.setProperty("socketTimeout","60000");
      ds.setConnectionProperties(options);
      var tx=new TransactionTemplate(new DataSourceTransactionManager(ds)); tx.setTimeout(120);
      var importer=new QdrantVectorTransfer(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
          URI.create(args[1]),args[2],env.getProperty("SKYGO_AI_QDRANT_API_KEY",""),new JdbcTemplate(ds),tx,ai,Integer.parseInt(args[3]));
      var report=importer.transfer(args[0].equals("apply"));
      System.out.println("VECTOR_TRANSFER_OK: source="+report.sourceDocuments()+", toInsert="+report.toInsert()+", unchanged="+report.unchanged()+", applied="+report.applied());
      System.out.println("No Gemini request, source mutation, schema migration or existing-row overwrite was performed.");
    } catch(Exception ex) {
      System.err.println("VECTOR_TRANSFER_FAILED: "+ex.getClass().getSimpleName());
      if(ex instanceof AiException || ex.getClass()==IllegalStateException.class) System.err.println(ex.getMessage());
      System.err.println("Transfer incomplete. Check DB configuration, V12 and stopped index writers. No success is assumed.");
      System.exit(1);
    }
  }
}
