package com.github.joergdev.mosy.backend.standalone.pool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import com.github.joergdev.mosy.shared.Utils;

/**
 * Generischer Object Pool.
 * 
 * Durch Uebergabe von true im Konstruktor ist der Pool threadsafe.
 * 
 * @author Andreas Joerg
 *
 * @param <T> Typ der Objekte im Pool
 */
public class ObjectPool<T>
{
  public static final long TTL_UNLIMITED = -1;

  private List<PoolObject<T>> poolObjects;
  private IPoolMethods<T> poolMethodsImpl;
  private Lock lock;

  /**
   * Konstruktur mit Uebergabe {@link IPoolMethods} Implementierung
   * 
   * NICHT threadsafe
   * 
   * @param impl
   */
  public ObjectPool(IPoolMethods<T> impl)
  {
    this(false, impl);
  }

  /**
  * Konstruktur mit Uebergabe {@link IPoolMethods} Implementierung
  * 
  * NICHT threadsafe
  * 
  * @param impl
  * @param initialSize  -> Initiale Groesse des pools, d.h. wie viele Objekte zu Beginn
  *                        initialisiert werden sollen
  */
  public ObjectPool(IPoolMethods<T> impl, int initialSize)
  {
    this(false, impl, initialSize);
  }

  /**
   * extended constructor.
   * 
   * Wenn concurrent true ist, ist der Pool threadsafe.
   * 
   * @param concurrent
   * @param impl
   */
  public ObjectPool(boolean concurrent, IPoolMethods<T> impl)
  {
    this(concurrent, impl, 0);
  }

  /**
   * extended constructor.
   * 
   * Wenn concurrent true ist, ist der Pool threadsafe.
   * 
   * @param concurrent
   * @param impl
   * @param initialSize  -> Initiale Groesse des pools, d.h. wie viele Objekte zu Beginn
   *                        initialisiert werden sollen
   */
  public ObjectPool(boolean concurrent, IPoolMethods<T> impl, int initialSize)
  {
    this(concurrent, impl, concurrent
        ? new ReentrantLock()
        : new NullLock(), initialSize);
  }

  /**
   * extended constructor.
   * 
   * Wenn concurrent true ist, ist die interne Liste des Pools threadsafe.
   * Es muss noch ein Lock Object uebergeben werden.
   * Falls der Pool concurrent verwendet wird, empfielt sich allerdings der Aufruf des Konstruktors
   * ObjectPool(boolean concurrent, IPoolMethods<T> impl). Hier wird automatisch ein
   * ReentrantLock als Lock gesetzt.
   * 
   * @param concurrent
   * @param impl
   * @param lock
   */
  public ObjectPool(boolean concurrent, IPoolMethods<T> impl, Lock lock)
  {
    this(concurrent, impl, lock, 0);
  }

