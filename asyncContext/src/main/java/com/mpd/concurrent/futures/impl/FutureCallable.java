package com.mpd.concurrent.futures.impl;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.executors.AsyncContext;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.RunnableFuture;
import com.mpd.concurrent.futures.impl.AbstractListenerFutures.SubmittableListenerFuture;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class FutureCallable<O> extends SubmittableListenerFuture<O> implements RunnableFuture<O> {
	private @Nullable Callable<? extends O> function;

	public FutureCallable(@Nullable AsyncContext context, @NonNull Callable<? extends O> function) {
		super(context);
		this.function = function;
	}

	public FutureCallable(
			@Nullable AsyncContext context,
			@Nullable Future<?> parent,
			@NonNull Callable<? extends O> function,
			Executor executor)
	{
		super(context, parent, executor);
		this.function = function;
	}

	public FutureCallable(
			@Nullable AsyncContext context,
			@Nullable Future<?> parent,
			@NonNull Callable<? extends O> function,
			long delay,
			TimeUnit delayUnit,
			Executor executor)
	{
		super(context, parent, delay, delayUnit, executor);
		this.function = function;
	}

	@Override public void execute() throws Exception {
		Callable<? extends O> function = this.function;
		if (function == null) {
			throw new RunCalledTwiceException();
		}
		setResult(function.call());
	}

	@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
		super.onCompletedLocked(e);
		this.function = null;
	}

	@Override protected @Nullable Object toStringSource() {
		Callable<? extends O> function = this.function;
		if (function == null) {
			return super.toStringSource();
		} else {
			return this.function;
		}
	}
}
