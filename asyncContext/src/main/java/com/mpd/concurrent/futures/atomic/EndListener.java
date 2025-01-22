package com.mpd.concurrent.futures.atomic;

import com.google.common.flogger.FluentLogger;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;

public class EndListener implements FutureListener<Object> {
	public static final EndListener INSTANCE = new EndListener();

	private static final FluentLogger log = FluentLogger.forEnclosingClass();

	protected EndListener() {}

	@Override public void onFutureSucceeded(Future<?> future, Object result) {
		log.atFinest().log("%s succeeded without unhandled exceptions", future);
	}

	@Override public void onFutureFailed(Future<?> future, Throwable exception, boolean mayInterruptIfRunning) {
		Future.futureConfig.onUnhandledException(exception);
	}
}
