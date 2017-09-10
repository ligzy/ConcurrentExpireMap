/**
 * Interface for a map with expiring entries.
 */
public interface ExpireMap<K,V> {
  /**
   * Add an entry to the map.
   * 
   * If there is no entry with the key in the map,
   * add the key/value pair as a new entry.
   * If there is an existing entry with the key,
   * the current entry will be replaced with the new key/value pair.
   * If the newly added entry is not removed after timeoutMs
   * since it's added to the map, remove it.
   *
   * @param key The map key
   * @param value The value to store in the map
   * @param timeoutMs The timeout in milliseconds
   */
  void put(K key, V value, long timeoutMs);
  
  /**
   * Get the value associated with the key if present; otherwise, return null.
   *
   * @param key The map key
   */
  V get(K key);

  /**
   * Remove the entry associated with key, if any.
   *
   * @param key The map key
   */
  void remove(K key);
}
