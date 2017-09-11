import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;


/**
 * Test ExpireMap implementations.
 */
@RunWith(Parameterized.class)
public class TestExpireMap {
  static final String K1 = "key1";
  static final String V1 = "value1";
  static final String K2 = "key2";
  static final String V2 = "value2";
  static final String K3 = "key3";
  static final String V3 = "value3";

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
        { new ConcurrentExpireMap<String, String>(
            new ConcurrentHashMap<>(), 4)},
//        { new SimpleExpireMap<String, String>(
//            new ConcurrentHashMap<>(),
//            Executors.newScheduledThreadPool(1)) },
//        { new MockExpireMap<String, String>() },
    });
  }
  
  private ExpireMapExtra<String, String> emap;

  public TestExpireMap(ExpireMapExtra<String, String> emap) {
    this.emap = emap;
    emap.clear();
  }
  
  @Test
  public void testBasic() {
    Assert.assertThat(null, CoreMatchers.is(emap.get(K1)));
    emap.put(K1, V1, TimeUnit.SECONDS.toMillis(1));
    Assert.assertThat("K1 added", V1, CoreMatchers.is(emap.get(K1)));
    emap.remove(K1);
    Assert.assertThat("K1 removed", null, CoreMatchers.is(emap.get(K1)));
    Assert.assertThat("0 entry",  0, CoreMatchers.is(emap.size()));
  }

  @Test
  public void testExpireOne() throws InterruptedException {
    // t+0: add K1 expiring at t+2
    emap.put(K1, V1, TimeUnit.SECONDS.toMillis(2));

    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    // t+1
    Assert.assertThat("K1 still there", V1, CoreMatchers.is(emap.get(K1)));

    Thread.sleep(TimeUnit.SECONDS.toMillis(2));
    // t+3
    Assert.assertThat("K1 expired", null, CoreMatchers.is(emap.get(K1)));
    Assert.assertThat("0 entry", 0, CoreMatchers.is(emap.size()));
  }

  @Test
  public void testExpireMany() throws InterruptedException {
    // t+0: add K1 expiring at t+3
    emap.put(K1, V1, TimeUnit.MILLISECONDS.toMillis(3000));

    Thread.sleep(TimeUnit.MILLISECONDS.toMillis(500));
    // t+0.5: add K2 expiring at t+1, add K3 expiring at t+2
    emap.put(K2, V2, TimeUnit.MILLISECONDS.toMillis(500));
    emap.put(K3, V3, TimeUnit.MILLISECONDS.toMillis(1500));

    Thread.sleep(TimeUnit.MILLISECONDS.toMillis(1000));
    // t+1.5
    Assert.assertThat("K1 still there", V1, CoreMatchers.is(emap.get(K1)));
    Assert.assertThat("K2 expired", null, CoreMatchers.is(emap.get(K2)));
    Assert.assertThat("K3 still there", V3, CoreMatchers.is(emap.get(K3)));
    Assert.assertThat("2 entries", 2, CoreMatchers.is(emap.size()));

    Thread.sleep(TimeUnit.MILLISECONDS.toMillis(1000));
    // t+2.5
    Assert.assertThat("K1 still there", V1, CoreMatchers.is(emap.get(K1)));
    Assert.assertThat("K3 expired", null, CoreMatchers.is(emap.get(K3)));
    Assert.assertThat("1 entry", 1, CoreMatchers.is(emap.size()));

    Thread.sleep(TimeUnit.MILLISECONDS.toMillis(1000));
    // t+3.5
    Assert.assertThat("K1 expired", null, CoreMatchers.is(emap.get(K1)));
    Assert.assertThat("0 entry", 0, CoreMatchers.is(emap.size()));
  }

  @Test
  public void testExpireRemoved() throws InterruptedException {
    // t+0: add K1 expiring at t+2
    emap.put(K1, V1, TimeUnit.SECONDS.toMillis(2));

    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    // t+1
    emap.remove(K1);

    Thread.sleep(TimeUnit.SECONDS.toMillis(2));
    // t+3: no news is good news
  }

  @Test
  public void testRemoveExpired() throws InterruptedException {
    // t+0: add K1 expiring at t+1
    emap.put(K1, V1, TimeUnit.SECONDS.toMillis(1));

    Thread.sleep(TimeUnit.SECONDS.toMillis(2));
    // t+2: remove the expired K1
    emap.remove(K1);
  }

  @Test
  public void testExpireReadded() throws InterruptedException {
    // t+0: add K1 expiring at t+2
    emap.put(K1, V1, TimeUnit.SECONDS.toMillis(2));

    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    // t+1
    emap.remove(K1);
    emap.put(K1, V1, TimeUnit.SECONDS.toMillis(100));

    Thread.sleep(TimeUnit.SECONDS.toMillis(2));
    // t+3: K1 should be still there
    Assert.assertThat("K1 still there", V1, CoreMatchers.is(emap.get(K1)));
  }
}
