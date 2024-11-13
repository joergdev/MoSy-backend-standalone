package de.joergdev.mosy.backend.standalone.persistence;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import de.joergdev.mosy.backend.standalone.pool.IPoolMethods;
import de.joergdev.mosy.backend.standalone.pool.ObjectPool;
import de.joergdev.mosy.shared.Utils;

public class EntityManagerPoolMethodsImpl implements IPoolMethods<EntityManager>
{
  public static final String SYSTEM_PROPERTY_MAX_POOL_SIZE = "MOSY_DB_MAX_POOL_SIZE";

  private static final int DEFAULT_MAX_POOL_SIZE = 5;

  @Override
  public void cleanup(EntityManager em)
  {
    try
    {
      if (em != null)
      {
        EntityManagerFactory emf = em.getEntityManagerFactory();

        try
        {
          EntityTransaction tx = em.getTransaction();
          if (tx != null && tx.isActive())
          {
            tx.rollback();
          }
        }
        catch (Exception exDontCare)
        {
          // do nothing
        }

        try
        {
          if (em.isOpen())
          {
            em.close();
          }
        }
        catch (Exception exDontCare)
        {
          // do nothing
        }

        try
        {
          if (emf != null && emf.isOpen())
          {
            emf.close();
          }
        }
        catch (Exception exDontCare)
        {
          // do nothing
        }
      }
    }
    catch (Exception exDontCare)
    {
      // do nothing
    }
  }

  @Override
  public long getCheckInterval()
  {
    return 0;
  }

  @Override
  public EntityManager getNewObj()
  {
    EntityManagerFactory emf = Persistence.createEntityManagerFactory("db");

    return emf.createEntityManager();
  }

  @Override
  public long getTTL()
  {
    return ObjectPool.TTL_UNLIMITED;
  }

  @Override
  public int getMaxSize()
  {
    String sysProp = Utils.getSystemProperty(SYSTEM_PROPERTY_MAX_POOL_SIZE);

    return Utils.isEmpty(sysProp) ? DEFAULT_MAX_POOL_SIZE : Utils.asInteger(sysProp);
  }

  @Override
  public boolean validate(EntityManager em)
  {
    return DbUtils.validateEntityManager(em);
  }

  @Override
  public boolean validateOnGet()
  {
    return false;
  }

  @Override
  public boolean validateOnGiveBack()
  {
    return true;
  }
}
