package com.github.joergdev.mosy.backend.standalone.persistence;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import com.github.joergdev.mosy.backend.standalone.pool.IPoolMethods;
import com.github.joergdev.mosy.backend.standalone.pool.ObjectPool;

public class EntityManagerPoolMethodsImpl implements IPoolMethods<EntityManager>
{
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

  public long getCheckInterval()
  {
    return 0;
  }

  public EntityManager getNewObj()
  {
    EntityManagerFactory emf = Persistence.createEntityManagerFactory("db");

    return emf.createEntityManager();
  }

  public long getTTL()
  {
    return ObjectPool.TTL_UNLIMITED;
  }

  public int getMaxSize()
  {
    return 5;
  }

  public boolean validate(EntityManager em)
  {
    return DbUtils.validateEntityManager(em);
  }

  public boolean validateOnGet()
  {
    return false;
  }

  public boolean validateOnGiveBack()
  {
    return true;
  }
}