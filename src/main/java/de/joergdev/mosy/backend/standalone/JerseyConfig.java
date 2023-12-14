package de.joergdev.mosy.backend.standalone;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;
import de.joergdev.mosy.backend.api.impl.Globalconfig;
import de.joergdev.mosy.backend.api.impl.Interfaces;
import de.joergdev.mosy.backend.api.impl.MockData;
import de.joergdev.mosy.backend.api.impl.MockProfiles;
import de.joergdev.mosy.backend.api.impl.MockServices;
import de.joergdev.mosy.backend.api.impl.RecordConfig;
import de.joergdev.mosy.backend.api.impl.RecordSessions;
import de.joergdev.mosy.backend.api.impl.Records;

@Component
public class JerseyConfig extends ResourceConfig
{
  //  private static final String API_IMPL_PACKAGE = "de.joergdev.mosy.backend.api.impl";

  public JerseyConfig()
  {
    register(Globalconfig.class);
    register(Interfaces.class);
    register(MockData.class);
    register(MockProfiles.class);
    register(MockServices.class);
    register(RecordConfig.class);
    register(Records.class);
    register(RecordSessions.class);
    register(de.joergdev.mosy.backend.api.impl.System.class);

    // the jersey scan via packages(..) is actually broken in connection with spring boot jar
    // so we have to register the API classes directly (see above)
    //    packages(API_IMPL_PACKAGE);
  }
}