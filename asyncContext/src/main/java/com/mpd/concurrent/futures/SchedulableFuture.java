package com.mpd.concurrent.futures;

import java.util.concurrent.Delayed;

// a Runnable that can be submitted to a executor and is its own Future
public interface SchedulableFuture<O> extends SubmittableFuture<O>, Delayed, Comparable<Delayed> {
	boolean setException(Throwable exception);

	boolean setException(Throwable exception, boolean mayInterruptIfRunning);
}
