package de.joergdev.mosy.backend.standalone.persistence;

import javax.persistence.EntityManager;

public class DbUtils
{
  public static boolean validateEntityManager(EntityManager em)
  {
    if (em != null && em.isOpen())
    {
      try
      {
        Number number = (Number) em.createNativeQuery("SELECT 1 FROM INFORMATION_SCHEMA.COLLATIONS Limit 1")
            .getSingleResult();
        if (number != null & number.intValue() == 1)
        {
          // required for deleting cached objects not causing problems in later transactions
          em.clear();

          return true;
        }
      }
      catch (Exception ex)
      {
        // do nothing
      }
    }

    return false;
  }
}