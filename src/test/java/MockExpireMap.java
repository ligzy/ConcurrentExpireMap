import java.util.concurrent.TimeUnit;

import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;

/**
 * Mock ExpireMap implementation.
 */
public class MockExpireMap<K, V> implements ExpireMap<K, V> {
  private ExpiringMap<K, V> impl;

  MockExpireMap() {
    impl = ExpiringMap.builder()
        .variableExpiration()
        .build();
  }

  @Override
  public void put(K key, V value, long timeoutMs) {
    impl.put(key, value, ExpirationPolicy.CREATED, timeoutMs,
        TimeUnit.MILLISECONDS);
  }

  @Override
  public V get(K key) {
    return impl.get(key);
  }

  @Override
  public void remove(K key) {
    impl.remove(key);
  }
}
