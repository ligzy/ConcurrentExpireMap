import java.util.concurrent.TimeUnit;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;


/**
 * Add a class comment here
 */
public class TestExpireMap {
  static final String K1 = "key1";
  static final String V1 = "value1";
  
  private ExpireMap<String, String> emap;

  public TestExpireMap() {
    this.emap = new MockExpireMap<>();
  }
  
  @Test
  public void testBasic() {
    Assert.assertThat(null, CoreMatchers.is(emap.get(K1)));
    emap.put(K1, V1, TimeUnit.SECONDS.toMillis(1));
    Assert.assertThat(V1, CoreMatchers.is(emap.get(K1)));
    emap.remove(K1);
    Assert.assertThat(null, CoreMatchers.is(emap.get(K1)));
  }

  @Test
  public void testExpire() throws InterruptedException {
    emap.put(K1, V1, TimeUnit.SECONDS.toMillis(2));
    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
    Assert.assertThat(V1, CoreMatchers.is(emap.get(K1)));
    Thread.sleep(TimeUnit.SECONDS.toMillis(2));
    Assert.assertThat(null, CoreMatchers.is(emap.get(K1)));
  }
}
