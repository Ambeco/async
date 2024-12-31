package com.mpd.concurrent.futures.impl;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.AsyncCallable;
import com.mpd.concurrent.executors.AsyncContext;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.SubmittableFuture;
import com.mpd.concurrent.futures.impl.AbstractListenerFutures.SubmittableListenerFuture;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.TimeUnit;

public class FutureAsyncCallable<O> extends SubmittableListenerFuture<O> implements SubmittableFuture<O> {
	private @Nullable AsyncCallable<? extends O> function;

	public FutureAsyncCallable(@Nullable AsyncContext context, @NonNull AsyncCallable<? extends O> function) {
		super(context);
		this.function = function;
	}

	public FutureAsyncCallable(
			@Nullable AsyncContext context,
			@Nullable Future<?> parent,
			@NonNull AsyncCallable<? extends O> function,
			Executor executor)
	{
		super(context, parent, executor);
		this.function = function;
	}

	public FutureAsyncCallable(
			@Nullable AsyncContext context,
			@Nullable Future<?> parent,
			@NonNull AsyncCallable<? extends O> function,
			long delay,
			TimeUnit delayUnit,
			Executor executor)
	{
		super(context, parent, delay, delayUnit, executor);
		this.function = function;
	}

	@Override protected void execute() throws Exception {
		AsyncCallable<? extends O> function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		setResult(function.call());
	}

	@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
		super.onCompletedLocked(e);
		this.function = null;
	}

	@Override protected @Nullable String toStringSource() {
		AsyncCallable<? extends O> function = this.function;
		if (function == null) {
			return super.toStringSource();
		} else {
			return this.function.toString();
		}
	}
}
