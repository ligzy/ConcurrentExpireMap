import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple ExpireMap implementation.
 */
public class SimpleExpireMap<K, V> implements ExpireMap<K, V> {
  static Logger LOG = LoggerFactory.getLogger(SimpleExpireMap.class);
  
  public class Entry<V> {
    private V value;
    /** Expiration time in nanoseconds. */
    private long expiration;

    public Entry(V value, long timeoutMs) {
      this.value = value;
      this.expiration = System.nanoTime()
          + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
    }

    public V getValue() {
      return value;
    }

    public boolean isExpired() {
      return System.nanoTime() >= expiration;
    }
  }
  
  private ConcurrentMap<K, Entry<V>> map;
  private ScheduledExecutorService scheduler;

  SimpleExpireMap(ConcurrentMap<K, Entry<V>> map,
                  ScheduledExecutorService scheduler) {
    this.map = map;
    this.scheduler = scheduler;
  }

  @Override
  public void put(K key, V value, long timeoutMs) {
    map.put(key, new Entry<>(value, timeoutMs));
    scheduler.schedule(() -> {
      Entry<V> entry = map.get(key);
      if (entry != null) {
        if (entry.isExpired()) {
          LOG.info(key + " expired");
          map.remove(key);
        } else {
          LOG.info(key + " re-added and not expired");
        }
      }
    }, timeoutMs, TimeUnit.MILLISECONDS);
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
    map.remove(key);
  }
}
