import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concurrent ExpireMap implementation.
 */
public class ConcurrentExpireMap<K, V> implements ExpireMapExtra<K, V> {
  static Logger LOG = LoggerFactory.getLogger(ConcurrentExpireMap.class);

  public class Entry<V> {
    private V value;
    /** The expiration time in nanoseconds. */
    private long expiration;
    /** The command to run when the entry is expired. */
    private Runnable command;

    public Entry(V value, long timeoutMs, Runnable command) {
      this.value = value;
      this.expiration = System.nanoTime()
          + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
      this.command = command;
    }

    public V getValue() {
      return value;
    }

    public long getExpiration() {
      return expiration;
    }

    public boolean isExpired() {
      return System.nanoTime() >= expiration;
    }

    public Runnable getCommand() {
      return command;
    }
  }
  
  private ConcurrentMap<K, Entry<V>> map;
  private ExpireMapScheduler[] cleaners;

  /**
   * Construct {@link ConcurrentExpireMap} object.
   * 
   * @param map A concurrent map
   * @param cleaners An array of concurrent {@link ExpireMapScheduler} objects
   */
  ConcurrentExpireMap(ConcurrentMap<K, Entry<V>> map,
                      ExpireMapScheduler[] cleaners) {
    this.map = map;
    this.cleaners = cleaners;
  }

  @Override
  public void put(K key, V value, long timeoutMs) {
    Entry<V> entryToPut = new Entry<>(value, timeoutMs, () -> {
      Entry<V> entryToRemove = map.get(key);
      if (entryToRemove != null) {
        assert entryToRemove.isExpired();
        LOG.info(key + " expired");
        map.remove(key);
      }
    });
    map.put(key, entryToPut);
    getCleaner(entryToPut.getExpiration())
        .schedule(entryToPut.getCommand(), entryToPut.getExpiration());
  }

  @Override
  public V get(K key) {
    Entry<V> entry = map.get(key);
    if (entry == null || entry.isExpired()) {
      return null;
    }
    return entry.getValue();
  }

  @Override
  public void remove(K key) {
    Entry<V> entry = map.get(key);
    if (entry != null) {
      map.remove(key);
      getCleaner(entry.getExpiration())
          .cancel(entry.getCommand(), entry.getExpiration());
    }
  }

  @Override
  public void clear() {
    map.clear();
    for (ExpireMapScheduler cleaner : cleaners) {
      cleaner.cancelAll();
    }
  }

  @Override
  public int size() {
    return map.size();
  }

  ExpireMapScheduler getCleaner(long expiration) {
    return cleaners[Math.floorMod((int) expiration, cleaners.length)];
  }
}
