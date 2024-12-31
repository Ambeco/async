package com.mpd.concurrent;

import com.mpd.concurrent.futures.Future;

public interface AsyncCallable<O> {
	Future<O> call();
}
