package com.mpd.concurrent;

import com.mpd.concurrent.futures.Future;

@FunctionalInterface public interface AsyncSupplier<O> {
	Future<O> call();
}