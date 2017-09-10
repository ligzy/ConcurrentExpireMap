/**
 * Interface for extra APIs.
 */
public interface ExpireMapExtra<K, V> extends ExpireMap<K, V> {
  /**
   * Clear all entries in the map.
   *
   * For testing only.
   */
  void clear();

  /**
   * Return the number of entries in the map including expired entries.
   *
   * For testing only.
   */
  int size();
}
