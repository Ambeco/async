package com.mpd.concurrent.futures.atomic;

import android.os.Build.VERSION_CODES;
import androidx.annotation.CallSuper;
import androidx.annotation.RequiresApi;
import com.google.common.flogger.FluentLogger;
import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.asyncContext.AsyncContextScope;
import com.mpd.concurrent.executors.Executor.RunnablePriority;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.SchedulableFuture;
import com.mpd.concurrent.futures.SubmittableFuture;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;

// A Future that can be submitted to an Executor, which may complete synchronously or asynchronously.
public abstract class AsyncContextScopeFuture<O> extends AbstractFuture<O> implements SubmittableFuture<O>,
		FutureListener<@Nullable Object>,
		SchedulableFuture<O>,
		AsyncContextScope,
		AsyncContext
{
	private static final FluentLogger log = FluentLogger.forEnclosingClass();

	/**
	 * @noinspection unchecked
	 */
	private static final AtomicIntegerFieldUpdater<AsyncContextScopeFuture<?>>
			atomicOutstanding =
			AtomicIntegerFieldUpdater.newUpdater((Class<AsyncContextScopeFuture<?>>) (Class<?>) AsyncContextScopeFuture.class,
					"outstandingChildren");

	private final AsyncContextScope parentScope;
	private ConcurrentHashMap<Class<?>, Object> contextData;
	private volatile boolean ownsContextData = false;
	private volatile int outstandingChildren = 1;
	private @Nullable AsyncContextScope previousScope;

	protected AsyncContextScopeFuture()
	{
		parentScope = AsyncContextScope.getCurrentAsyncContextScope();
		this.contextData = parentScope.getAsyncContext().getRawContextData();
	}

	@RequiresApi(api = VERSION_CODES.O) protected AsyncContextScopeFuture(Instant time)
	{
		super(time);
		parentScope = AsyncContextScope.getCurrentAsyncContextScope();
		this.contextData = parentScope.getAsyncContext().getRawContextData();
	}

	protected AsyncContextScopeFuture(long delay, TimeUnit delayUnit)
	{
		super(delay, delayUnit);
		parentScope = AsyncContextScope.getCurrentAsyncContextScope();
		this.contextData = parentScope.getAsyncContext().getRawContextData();
	}

	@Override public Object getName() {
		return null;
	}

	@Override public <T> @Nullable T compute(Class<T> clazz, BiFunction<? super Class<T>, ? super T, ? extends T> func) {
		return clazz.cast(mutableData().compute(clazz, (k, v) -> func.apply(clazz, clazz.cast(v))));
	}

	@Override public <T> T computeIfAbsent(Class<T> clazz, Function<? super Class<T>, ? super T> func) {
		return clazz.cast(mutableData().computeIfAbsent(clazz, k -> func.apply(clazz)));
	}

	@Override
	public <T> @PolyNull T computeIfPresent(Class<T> clazz, BiFunction<? super Class<T>, ? super T, ? extends T> func) {
		return clazz.cast(mutableData().computeIfPresent(clazz, (k, v) -> func.apply(clazz, clazz.cast(v))));
	}

	@Override public <T> boolean containsKey(Class<T> clazz) {
		return contextData.containsKey(clazz);
	}

	@Override public <T> @Nullable T get(Class<T> clazz) {
		return clazz.cast(contextData.get(clazz));
	}

	@Override public <T> T getOrDefault(Class<T> clazz, T defaultValue) {
		return clazz.cast(contextData.getOrDefault(clazz, defaultValue));
	}

	@Override public <T> @Nullable T merge(Class<T> clazz, T value, BiFunction<? super T, ? super T, ? extends T> func) {
		return clazz.cast(mutableData().merge(clazz, value, (o, n) -> func.apply(clazz.cast(o), clazz.cast(n))));
	}

	@Override public <T> @Nullable T put(Class<T> clazz, T value) {
		return clazz.cast(mutableData().put(clazz, value));
	}

	@Override public <T> @Nullable T putIfAbsent(Class<T> clazz, T value) {
		return clazz.cast(mutableData().putIfAbsent(clazz, value));
	}

	@Override public <T> boolean remove(Class<T> clazz, T value) {
		return mutableData().remove(clazz, value);
	}

	@Override public <T> @Nullable T remove(Class<T> clazz) {
		return clazz.cast(mutableData().remove(clazz));
	}

	@Override public <T> T replace(Class<T> clazz, T oldValue, T newValue) {
		return clazz.cast(mutableData().replace(clazz, oldValue, newValue));
	}

	@Override public <T> @Nullable T replace(Class<T> clazz, T value) {
		return clazz.cast(mutableData().replace(clazz, value));
	}

	@Override public ConcurrentHashMap<Class<?>, Object> getRawContextData() {
		return contextData;
	}

	@Override public void privateOnChildComplete(AsyncContext child) {
		int outstandingCount = atomicOutstanding.decrementAndGet(this);
		if (outstandingCount > 0) {
			log.atFinest().log("AsyncContext %s ended, but %s still has %s outstanding children",
					child.getName(),
					getName(),
					outstandingCount);
			return;
		}
		log.atFinest().log("AsyncContext %s and all children completed", getName());
		parentScope.privateOnChildComplete(this);
	}

	@Override public void close() {
		AsyncContextScope currentScope = AsyncContextScope.currentScope.get();
		if (currentScope != this) {
			config.get().onEndOverLeakedScope(currentScope, this, previousScope);
		}
		log.atFinest().log("Exiting deferred scope %s", getName());
		AsyncContextScope.currentScope.set(previousScope);
		privateOnChildComplete(this);
	}

	@Override public void appendContextStack(StringBuilder sb, int maxDepth) {
		if (parentScope != null && maxDepth > 0) {
			parentScope.appendContextStack(sb, maxDepth - 1);
			sb.append('/');
		}
		sb.append(getName());
	}

	@Override public @MonotonicNonNull AsyncContextScope getParentScope() {
		return parentScope;
	}

	@Override public void toString(StringBuilder sb) {
		toString(sb, TO_STRING_WITH_STATE);
	}

	@Override public AsyncContext getAsyncContext() {
		return this;
	}

	@Override public RunnablePriority getRunnablePriority() {
		return getOrDefault(RunnablePriority.class, RunnablePriority.PRIORITY_DEFAULT);
	}

	@CallSuper @Override protected void afterDone(
			@Nullable O result,
			@Nullable Throwable exception,
			boolean mayInterruptIfRunning,
			FutureListener<? super O> listener)
	{
		super.afterDone(result, exception, mayInterruptIfRunning, listener);
		previousScope = null;
	}

	public AsyncContextScope resumeAsyncContext() {
		previousScope = currentScope.get();
		AsyncContextScope newScope = this;
		if (previousScope != null && previousScope != getParentScope()) {
			newScope = config.get().onResumeOverLeakedScope(previousScope, newScope);
		}
		log.atFinest().log("Resuming deferred scope %s", getName());
		currentScope.set(newScope);
		return this;
	}

	protected ConcurrentHashMap<Class<?>, Object> mutableData() {
		if (!ownsContextData) {
			synchronized (this) {
				if (!ownsContextData) {
					contextData = new ConcurrentHashMap<>(this.contextData);
					ownsContextData = true;
				}
			}
		}
		return contextData;
	}
}
