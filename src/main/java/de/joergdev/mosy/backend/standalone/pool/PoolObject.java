package de.joergdev.mosy.backend.standalone.pool;

/**
 * Klasse fuer ein Object im Pool.
 * 
 * @author Andreas Joerg
 *
 * @param <T>
 */
class PoolObject<T>
{
	//eigentliches object
	private T obj;
	//Flag ob freigegeben
	private boolean locked;
	//timeStamp seit wann inaktiv
	private long timeGaveBack;
	
	/**
	 * constructor
	 * 
	 * @param obj
	 * @param locked
	 */
	public PoolObject(T obj, boolean locked)
	{
		setObj(obj);
		setLocked(locked);
	}
	
	public void setLocked(boolean locked) 
	{
		this.locked = locked;
	}
	public boolean isLocked() 
	{
		return locked;
	}
	
	public void setObj(T obj) 
	{
		this.obj = obj;
	}
	public T getObj() 
	{
		return obj;
	}

	public void setTimeGaveBack(long timeGaveBack)
	{
		this.timeGaveBack = timeGaveBack;
	}
	public long getTimeGaveBack()
	{
		return timeGaveBack;
	}
}
