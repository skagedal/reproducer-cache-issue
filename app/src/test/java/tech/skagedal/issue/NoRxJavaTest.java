package tech.skagedal.issue;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(VertxExtension.class)
public class NoRxJavaTest {
  WebClient webClient;
  AsyncLoadingCache<String, String> cache;

  @BeforeEach
  void setUp(Vertx vertx) {
    webClient = WebClient.create(vertx);
    cache = Caffeine.newBuilder()
        .executor(cmd -> vertx.runOnContext(v -> cmd.run()))
        .buildAsync(this::getStatus);
  }

  // This one is ok
  @Test
  void get_url_simple(Vertx vertx, VertxTestContext testContext) throws ExecutionException, InterruptedException {
    final var status = getStatus("https://google.com", null).get();
    System.out.println(status);
    testContext.completeNow();
  }

  // This one is ok
  @Test
  void get_url_cached(Vertx vertx, VertxTestContext testContext) throws ExecutionException, InterruptedException {
    final var status = cache.get("https://google.com").get();
    System.out.println(status);
    testContext.completeNow();
  }

  // This one is not - locks the thread forever:
  // Sep 01, 2021 10:21:05 AM io.vertx.core.impl.BlockedThreadChecker
  //WARNING: Thread Thread[vert.x-eventloop-thread-2,5,main]=Thread[vert.x-eventloop-thread-2,5,main] has been blocked for 13030 ms, time limit is 2000 ms
  //io.vertx.core.VertxException: Thread blocked
  //	at java.base@11.0.11/jdk.internal.misc.Unsafe.park(Native Method)
  //	at java.base@11.0.11/java.util.concurrent.locks.LockSupport.park(LockSupport.java:194)
  //	at java.base@11.0.11/java.util.concurrent.CompletableFuture$Signaller.block(CompletableFuture.java:1796)
  //	at java.base@11.0.11/java.util.concurrent.ForkJoinPool.managedBlock(ForkJoinPool.java:3128)
  //	at java.base@11.0.11/java.util.concurrent.CompletableFuture.waitingGet(CompletableFuture.java:1823)
  //	at java.base@11.0.11/java.util.concurrent.CompletableFuture.get(CompletableFuture.java:1998)
  //	at app//tech.skagedal.issue.NoRxJavaTest.lambda$get_url_cached_from_within_context$2(NoRxJavaTest.java:51)
  //	at app//tech.skagedal.issue.NoRxJavaTest$$Lambda$509/0x00000008003e0c40.handle(Unknown Source)
  //	at app//io.vertx.core.impl.ContextImpl.executeTask(ContextImpl.java:366)
  //	at app//io.vertx.core.impl.EventLoopContext.lambda$executeAsync$0(EventLoopContext.java:38)
  //	at app//io.vertx.core.impl.EventLoopContext$$Lambda$467/0x00000008003c2c40.run(Unknown Source)
  //	at app//io.netty.util.concurrent.AbstractEventExecutor.safeExecute(AbstractEventExecutor.java:164)
  //	at app//io.netty.util.concurrent.SingleThreadEventExecutor.runAllTasks(SingleThreadEventExecutor.java:472)
  //	at app//io.netty.channel.nio.NioEventLoop.run(NioEventLoop.java:500)
  //	at app//io.netty.util.concurrent.SingleThreadEventExecutor$4.run(SingleThreadEventExecutor.java:989)
  //	at app//io.netty.util.internal.ThreadExecutorMap$2.run(ThreadExecutorMap.java:74)
  //	at app//io.netty.util.concurrent.FastThreadLocalRunnable.run(FastThreadLocalRunnable.java:30)
  //	at java.base@11.0.11/java.lang.Thread.run(Thread.java:829)
  @Test
  void get_url_cached_from_within_context(Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(context -> {
      final String status;
      cache.get("https://google.com").whenComplete((str, t) -> {
        if (t == null) {
          System.out.println(str);
          testContext.completeNow();
        } else {
          testContext.failNow(t);
        }
      });
    });
  }

  private CompletableFuture<String> getStatus(String key, Executor executor) {
    CompletableFuture<String> future = new CompletableFuture<>();
    webClient
        .getAbs(key)
        .send(result -> {
          if (result.succeeded()) {
            future.complete(result.result().statusMessage());
          } else {
            future.completeExceptionally(result.cause());
          }
        });
    return future;
  }

}
