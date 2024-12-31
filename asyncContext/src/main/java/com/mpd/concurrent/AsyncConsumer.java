package com.mpd.concurrent;

import com.mpd.concurrent.futures.Future;

@FunctionalInterface public interface AsyncConsumer<I> {
	Future<?> consume(I arg);
}