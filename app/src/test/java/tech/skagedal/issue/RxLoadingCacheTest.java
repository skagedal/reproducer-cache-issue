package tech.skagedal.issue;

import com.github.benmanes.caffeine.cache.Caffeine;
import hu.akarnokd.rxjava3.bridge.RxJavaBridge;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
class RxLoadingCacheTest {
  WebClient webClient;
  RxLoadingCache<String, String> cache;

  @BeforeEach
  void setUp(Vertx vertx) {
    webClient = WebClient.create(new io.vertx.reactivex.core.Vertx(vertx));
    cache = new RxLoadingCache<>(vertx, Caffeine.newBuilder(), this::getStatus);
  }

  // This one is ok
  @Test
  void get_url_simple(Vertx vertx, VertxTestContext testContext) {
    getStatus("https://google.com").subscribe(status -> {
      System.out.println(status);
      testContext.completeNow();
    });
  }

  // This one is ok
  @Test
  void get_url_cached(Vertx vertx, VertxTestContext testContext) {
    cache.get("https://google.com").subscribe(status -> {
      System.out.println(status);
      testContext.completeNow();
    });
  }

  // This one is not ok - blocks thread!
  @Test
  void get_url_cached_from_within_context(Vertx vertx, VertxTestContext testContext) {
    getStatus("https://google.com").subscribe(status -> {
      System.out.println(status);
      cache.get("https://google.com").subscribe(status2 -> {
        System.out.println(status);
        testContext.completeNow();
      });
    });
  }

  // Same as above
  @Test
  void get_url_cached_from_within_context2(Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(context ->
        cache.get("https://google.com").subscribe(status -> {
          System.out.println(status);
          testContext.completeNow();
        }));
  }

  // Sep 01, 2021 9:32:13 AM io.vertx.core.impl.BlockedThreadChecker
  //WARNING: Thread Thread[vert.x-eventloop-thread-2,5,main]=Thread[vert.x-eventloop-thread-2,5,main] has been blocked for 3789 ms, time limit is 2000 ms
  //Sep 01, 2021 9:32:14 AM io.vertx.core.impl.BlockedThreadChecker
  //WARNING: Thread Thread[vert.x-eventloop-thread-2,5,main]=Thread[vert.x-eventloop-thread-2,5,main] has been blocked for 4790 ms, time limit is 2000 ms
  //Sep 01, 2021 9:32:15 AM io.vertx.core.impl.BlockedThreadChecker
  //WARNING: Thread Thread[vert.x-eventloop-thread-2,5,main]=Thread[vert.x-eventloop-thread-2,5,main] has been blocked for 5792 ms, time limit is 2000 ms
  //io.vertx.core.VertxException: Thread blocked
  //	at java.base@11.0.11/jdk.internal.misc.Unsafe.park(Native Method)
  //	at java.base@11.0.11/java.util.concurrent.locks.LockSupport.park(LockSupport.java:194)
  //	at java.base@11.0.11/java.util.concurrent.CompletableFuture$Signaller.block(CompletableFuture.java:1796)
  //	at java.base@11.0.11/java.util.concurrent.ForkJoinPool.managedBlock(ForkJoinPool.java:3128)
  //	at java.base@11.0.11/java.util.concurrent.CompletableFuture.waitingGet(CompletableFuture.java:1823)
  //	at java.base@11.0.11/java.util.concurrent.CompletableFuture.get(CompletableFuture.java:1998)
  //	at app//io.reactivex.rxjava3.internal.operators.flowable.FlowableFromFuture.subscribeActual(FlowableFromFuture.java:43)
  //	at app//io.reactivex.rxjava3.core.Flowable.subscribe(Flowable.java:15868)
  //	at app//io.reactivex.rxjava3.internal.operators.flowable.FlowableSingleSingle.subscribeActual(FlowableSingleSingle.java:39)
  //	at app//io.reactivex.rxjava3.core.Single.subscribe(Single.java:4813)
  //	at app//io.reactivex.rxjava3.core.Single.subscribe(Single.java:4799)
  //	at app//io.reactivex.rxjava3.core.Single.subscribe(Single.java:4768)
  //	at app//tech.skagedal.issue.RxLoadingCacheTest.lambda$get_url_cached_from_within_context2$5(RxLoadingCacheTest.java:62)
  //	at app//tech.skagedal.issue.RxLoadingCacheTest$$Lambda$478/0x00000008002a8840.handle(Unknown Source)
  //	at app//io.vertx.core.impl.ContextImpl.executeTask(ContextImpl.java:366)
  //	at app//io.vertx.core.impl.EventLoopContext.lambda$executeAsync$0(EventLoopContext.java:38)
  //	at app//io.vertx.core.impl.EventLoopContext$$Lambda$479/0x00000008002a8c40.run(Unknown Source)
  //	at app//io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:164)
  //	at app//io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:472)
  //	at app//io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:500)
  //	at app//io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:989)
  //	at app//io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
  //	at app//io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
  //	at java.base@11.0.11/java.lang.Thread.run(Thread.java:829)

  Single<String> getStatus(String url) {
    return webClient
        .getAbs(url)
        .rxSend()
        .map(HttpResponse::statusMessage)
        .as(RxJavaBridge.toV3Single())
        // Adding this "solves" the problem
        // .subscribeOn(Schedulers.computation())
        .doOnSubscribe(d -> System.out.printf("Fetching %s\n", url))
        .doOnSuccess(result -> System.out.printf("Succesfully fetched %s: %s\n", url, result))
        .doOnError(error -> System.out.printf("Error fetching %s: %s\n", url, error));
  }
}