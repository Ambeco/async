package com.mpd.test;

import androidx.annotation.NonNull;
import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.qual.Nullable;

public class MockedCall<T> {
	public final T object;
	public final Method method;
	public final @Nullable Object result;
	public final @Nullable Object[] args;

	public MockedCall(T object, Method method, @Nullable Object result, @Nullable Object... args) {
		this.object = object;
		this.method = method;
		this.result = result;
		this.args = args;
	}

	<U> U arg(int index) {
		//noinspection unchecked
		return (U) args[index];
	}

	@NonNull @Override public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(method).append('(');
		for (int i = 0; i < args.length; i++) {
			sb.append(args[i]).append(", ");
		}
		sb.append(") called on ").append(object).append(" with result ").append(result);
		return sb.toString();
	}
}
