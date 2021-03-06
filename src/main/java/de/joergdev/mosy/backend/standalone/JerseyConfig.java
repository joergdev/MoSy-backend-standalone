package de.joergdev.mosy.backend.standalone;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

@Component
public class JerseyConfig extends ResourceConfig
{
  public static final String API_IMPL_PACKAGE = "de.joergdev.mosy.backend.api.impl";

  public JerseyConfig()
  {
    packages(API_IMPL_PACKAGE);
  }
}