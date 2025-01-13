package com.mpd.concurrent.futures.locked;

import androidx.annotation.CallSuper;
import com.mpd.concurrent.futures.Future;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public class FutureTimeout<O> extends AbstractListenerFuture<O> {
	private final boolean interruptOnTimeout;
	private @Nullable Future<? extends O> parent;
	private @Nullable Throwable exceptionOnTimeout;

	public FutureTimeout(
			@NonNull Future<? extends O> parent,
			long delay,
			TimeUnit delayUnit,
			@Nullable Throwable exceptionOnTimeout,
			boolean interruptOnTimeout)
	{
		super(null, delay, delayUnit, Future.futureConfig.getDelegateScheduledExecutor());
		this.parent = parent;
		this.exceptionOnTimeout = exceptionOnTimeout;
		this.interruptOnTimeout = interruptOnTimeout;
		parent.setListener(this);
		Future.futureConfig.getDelegateScheduledExecutor().submit(this);
	}

	@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
		super.onCompletedLocked(e);
		this.parent = null;
		this.exceptionOnTimeout = null;
	}

	@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
		Future<?> parent;
		synchronized (this) {
			parent = this.parent;
		}
		super.onCancelled(exception, mayInterruptIfRunning);
		if (parent != null) {
			parent.cancel(exception, mayInterruptIfRunning);
		}
	}

	@Override protected boolean shouldQueueExecutionAfterParentComplete(
			Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
	{
		setComplete((O) result, exception, mayInterruptIfRunning);
		return false;
	}

	@Override protected void execute() throws Exception {
		Future<?> parent;
		synchronized (this) {
			parent = this.parent;
		}
		if (parent != null) {
			Throwable exceptionOnTimeout;
			boolean interruptOnTimeout;
			synchronized (this) {
				exceptionOnTimeout = this.exceptionOnTimeout;
				interruptOnTimeout = this.interruptOnTimeout;
				this.exceptionOnTimeout = null;
			}
			if (exceptionOnTimeout == null) {
				parent.cancel(interruptOnTimeout);
			} else {
				parent.setException(exceptionOnTimeout, interruptOnTimeout);
			}
		}
	}
}
