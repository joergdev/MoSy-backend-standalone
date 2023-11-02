package de.joergdev.mosy.backend.standalone;

import javax.ws.rs.core.Response;
import org.apache.log4j.Logger;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import de.joergdev.mosy.api.response.EmptyResponse;
import de.joergdev.mosy.api.response.ResponseMessageLevel;
import de.joergdev.mosy.backend.api.APIUtils;
import de.joergdev.mosy.backend.bl.system.BootIntern;
import de.joergdev.mosy.backend.persistence.EntityManagerProviderService;
import de.joergdev.mosy.backend.standalone.persistence.EntityManagerProviderImpl;

@SpringBootApplication
public class ApplicationMain extends SpringBootServletInitializer
{
  private static final Logger LOG = Logger.getLogger(ApplicationMain.class);

  public static void main(String[] args)
  {
    try
    {
      long timeStart = System.currentTimeMillis();
      LOG.info("Booting Application " + ApplicationMain.class);

      new ApplicationMain().configure(new SpringApplicationBuilder(ApplicationMain.class)).run(args);

      // set EntityManagerProvider
      EntityManagerProviderService.getInstance().setEntityManagerProvider(new EntityManagerProviderImpl());

      doSystemBoot();

      LOG.info("Booted application " + ApplicationMain.class + " in "
               + (System.currentTimeMillis() - timeStart) + " ms");
    }
    catch (Exception ex)
    {
      LOG.error(ex.getMessage(), ex);

      System.exit(-1);
    }
  }

  private static void doSystemBoot()
  {
    Response response = APIUtils.executeBL(null, new EmptyResponse(), new BootIntern());

    EmptyResponse emptyResponse = (EmptyResponse) response.getEntity();

    if (!emptyResponse.isStateOK())
    {
      emptyResponse.getMessagesForLevel(ResponseMessageLevel.FATAL, ResponseMessageLevel.ERROR)
          .forEach(m -> LOG.error(m.getFullMessage()));

      throw new IllegalStateException("system boot failed");
    }
  }
}