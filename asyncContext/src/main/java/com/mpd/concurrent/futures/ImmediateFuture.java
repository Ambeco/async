package com.mpd.concurrent.futures;

import com.mpd.concurrent.futures.atomic.AbstractFuture;

import org.checkerframework.checker.nullness.qual.Nullable;

public class ImmediateFuture<O> extends AbstractFuture<O> {
	public ImmediateFuture(@Nullable O result) {
		super(result);
	}

	public ImmediateFuture(Throwable exception) {
		super(exception);
	}
}
