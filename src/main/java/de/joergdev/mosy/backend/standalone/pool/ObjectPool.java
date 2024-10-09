package de.joergdev.mosy.backend.standalone.pool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import de.joergdev.mosy.shared.Utils;

/**
 * Generic Object Pool.
 * 
 * By passing concurrent=true in the constructor, the pool is thread-safe.
 * 
 * @author Andreas Joerg
 *
 * @param <T> Type of pool objects
 */
public class ObjectPool<T>
{
  public static final long TTL_UNLIMITED = -1;

  private List<PoolObject<T>> poolObjects;
  private IPoolMethods<T> poolMethodsImpl;
  private Lock lock;

  /**
   * Constructor with {@link IPoolMethods} implementation param.
   * 
   * NOT threadsafe
   * 
   * @param impl - IPoolMethods
   */
  public ObjectPool(IPoolMethods<T> impl)
  {
    this(false, impl);
  }

  /**
  * Constructor with {@link IPoolMethods} implementation param
  * 
  * NOT threadsafe
  * 
  * @param impl - IPoolMethods
  * @param initialSize - Initial size of the pools. On startup, this number of objects will be created.
  */
  public ObjectPool(IPoolMethods<T> impl, int initialSize)
  {
    this(false, impl, initialSize);
  }

  /**
   * extended constructor.
   * 
   * By passing concurrent=true in the constructor, the pool is thread-safe.
   * 
   * @param concurrent - if true pool can handle concurrency
   * @param impl - IPoolMethods
   */
  public ObjectPool(boolean concurrent, IPoolMethods<T> impl)
  {
    this(concurrent, impl, 0);
  }

  /**
   * extended constructor.
   * 
   * By passing concurrent=true in the constructor, the pool is thread-safe.
   * 
   * @param concurrent - if true pool can handle concurrency
   * @param impl - IPoolMethods
   * @param initialSize - Initial size of the pools. On startup, this number of objects will be created
   */
  public ObjectPool(boolean concurrent, IPoolMethods<T> impl, int initialSize)
  {
    this(concurrent, impl, concurrent ? new ReentrantLock() : new NullLock(), initialSize);
  }

  /**
   * extended constructor.
   * 
   * By passing concurrent=true in the constructor, the pool is thread-safe
   * A lock object must also be passed.
   * However, if the pool uses concurrent, it is recommended to use the constructor ObjectPool(boolean concurrent, IPoolMethods&lt;T&gt; impl).
   * Here, a ReentrantLock is automatically set as the lock.
   * 
   * @param concurrent - if true pool can handle concurrency
   * @param impl - IPoolMethods
   * @param lock - Lock to set
   */
  public ObjectPool(boolean concurrent, IPoolMethods<T> impl, Lock lock)
  {
    this(concurrent, impl, lock, 0);
  }

  /**
   * extended constructor.
   * 
   * By passing concurrent=true in the constructor, the pool is thread-safe
   * A lock object must also be passed.
   * However, if the pool uses concurrent, it is recommended to use the constructor ObjectPool(boolean concurrent, IPoolMethods&lt;T&gt; impl).
   * Here, a ReentrantLock is automatically set as the lock.
   * 
   * @param concurrent - if true pool can handle concurrency 
   * @param impl - IPoolMethods
   * @param lock - Lock to set
   * @param initialSize - Initial size of the pools. On startup, this number of objects will be created
   */
  public ObjectPool(boolean concurrent, IPoolMethods<T> impl, Lock lock, int initialSize)
  {
    // check and set impl
    checkPoolMethodsImpl(impl);
    this.poolMethodsImpl = impl;

    // set list thread-safe or not
    poolObjects = concurrent ? new CopyOnWriteArrayList<>() : new ArrayList<>();

    setLock(lock);

    // inital creation of objects in pool (number if objects=initialSize) 
    initialInit(initialSize);

    if (TTL_UNLIMITED != impl.getTTL())
    {
      // start pool-cleaning thread
      Thread threadClean = new Thread(checkPool);
      threadClean.setDaemon(true);

      threadClean.start();
    }
  }

  /**
   * @return lock
   */
  public Lock getLock()
  {
    return lock;
  }

  /**
   * Set an lock.
   * 
   * @param lock - Lock to set
   * @throws NullPointerException - if lock is null (use NullLock)
   * @see NullLock
   */
  public void setLock(Lock lock)
  {
    if (lock == null)
    {
      throw new NullPointerException("lock may not be null");
    }

    this.lock = lock;
  }

  /**
   * Validating IPoolMethods implementation.
   * 
   * @param impl - IPoolMethods
   */
  private void checkPoolMethodsImpl(IPoolMethods<T> impl)
  {
    if (impl == null)
    {
      throw new NullPointerException("IPoolMethods Impl may not be null");
    }

    if (impl.getMaxSize() < 1)
    {
      throw new IllegalArgumentException("MaxPoolSize may not be smaller than 1");
    }

    if (TTL_UNLIMITED != impl.getTTL())
    {
      if (impl.getCheckInterval() < 10000)
      {
        throw new IllegalArgumentException("CheckInterval may not be smaller than 10 seconds");
      }

      if (impl.getTTL() < 10000)
      {
        throw new IllegalArgumentException("TTL may not be smaller than 10 seconds");
      }
    }
  }

