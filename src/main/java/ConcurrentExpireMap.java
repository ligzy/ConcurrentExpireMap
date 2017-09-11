import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concurrent ExpireMap implementation.
 */
public class ConcurrentExpireMap<K, V> implements ExpireMapExtra<K, V> {
  static Logger LOG = LoggerFactory.getLogger(ConcurrentExpireMap.class);

  static class Entry<K, V> implements Comparable<Entry<K, V>> {
    private K key;
    private V value;
    /** The expiration time in nanoseconds. */
    private long expiration;
    /** The command to run when the entry is expired. */
    private Runnable command;

    public Entry(K key, V value, long timeoutMs, Runnable command) {
      this.key = key;
      this.value = value;
      this.expiration = System.nanoTime()
          + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
      this.command = command;
    }

    public K getKey() {
      return key;
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

    @Override
    public int compareTo(Entry<K, V> o) {
      if (getExpiration() < o.getExpiration()) {
        return 1;
      } else if (getExpiration() == o.getExpiration()) {
        return 0;
      } else {
        return -1;
      }
    }
  }

  static class Cleaner<K, V> {
    static Logger LOG = LoggerFactory.getLogger(Cleaner.class);

    private ArrayList<SortedSet<Entry<K, V>>> sortedSets;

    public Cleaner(int numCleaners) {
      ExecutorService pool = Executors.newFixedThreadPool(numCleaners);
      sortedSets = new ArrayList<>(numCleaners);
      for (int i = 0; i < numCleaners; ++i) {
        SortedSet<Entry<K, V>> sortedSet = new TreeSet<>();
        sortedSets.add(sortedSet);
        pool.execute(() -> {
          for (;;) {
            try {
              List<Runnable> commands = new LinkedList<>();
              synchronized (sortedSet) {
                while (commands.isEmpty()) {
                  while (!sortedSet.isEmpty()) {
                    Entry<K, V> entry = sortedSet.first();
                    if (System.nanoTime() >= entry.getExpiration()) {
                      LOG.info("Add expired command for " + entry.getKey());
                      commands.add(entry.getCommand());
                      sortedSet.remove(entry);
                    } else {
                      break;
                    }
                  }
                  if (!commands.isEmpty()) {
                    continue;
                  }
                  if (sortedSet.isEmpty()) {
                    LOG.info("Waiting forever");
                    sortedSet.wait();
                  } else {
                    Entry<K, V> entry = sortedSet.first();
                    long ns = entry.getExpiration() - System.nanoTime();
                    LOG.info("Waiting " + ns);
                    final long nsInMs = TimeUnit.MILLISECONDS.toNanos(1);
                    sortedSet.wait(ns / nsInMs,
                        (int) (Math.floorMod(ns, nsInMs)));
                  }
                }
              }
              // Run commands without sortedSet locked.
              if (!commands.isEmpty()) {
                for (Runnable command : commands) {
                  command.run();
                }
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
        });
      }
    }

    SortedSet<Entry<K, V>> getSortedSet(Entry<K, V> entry) {
      return sortedSets.get(Math.floorMod(
          entry.getKey().hashCode(), sortedSets.size()));
    }

    public void schedule(Entry<K, V> entry) {
      SortedSet<Entry<K, V>> sortedSet = getSortedSet(entry);
      synchronized (sortedSet) {
        LOG.info("Scheduling expiration for key " + entry.getKey());
        sortedSet.add(entry);
        sortedSet.notifyAll();
      }
    }

    public void cancel(Entry<K, V> entry) {
      SortedSet<Entry<K, V>> sortedSet = getSortedSet(entry);
      synchronized (sortedSet) {
        LOG.info("Cancelling expiration for key " + entry.getKey());
        sortedSet.remove(entry);
        sortedSet.notifyAll();
      }
    }

    public void cancelAll() {
      for (SortedSet<Entry<K, V>> sortedSet : sortedSets) {
        synchronized (sortedSet) {
          if (!sortedSet.isEmpty()) {
            LOG.info("Cancelling all for set " + sortedSet);
            sortedSet.clear();
            sortedSet.notifyAll();
          }
        }
      }
    }
  }
  
  private ConcurrentMap<K, Entry<K, V>> map;
  private Cleaner<K, V> cleaner;

  /**
   * Construct {@link ConcurrentExpireMap} object.
   * 
   * @param map A concurrent map
   * @param numCleaners the number to cleaners
   */
  ConcurrentExpireMap(ConcurrentMap<K, Entry<K, V>> map, int numCleaners) {
    this.map = map;
    this.cleaner = new Cleaner<>(numCleaners);
  }

  @Override
  public void put(K key, V value, long timeoutMs) {
    Entry<K, V> entryToPut = new Entry<>(key, value, timeoutMs, () -> {
      Entry<K, V> entryToRemove = map.get(key);
      if (entryToRemove != null) {
        if (entryToRemove.isExpired()) {
          LOG.info(key + " expired");
          map.remove(key);
        }
      }
    });
    map.put(key, entryToPut);
    cleaner.schedule(entryToPut);
  }

  @Override
  public V get(K key) {
    Entry<K, V> entry = map.get(key);
    if (entry == null || entry.isExpired()) {
      return null;
    }
    return entry.getValue();
  }

  @Override
  public void remove(K key) {
    Entry<K, V> entry = map.get(key);
    if (entry != null) {
      map.remove(key);
      cleaner.cancel(entry);
    }
  }

  @Override
  public void clear() {
    map.clear();
    cleaner.cancelAll();
  }

  @Override
  public int size() {
    return map.size();
  }
}
