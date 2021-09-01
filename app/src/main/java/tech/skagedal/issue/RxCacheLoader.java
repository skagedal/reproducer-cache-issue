package tech.skagedal.issue;

import io.reactivex.rxjava3.core.Single;
import org.checkerframework.checker.nullness.qual.NonNull;

public interface RxCacheLoader<Key extends @NonNull Object, Value extends @NonNull Object> {
  Single<Value> load(Key key);
}