  private void initialInit(int initialSize)
  {
    // Validations
    if (initialSize < 0)
    {
      throw new IllegalArgumentException("initialSize may not be negative");
    }

    if (initialSize == 0)
    {
      return;
    }

    if (initialSize > poolMethodsImpl.getMaxSize())
    {
      throw new IllegalArgumentException("initialSize may not be bigger than maxPoolSize");
    }

    // All fine -> initialise object and put into pool
    for (int x = 0; x < initialSize; x++)
    {
      PoolObject<T> obj = new PoolObject<>(poolMethodsImpl.getNewObj(), false);
      poolObjects.add(obj);
    }
  }

  /**
   * Internal method to obtain an object from the pool.
   * 
   * @return T
   */
  public T get()
  {
    lock.lock();

    try
    {
      PoolObject<T> obj = getFreePoolObject();

      // no free object found
      if (obj == null)
      {
        // Still space in the pool -> create a new object and add it to the pool
        if (poolObjects.size() < poolMethodsImpl.getMaxSize())
        {
          obj = new PoolObject<>(poolMethodsImpl.getNewObj(), true);
          poolObjects.add(obj);
        }
        // No space in pool -> recursive call after delay
        else
        {
          Utils.delay(20);

          return get();
        }
      }

      // Validation
      if (poolMethodsImpl.validateOnGet())
      {
        if (!poolMethodsImpl.validate(obj.getObj()))
        {
          poolObjects.remove(obj);
          poolMethodsImpl.cleanup(obj.getObj());

          return get();
        }
      }

      return obj.getObj();
    }
    finally
    {
      lock.unlock();
    }
  }

  private PoolObject<T> getFreePoolObject()
  {
    for (PoolObject<T> poolObj : poolObjects)
    {
      // free object found
      if (poolObj.isLocked() == false)
      {
        poolObj.setLocked(true);
        return poolObj;
      }
    }

    return null;
  }

  /**
   * Release the object in pool.
   * 
   * @param obj - object in pool
   */
  public void giveBack(T obj)
  {
    for (PoolObject<T> poolObj : poolObjects)
    {
      if (poolObj.getObj().equals(obj))
      {
        // Validations
        if (poolMethodsImpl.validateOnGiveBack())
        {
          if (!poolMethodsImpl.validate(obj))
          {
            poolObjects.remove(poolObj);
            poolMethodsImpl.cleanup(obj);

            return;
          }
        }

        poolObj.setLocked(false);
        poolObj.setTimeGaveBack(System.currentTimeMillis());

        return;
      }
    }
  }

  /**
   * Returns the current number of objects in the pool.
   * 
   * @return int
   */
  public int getPoolSize()
  {
    return poolObjects.size();
  }

  /**
   * Returns the number of objects in the pool that are available.
   * 
   * @return int
   */
  public int getUnlockedCount()
  {
    int size = 0;

    for (PoolObject<T> obj : poolObjects)
    {
      if (!obj.isLocked())
      {
        size++;
      }
    }

    return size;
  }

  /**
   * Remove all inactive (unlocked) objects from pool.
   */
  public void flushPool()
  {
    Iterator<PoolObject<T>> it = poolObjects.iterator();
    while (it.hasNext())
    {
      PoolObject<T> poolObj = it.next();
      boolean unlocked = false;

      try
      {
        lock.lock();

        // can only flush if not locked
        if (!poolObj.isLocked())
        {
          poolObj.setLocked(true);

          // before cleanup release lock, because cleanup may take some time and lock is only needed for locking poolObjects
          lock.unlock();
          unlocked = true;

          poolObjects.remove(poolObj);
          poolMethodsImpl.cleanup(poolObj.getObj());
        }
      }
      finally
      {
        if (!unlocked)
        {
          lock.unlock();
        }
      }
    }
  }

  /** Runnable for pool cleaning thread */
  private Runnable checkPool = new Runnable()
  {
    @Override
    public void run()
    {
      while (true)
      {
        doCheck();

        // delay
        Utils.delay(poolMethodsImpl.getCheckInterval());
      }
    }

    private void doCheck()
    {
      Iterator<PoolObject<T>> it = poolObjects.iterator();
      while (it.hasNext())
      {
        PoolObject<T> poolObj = it.next();
        boolean timeouted = System.currentTimeMillis() - poolObj.getTimeGaveBack() > poolMethodsImpl.getTTL();

        // if not timeouted -> go to next object in pool
        if (!timeouted)
        {
          continue;
        }

        boolean unlocked = false;

        try
        {
          lock.lock();

          // if released and inactive for too long -> cleanup and remove
          if (!poolObj.isLocked())
          {
            poolObj.setLocked(true);

            // before cleanup release lock, because cleanup may take some time and lock is only needed for locking poolObjects
            lock.unlock();
            unlocked = true;

            poolObjects.remove(poolObj);
            poolMethodsImpl.cleanup(poolObj.getObj());
          }
        }
        finally
        {
          if (!unlocked)
          {
            lock.unlock();
          }
        }

      }
    }
  };

  private static class NullLock implements Lock
  {
    @Override
    public void lock()
    {}

    @Override
    public void lockInterruptibly()
      throws InterruptedException
    {}

    @Override
    public Condition newCondition()
    {
      return null;
    }

    @Override
    public boolean tryLock()
    {
      return false;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit)
      throws InterruptedException
    {
      return false;
    }

    @Override
    public void unlock()
    {}
  }
}
