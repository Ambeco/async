package com.mpd.concurrent.asyncContext.impl;

import androidx.annotation.NonNull;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.asyncContext.AsyncContextScope;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;

public abstract class AbstractAsyncContextScope implements AsyncContextScope, AsyncContext {
	public static final int MAX_TO_STRING_DEPTH = 20;

	private static final FluentLogger log = FluentLogger.forEnclosingClass();

	/**
	 * @noinspection unchecked
	 */
	private static final AtomicIntegerFieldUpdater<AbstractAsyncContextScope>
			atomicOutstanding =
			AtomicIntegerFieldUpdater.newUpdater(AbstractAsyncContextScope.class, "outstandingChildren");

	private final @CompileTimeConstant Object name;
	private final @MonotonicNonNull AsyncContextScope parent;
	private ConcurrentHashMap<Class<?>, Object> contextData;
	private volatile boolean ownsContextData;
	private volatile int outstandingChildren = 1;

	public AbstractAsyncContextScope(@CompileTimeConstant Object name) {
		this(name, AsyncContextScope.getCurrentAsyncContextScope());
	}

	public AbstractAsyncContextScope(@CompileTimeConstant Object name, @Nullable AsyncContextScope parent) {
		this.name = name;
		this.parent = parent;
	}

	@Override public void privateOnChildComplete(AsyncContext child) {
		int outstandingCount = atomicOutstanding.decrementAndGet(this);
		if (outstandingCount > 0) {
			log.atFinest().log("AsyncContext %s ended, but %s still has %s outstanding children",
					child.getName(),
					name,
					outstandingCount);
			return;
		}
		log.atFinest().log("AsyncContext %s and all children completed", name);
		onScopeChildrenComplete();
		if (parent == null) {
			onRootComplete();
		} else {
			parent.privateOnChildComplete(this);
		}
	}

	@Override public AsyncContext getAsyncContext() {
		return this;
	}

	@Override public void appendContextStack(StringBuilder sb, int maxDepth) {
		if (parent != null && maxDepth > 0) {
			parent.appendContextStack(sb, maxDepth - 1);
			sb.append('/');
		}
		sb.append(name);
	}

	public @Override @MonotonicNonNull AsyncContextScope getParentScope() {
		return parent;
	}

	public void toString(StringBuilder sb) {
		sb.append(getClass().getSimpleName());
		sb.append('[');
		appendContextStack(sb, MAX_TO_STRING_DEPTH);
		sb.append(']');
	}

	void onScopeChildrenComplete() {}

	void onRootComplete() {}

	@Override public Object getName() {
		return name;
	}

	/**
	 * @return the new value associated with the specified key, or null if none
	 */
	public <T> @Nullable T compute(
			Class<T> clazz, BiFunction<? super Class<T>, @Nullable ? super T, @Nullable ? extends T> func)
	{
		return clazz.cast(mutableData().compute(clazz, (k, v) -> func.apply(clazz, clazz.cast(v))));
	}

	/**
	 * @return the current (existing or computed) value associated with the specified key, or null if the computed value
	 * 		is null
	 */
	public <T> T computeIfAbsent(Class<T> clazz, Function<? super Class<T>, @Nullable ? super T> func) {
		return clazz.cast(mutableData().computeIfAbsent(clazz, k -> func.apply(clazz)));
	}

	/**
	 * @return the new value associated with the specified key, or null if none
	 */
	public <T> @PolyNull T computeIfPresent(
			Class<T> clazz, BiFunction<? super Class<T>, @Nullable ? super T, @Nullable ? extends T> func)
	{
		return clazz.cast(mutableData().computeIfPresent(clazz, (k, v) -> func.apply(clazz, clazz.cast(v))));
	}

	public <T> boolean containsKey(Class<T> clazz) {
		return contextData.containsKey(clazz);
	}

	public <T> @Nullable T get(Class<T> clazz) {
		return clazz.cast(contextData.get(clazz));
	}

	public <T> T getOrDefault(Class<T> clazz, T defaultValue) {
		return clazz.cast(contextData.getOrDefault(clazz, defaultValue));
	}

	/**
	 * @return the new value associated with the specified key, or null if none
	 */
	public <T> @Nullable T merge(
			Class<T> clazz, T value, BiFunction<@Nullable ? super T, @Nullable ? super T, @Nullable ? extends T> func)
	{
		return clazz.cast(mutableData().merge(clazz, value, (o, n) -> func.apply(clazz.cast(o), clazz.cast(n))));
	}

	/**
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public <T> @Nullable T put(Class<T> clazz, T value) {
		return clazz.cast(mutableData().put(clazz, value));
	}

	/**
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key
	 */
	public <T> @Nullable T putIfAbsent(Class<T> clazz, T value) {
		return clazz.cast(mutableData().putIfAbsent(clazz, value));
	}

	public <T> boolean remove(Class<T> clazz, T value) {
		return mutableData().remove(clazz, value);
	}

	/**
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key
	 */
	public <T> @Nullable T remove(Class<T> clazz) {
		return clazz.cast(mutableData().remove(clazz));
	}

	/**
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key
	 */
	public <T> T replace(Class<T> clazz, T oldValue, T newValue) {
		return clazz.cast(mutableData().replace(clazz, oldValue, newValue));
	}

	/**
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key
	 */
	public <T> @Nullable T replace(Class<T> clazz, T value) {
		return clazz.cast(mutableData().replace(clazz, value));
	}

	@Override public ConcurrentHashMap<Class<?>, Object> getRawContextData() {
		return contextData;
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

	@NonNull @Override public String toString() {
		StringBuilder sb = new StringBuilder();
		toString(sb);
		return sb.toString();
	}

	public static final class ContextScopeScopeImpl extends AbstractAsyncContextScope
			implements AsyncContextScope, DeferredContextScope
	{
		@Nullable AsyncContextScope previousScope;

		public ContextScopeScopeImpl(@CompileTimeConstant Object name, @Nullable AsyncContextScope parent) {
			super(name, parent);
			previousScope = parent;
		}

		public static AsyncContextScope newRootScope(@CompileTimeConstant Object name) {
			AsyncContextScope oldScope = currentScope.get();
			AsyncContextScope newScope = new ContextScopeScopeImpl(name, null);
			if (oldScope != null) {
				newScope = config.get().onNewRootInExistingScope(oldScope, newScope);
			}
			log.atFinest().log("Starting new root scope %s", name);
			currentScope.set(newScope);
			return newScope;
		}

		public static DeferredContextScope newDeferredScope(@CompileTimeConstant Object name) {
			AsyncContextScope oldScope = currentScope.get();
			if (oldScope == null) {
				oldScope = config.get().onMissingAsyncContextScope(name);
			}
			return new ContextScopeScopeImpl(name, oldScope);
		}

		@Override public AsyncContextScope resumeAsyncContext() {
			previousScope = currentScope.get();
			AsyncContextScope newScope = this;
			if (previousScope != null && previousScope != getParentScope()) {
				newScope = config.get().onResumeOverLeakedScope(previousScope, newScope);
			}
			log.atFinest().log("Resuming deferred scope %s", getName());
			currentScope.set(newScope);
			return this;
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
	}
}
