package com.mpd.concurrent;

import com.mpd.concurrent.futures.Future;

@FunctionalInterface public interface AsyncFunction<I, O> {
	Future<O> apply(I arg);
}
