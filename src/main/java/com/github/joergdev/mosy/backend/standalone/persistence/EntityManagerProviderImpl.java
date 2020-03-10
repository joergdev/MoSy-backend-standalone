package com.github.joergdev.mosy.backend.standalone.persistence;

import javax.persistence.EntityManager;
import org.apache.log4j.Logger;
import com.github.joergdev.mosy.backend.persistence.EntityManagerProvider;
import com.github.joergdev.mosy.backend.standalone.pool.ObjectPool;

public class EntityManagerProviderImpl implements EntityManagerProvider
{
  private static final Logger LOG = Logger.getLogger(EntityManagerProviderImpl.class);

  private ObjectPool<EntityManager> emPool = null;

  @Override
  public EntityManager getEntityManager()
  {
    try
    {
      initEntityManagerPool();

      return emPool.get();
    }
    catch (RuntimeException ex)
    {
      LOG.error(ex.getMessage(), ex);

      throw ex;
    }
    catch (Exception ex)
    {
      LOG.error(ex.getMessage(), ex);

      throw new IllegalStateException(ex);
    }
  }

  private synchronized void initEntityManagerPool()
  {
    if (emPool != null)
    {
      return;
    }

    try
    {
      emPool = new ObjectPool<>(true, new EntityManagerPoolMethodsImpl(), 1);
    }
    catch (Exception ex)
    {
      throw new IllegalStateException(ex);
    }
  }

  @Override
  public void releaseEntityManager(EntityManager em)
  {
    emPool.giveBack(em);
  }

  @Override
  public boolean isContainerManaged()
  {
    return false;
  }
}