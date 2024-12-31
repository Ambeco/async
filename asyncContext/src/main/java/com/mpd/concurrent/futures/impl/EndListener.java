package com.mpd.concurrent.futures.impl;

import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;

public class EndListener implements FutureListener<Object> {
	public static final EndListener INSTANCE = new EndListener();

	@Override public void onFutureSucceeded(Future<?> future, Object result) {
	}

	@Override public void onFutureFailed(Future<?> future, Throwable exception, boolean mayInterruptIfRunning) {
		if (exception != null) {
			Future.futureConfig.onUnhandledException(exception);
		}
	}
}
