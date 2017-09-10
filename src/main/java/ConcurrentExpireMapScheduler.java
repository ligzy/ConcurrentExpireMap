import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scheduler based on sorted map with time-to-run as the key.
 */
public class ConcurrentExpireMapScheduler
    implements ExpireMapScheduler, Runnable {
  static Logger LOG = LoggerFactory.getLogger(
      ConcurrentExpireMapScheduler.class);

  private SortedMap<Long, Set<Runnable>> sortedMap;

  public ConcurrentExpireMapScheduler(
      SortedMap<Long, Set<Runnable>> sortedMap) {
    this.sortedMap = sortedMap;
  }

  @Override
  public void run() {
    for (;;) {
      try {
        Set<Runnable> commands = null;
        synchronized (sortedMap) {
          while (commands == null) {
            if (sortedMap.isEmpty()) {
              LOG.info("Empty map");
              sortedMap.wait();
            } else {
              Long firstKey = sortedMap.firstKey();
              if (System.nanoTime() >= firstKey) {
                LOG.info("Found expired commands for " + firstKey);
                commands = sortedMap.get(firstKey);
                sortedMap.remove(firstKey);
              } else {
                long ns = firstKey - System.nanoTime();
                LOG.info("Waiting " + ns);
                final long nsInMs = TimeUnit.MILLISECONDS.toNanos(1);
                sortedMap.wait(ns / nsInMs, (int) (ns % nsInMs));
              }
            }
          }
        }
        // Run commands without sortedMap locked.
        if (commands != null) {
          for (Runnable command : commands) {
            LOG.info("Running command " + command);
            command.run();
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
  
  @Override
  public void schedule(Runnable command, long timeToRun) {
    synchronized (sortedMap) {
      Set<Runnable> set = sortedMap.get(timeToRun);
      if (set == null) {
        LOG.info("Creating a new set of " + command + " for " + timeToRun);
        sortedMap.put(timeToRun, new HashSet<>(Arrays.asList(command)));
        sortedMap.notifyAll();
      } else {
        LOG.info("Adding " + command + " to set for " + timeToRun);
        set.add(command);
      }
    }
  }

  @Override
  public void cancel(Runnable command, long timeToRun) {
    synchronized (sortedMap) {
      Set<Runnable> set = sortedMap.get(timeToRun);
      if (set != null) {
        LOG.info("Cancelling command " + command);
        set.remove(command);
        if (set.isEmpty()) {
          LOG.info("Removing set for " + timeToRun);
          sortedMap.remove(timeToRun);
          sortedMap.notifyAll();
        }
      }
    }
  }

  @Override
  public void cancelAll() {
    synchronized (sortedMap) {
      if (!sortedMap.isEmpty()) {
        LOG.info("Cancelling all commands");
        sortedMap.clear();
        sortedMap.notifyAll();
      }
    }
  }
}
