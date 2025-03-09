package com.mpd.concurrent.futures.locked;

import androidx.annotation.CallSuper;
import com.mpd.concurrent.AsyncCallable;
import com.mpd.concurrent.asyncContext.AsyncContextScope.DeferredContextScope;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.SubmittableFuture;
import com.mpd.concurrent.futures.locked.AbstractListenerFutures.SubmittableListenerFuture;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FutureAsyncCallable<O> extends SubmittableListenerFuture<O> implements SubmittableFuture<O> {
	private @Nullable AsyncCallable<? extends O> function;

	public FutureAsyncCallable(@Nullable DeferredContextScope scope, @NonNull AsyncCallable<? extends O> function) {
		super(scope);
		this.function = function;
	}

	public FutureAsyncCallable(
			@Nullable DeferredContextScope scope,
			@Nullable Future<?> parent,
			@NonNull AsyncCallable<? extends O> function,
			Executor executor)
	{
		super(scope, parent, executor);
		this.function = function;
	}

	public FutureAsyncCallable(
			@Nullable DeferredContextScope scope,
			@Nullable Future<?> parent,
			@NonNull AsyncCallable<? extends O> function,
			long delay,
			TimeUnit delayUnit,
			Executor executor)
	{
		super(scope, parent, delay, delayUnit, executor);
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

	@Override protected @Nullable Object toStringSource() {
		AsyncCallable<? extends O> function = this.function;
		if (function == null) {
			return super.toStringSource();
		} else {
			return this.function;
		}
	}
}