  /**
   * extended constructor.
   * 
   * Wenn concurrent true ist, ist die interne Liste des Pools threadsafe.
   * Es muss noch ein Lock Object uebergeben werden.
   * Falls der Pool concurrent verwendet wird, empfielt sich allerdings der Aufruf des Konstruktors
   * ObjectPool(boolean concurrent, IPoolMethods<T> impl). Hier wird automatisch ein
   * ReentrantLock als Lock gesetzt.
   * 
   * @param concurrent
   * @param impl
   * @param lock
   * @param initialSize -> Initiale Groesse des pools, d.h. wie viele Objekte zu Beginn
   *                       initialisiert werden sollen
   */
  public ObjectPool(boolean concurrent, IPoolMethods<T> impl, Lock lock, int initialSize)
  {
    //Pruefen der Implementierung und ggf. setzen
    checkPoolMethodsImpl(impl);
    this.poolMethodsImpl = impl;

    //Je nachdem ob pool threadsafe sein soll, entsprechende Liste setzen
    poolObjects = concurrent
        ? new CopyOnWriteArrayList<>()
        : new ArrayList<>();

    setLock(lock);

    // Initiales Erstellen Objekte im Pool, Anzahl Objekte (=initialSize) 
    initialInit(initialSize);

    if (TTL_UNLIMITED != impl.getTTL())
    {
      //Starten des Threads um Pool zu cleanen
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
   * Setzt das Lock
   * 
   * @param lock
   * @throws NullPointerException -> Wenn lock null
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
   * Prueft die Implementierung der IPoolMethods auf Gueltigkeit.
   * 
   * @param impl
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
    //Validierungen
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

    // Alles OK -> Objekte initialiseren und in Pool stellen
    for (int x = 0; x < initialSize; x++)
    {
      PoolObject<T> obj = new PoolObject<>(poolMethodsImpl.getNewObj(), false);
      poolObjects.add(obj);
    }
  }

  /**
   * interne Methode um ein Object aus dem Pool zu erhalten.
   * 
   * @return T
   */
  public T get()
  {
    lock.lock();

    try
    {
      PoolObject<T> obj = getFreePoolObject();

      //kein freies object gefunden
      if (obj == null)
      {
        //Noch platz im Pool -> neues Object erzeugen und dem Pool hinzufuegen
        if (poolObjects.size() < poolMethodsImpl.getMaxSize())
        {
          obj = new PoolObject<>(poolMethodsImpl.getNewObj(), true);
          poolObjects.add(obj);
        }
        //kein Platz mehr im Pool -> Rekursion
        else
        {
          Utils.delay(20);

          return get();
        }
      }

      // Validierung
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
    //Durchlauf Pool
    for (PoolObject<T> poolObj : poolObjects)
    {
      //Freies object gefunden
      if (poolObj.isLocked() == false)
      {
        poolObj.setLocked(true);
        return poolObj;
      }
    }

    return null;
  }

  /**
   * Gibt das uebergebene object im Pool wieder frei.
   * 
   * @param obj
   */
  public void giveBack(T obj)
  {
    //Durchlaufe Pool
    for (PoolObject<T> poolObj : poolObjects)
    {
      if (poolObj.getObj().equals(obj))
      {
        // Validierung
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
   * Gibt die aktuelle Anzahl der Objekte im Pool zurueck.
   * 
   * @return int
   */
  public int getPoolSize()
  {
    return poolObjects.size();
  }

  /**
   * Gibt die Anzahl der Objekte im Pool zurueck, die freigegeben sind.
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
   * Entfernt alle inaktiven (unlocked) Objekte aus dem Pool
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

        // kann nur flushen, wenn nicht gelocked
        if (!poolObj.isLocked())
        {
          poolObj.setLocked(true);

          // bevor cleanup lock freigeben, da cleanup uU laenger dauern kann und der lock
          // nur fuer das Locken des poolObjects benoetigt wird
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

  //Runnable fuer Thread um Pool zu cleanen
  private Runnable checkPool = new Runnable()
  {
    public void run()
    {
      while (true)
      {
        doCheck();

        //Schlafen vor naechstem Durchlauf
        Utils.delay(poolMethodsImpl.getCheckInterval());
      }
    }

    private void doCheck()
    {
      //Durchlaufe Pool
      Iterator<PoolObject<T>> it = poolObjects.iterator();
      while (it.hasNext())
      {
        PoolObject<T> poolObj = it.next();
        boolean timeouted = System.currentTimeMillis() - poolObj.getTimeGaveBack() > poolMethodsImpl.getTTL();

        // Wenn timeout noch nicht abgelaufen -> Weiter mit naechstem Object im Pool
        if (!timeouted)
        {
          continue;
        }

        boolean unlocked = false;

        try
        {
          lock.lock();

          //Wenn freigegeben und zu lange inaktiv -> cleanup und entfernen
          if (!poolObj.isLocked())
          {
            poolObj.setLocked(true);

            // bevor cleanup lock freigeben, da cleanup uU laenger dauern kann und der lock
            // nur fuer das Locken des poolObjects benoetigt wird
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
    public void lock()
    {}

    public void lockInterruptibly()
      throws InterruptedException
    {}

    public Condition newCondition()
    {
      return null;
    }

    public boolean tryLock()
    {
      return false;
    }

    public boolean tryLock(long time, TimeUnit unit)
      throws InterruptedException
    {
      return false;
    }

    public void unlock()
    {}
  }
}