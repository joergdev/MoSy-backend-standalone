package de.joergdev.mosy.backend.standalone.pool;

/**
 * Interface fuer Methoden die der Pool nutzt.
 * Mit dessen Hilfe kann der Pool dynamisch zur Laufzeit konfiguriert werden.
 * 
 * @author Andreas Joerg
 *
 * @param <T>
 */
public interface IPoolMethods<T>
{
  /**
   * Diese Methode wird aufgerufen, wenn ein Object aus dem Pool geloescht werden soll, weil es zu
   * lange inaktiv war.
   * Falls noch Resourcen freigegeben werden muessen kann dies hier erledigt werden.
   * Zb Schliessen einer DB Connection.
   * 
   * @param obj
   */
  void cleanup(T obj);

  /**
   * Gibt das Zeitinterval zurueck, in dem die Objekte im Pool auf Gueltigkeit geprueft werden.
   * 
   * @return long
   */
  long getCheckInterval();

  /**
   * Gibt ein neues Object zurueck um es dem Pool hinzuzufuegen.
   * 
   * @return T
   */
  T getNewObj();

  /**
   * Gibt die Zeit zurueck, wie lange ein Object im Pool gueltig ist, wenn es inaktiv ist.
   * 
   * @return long
   */
  long getTTL();

  /**
   * Gibt die maximale Groesse des Pools zurueck.
   * 
   * @return int
   */
  int getMaxSize();

  /**
   * Validiert das uebergene Objekt aus dem Pool.
   * Falls es nicht mehr gueltig ist (->false) wird es aus dem Pool entfernt.
   * 
   * @return boolean
   * @see #validateOnGet()
   */
  boolean validate(T obj);

  /** 
   * @return boolean -> wenn true wird {@link #validate(Object)} 
   *                    beim Holen eines Objekts aus dem Pool aufgerufen
   */
  boolean validateOnGet();

  /** 
   * @return boolean -> wenn true wird {@link #validate(Object)} 
   *                    bei Rueckgabe eines Objekts in den Pool aufgerufen
   */
  boolean validateOnGiveBack();
}