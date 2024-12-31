package com.mpd.concurrent;

import com.mpd.concurrent.futures.Future;

@FunctionalInterface public interface AsyncBiFunction<T, U, R> {
	Future<R> apply(T var1, U var2);
}