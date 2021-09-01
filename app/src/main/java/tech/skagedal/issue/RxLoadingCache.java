package tech.skagedal.issue;

import com.github.benmanes.caffeine.cache.AsyncCacheLoader;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * This class thinly wraps an AsyncLoadingCache from the Caffeine library with API that makes it more convenient to use
 * with RxJava.
 *
 * @param <Key> the type of keys maintained by this cache
 * @param <Value> the type of mapped values
 */
public class RxLoadingCache<Key extends @NonNull Object, Value extends @NonNull Object> {
  private final AsyncLoadingCache<Key, Value> asyncLoadingCache;
//  private final Executor executor;

  /**
   * Create an RxLoadingCache
   *
   * @param caffeine A {@link Caffeine} builder - create with {@link Caffeine#newBuilder()} annd use this to specify the
   *                 properties of your cache
   * @param loader the cache loader used to obtain new values - basically a function from {@link Key} to {@link Single<Value>}}.
   *               Compare {@link Caffeine#buildAsync(AsyncCacheLoader)}.
   */
  public RxLoadingCache(
      Vertx vertx,
      Caffeine<@NonNull Object, @NonNull Object> caffeine,
      RxCacheLoader<Key, Value> loader
  ) {
//    executor = createExecutor(vertx.getOrCreateContext());
    asyncLoadingCache = caffeine
        .executor(createExecutor(vertx))
        .buildAsync((key, executor) -> loader.load(key)
            .toCompletionStage().toCompletableFuture());
  }

  private static Executor createExecutor(Vertx vertx) {
    return command -> {
      System.out.println("Running on the executor");
      vertx.runOnContext(v -> command.run());
    };
  }

  /**
   * Obtain a value from the cache. Compare {@link AsyncLoadingCache#get(Object)}.
   * @param key
   * @return
   */
  public Single<Value> get(Key key) {
    return Single.fromFuture(asyncLoadingCache.get(key));
  }
}
