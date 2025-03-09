package com.mpd.concurrent.asyncContext;

import androidx.annotation.NonNull;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.PolyNull;

public interface AsyncContext {


	/**
	 * Gets the current AsyncContext
	 **/
	static AsyncContext getCurrentAsyncContext() {
		return AsyncContextScope.getCurrentAsyncContextScope().getAsyncContext();
	}

	// Non-static members

	Object getName();

	// The Remaining members are the same as ConcurrentHashMap.

	/**
	 * @return the new value associated with the specified key, or null if none
	 */
	<T> @Nullable T compute(
			Class<T> clazz, BiFunction<? super Class<T>, @Nullable ? super T, @Nullable ? extends T> func);

	/**
	 * @return the current (existing or computed) value associated with the specified key, or null if the computed value
	 * 		is null
	 */
	<T> T computeIfAbsent(Class<T> clazz, Function<? super Class<T>, @Nullable ? super T> func);

	/**
	 * @return the new value associated with the specified key, or null if none
	 */
	<T> @PolyNull T computeIfPresent(
			Class<T> clazz, BiFunction<? super Class<T>, @Nullable ? super T, @Nullable ? extends T> func);

	<T> boolean containsKey(Class<T> clazz);

	<T> @Nullable T get(Class<T> clazz);

	<T> T getOrDefault(Class<T> clazz, T defaultValue);

	/**
	 * @return the new value associated with the specified key, or null if none
	 */
	<T> @Nullable T merge(
			Class<T> clazz, T value, BiFunction<@Nullable ? super T, @Nullable ? super T, @Nullable ? extends T> func);

	/**
	 * @return the previous value associated with key, or null if there was no mapping for key
	 */
	<T> @Nullable T put(Class<T> clazz, T value);

	/**
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key
	 */
	<T> @Nullable T putIfAbsent(Class<T> clazz, T value);

	<T> boolean remove(Class<T> clazz, T value);

	/**
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key
	 */
	<T> @Nullable T remove(Class<T> clazz);

	/**
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key
	 */
	<T> T replace(Class<T> clazz, T oldValue, T newValue);

	/**
	 * @return the previous value associated with the specified key, or null if there was no mapping for the key
	 */
	<T> @Nullable T replace(Class<T> clazz, T value);

	/**
	 * dangerous escape hatch
	 */
	ConcurrentHashMap<Class<?>, Object> getRawContextData();

	void toString(StringBuilder sb);

	@NonNull @Override String toString();
}
