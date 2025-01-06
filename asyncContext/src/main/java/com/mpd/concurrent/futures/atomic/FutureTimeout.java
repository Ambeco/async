package com.mpd.concurrent.futures.atomic;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FutureTimeout<O> extends AbstractListenerFuture<O> {
	private final boolean interruptOnTimeout;
	private volatile @Nullable Future<? extends O> parent;
	private volatile @Nullable Throwable exceptionOnTimeout;

	public FutureTimeout(
			@NonNull Future<? extends O> parent,
			long delay,
			TimeUnit delayUnit,
			@Nullable Throwable exceptionOnTimeout,
			boolean interruptOnTimeout)
	{
		super(null, Future.futureConfig.getDelegateScheduledExecutor(), delay, delayUnit);
		this.parent = parent;
		this.exceptionOnTimeout = exceptionOnTimeout != null ? exceptionOnTimeout : new TimeoutException();
		this.interruptOnTimeout = interruptOnTimeout;
		parent.setListener(this);
		Future.futureConfig.getDelegateScheduledExecutor().submit(this);
	}

	@Override protected boolean shouldQueueExecutionAfterParentComplete(
			Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
	{
		//noinspection unchecked
		setComplete((O) result, exception, mayInterruptIfRunning);
		return false;
	}

	@CallSuper @Override protected void afterDone(
			@Nullable O result,
			@Nullable Throwable exception,
			boolean mayInterruptIfRunning,
			FutureListener<? super O> listener)
	{
		super.afterDone(result, exception, mayInterruptIfRunning, listener);
		this.parent = null;
		this.exceptionOnTimeout = null;
	}

	@Override protected void execute() throws Exception {
		Future<?> parent = this.parent;
		if (parent != null) {
			parent.setException(exceptionOnTimeout, interruptOnTimeout);
		}
	}

	@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
		Future<?> parent = this.parent;
		super.onCancelled(exception, mayInterruptIfRunning);
		if (parent != null) {
			parent.cancel(exception, mayInterruptIfRunning);
		}
	}
}
