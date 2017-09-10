/**
 * Interface for a scheduler.
 */
public interface ExpireMapScheduler {
  /**
   * Schedule a command to run.
   *
   * @param command The command to run
   * @param timeToRun The time in nanoseconds to run the command
   */
  void schedule(Runnable command, long timeToRun);

  /**
   * Cancel a command to run.
   *
   * @param command The command to run
   * @param timeToRun The time in nanoseconds to run the command
   */
  void cancel(Runnable command, long timeToRun);

  /**
   * Cancel all commands.
   */
  void cancelAll();
}
