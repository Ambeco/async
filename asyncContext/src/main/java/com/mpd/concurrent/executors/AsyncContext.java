package com.mpd.concurrent.executors;

import static com.google.common.base.Preconditions.checkNotNull;

import androidx.annotation.NonNull;

import com.google.errorprone.annotations.CompileTimeConstant;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class AsyncContext implements Cloneable {
	private static final ThreadLocal<AsyncContext> currentContext = new ThreadLocal<>();
	private static AsyncContextConfig config = new AsyncContextConfig.DefaultConfig();

	private final @MonotonicNonNull AsyncContext parentContext;
	private final ConcurrentHashMap<Class<?>, Object> contextData;

	private AsyncContext(@CompileTimeConstant String rootName) {
		parentContext = null;
		contextData = new ConcurrentHashMap<>();
		contextData.put(RootContextName.class, new RootContextName(rootName));
		contextData.put(ExecutionContextName.class, new ExecutionContextName(rootName));
	}

	public AsyncContext(AsyncContext other) {
		parentContext = other;
		contextData = new ConcurrentHashMap<>(other.contextData);
		contextData.remove(ExecutionContextName.class);
	}

	public AsyncContext(AsyncContext other, @CompileTimeConstant String name) {
		parentContext = other;
		contextData = new ConcurrentHashMap<>(other.contextData);
		contextData.put(ExecutionContextName.class, new ExecutionContextName(name));
	}

	public static void initConfig(@Nullable AsyncContextConfig config) {
		AsyncContext.config = config;
	}

	public static AsyncContext getCurrentExecutionContext() {
		AsyncContext context = currentContext.get();
		if (context != null) {
			return context;
		} else {
			return config.onMissingTrace();
		}
	}

	public static AsyncContext forkCurrentExecutionContext() {
		AsyncContext context = getCurrentExecutionContext().clone();
		currentContext.set(context);
		return context;
	}

	public static @Nullable AsyncContext resumeExecutionContext(AsyncContext context) {
		AsyncContext oldContext = currentContext.get();
		if (oldContext != null) {
			config.onDuplicateTrace(oldContext);
		}
		currentContext.set(context);
		return oldContext;
	}

	public static void pauseExecutionContext(AsyncContext popContext, @Nullable AsyncContext oldContext) {
		AsyncContext topContext = currentContext.get();
		if (popContext != topContext) {
			config.popTraceMismatch(popContext, oldContext, topContext);
		}
		currentContext.set(oldContext);
	}

	public static AsyncContext setNewRootContext(@CompileTimeConstant String rootName) {
		AsyncContext asyncContext = new AsyncContext(rootName);
		currentContext.set(asyncContext);
		return asyncContext;
	}

	@NonNull @Override public AsyncContext clone() {
		return new AsyncContext(this);
	}

	public String getTopMostName() {
		ExecutionContextName name = getCurrentExecutionContext(ExecutionContextName.class);
		AsyncContext current = this;
		while (name == null && current.parentContext != null) {
			current = current.parentContext;
			name = current.getCurrentExecutionContext(ExecutionContextName.class);
		}
		if (name != null) {
			return name.value;
		} else {
			return checkNotNull(current.getCurrentExecutionContext(RootContextName.class)).value;
		}
	}

	/**
	 * @return the new value associated with the specified key, or null if none
	 */
	public <T> @Nullable T compute(
			Class<T> clazz, BiFunction<? super Class<T>, @Nullable ? super T, @Nullable ? extends T> func)
	{
		return clazz.cast(contextData.compute(clazz, (k, v) -> func.apply(clazz, clazz.cast(v))));
	}

	/**
	 * @return the current (existing or computed) value associated with the specified key, or null if the computed value
	 * 		is null
	 */
	public <T> T computeIfAbsent(Class<T> clazz, Function<? super Class<T>, @Nullable ? super T> func) {
		return clazz.cast(contextData.computeIfAbsent(clazz, k -> func.apply(clazz)));
	}

	/**
	 * @return the new value associated with the specified key, or null if none
	 */
	public <T> @PolyNull T computeIfPresent(
			Class<T> clazz, BiFunction<? super Class<T>, @Nullable ? super T, @Nullable ? extends T> func)
	{
		return clazz.cast(contextData.computeIfPresent(clazz, (k, v) -> func.apply(clazz, clazz.cast(v))));
	}

	public <T> boolean containsKey(Class<T> clazz) {
		return contextData.containsKey(clazz);
	}

	public <T> @Nullable T getCurrentExecutionContext(Class<T> clazz) {
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
		return clazz.cast(contextData.merge(clazz, value, (o, n) -> func.apply(clazz.cast(o), clazz.cast(n))));
	}

	/**
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	public <T> @Nullable T put(Class<T> clazz, T value) {
		return clazz.cast(contextData.put(clazz, value));
	}

	/**
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key
	 */
	public <T> @Nullable T putIfAbsent(Class<T> clazz, T value) {
		return clazz.cast(contextData.putIfAbsent(clazz, value));
	}

	public <T> boolean remove(Class<T> clazz, T value) {
		return contextData.remove(clazz, value);
	}

	/**
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key
	 */
	public <T> @Nullable T remove(Class<T> clazz) {
		return clazz.cast(contextData.remove(clazz));
	}

	/**
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key
	 */
	public <T> T replace(Class<T> clazz, T oldValue, T newValue) {
		return clazz.cast(contextData.replace(clazz, oldValue, newValue));
	}

	/**
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key
	 */
	public <T> @Nullable T replace(Class<T> clazz, T value) {
		return clazz.cast(contextData.replace(clazz, value));
	}

	public static class RootContextName {
		public final String value;

		public RootContextName(String value) {
			this.value = value;
		}
	}

	public static class ExecutionContextName {
		public final String value;

		public ExecutionContextName(String value) {
			this.value = value;
		}
	}
}
