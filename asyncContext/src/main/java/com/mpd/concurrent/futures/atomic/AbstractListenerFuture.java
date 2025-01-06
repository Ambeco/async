package com.mpd.concurrent.futures.atomic;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.SchedulableFuture;
import com.mpd.concurrent.futures.SubmittableFuture;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

// Listens to parent(s), and then executes itself on the executor.
public abstract class AbstractListenerFuture<O> extends AbstractSubmittableFuture<O>
		implements SubmittableFuture<O>, FutureListener<Object>, SchedulableFuture<O>
{
	protected static final boolean SHOULD_QUEUE_WORK = true;
	protected static final boolean DO_NOT_QUEUE_WORK = true;

	/**
	 * @noinspection unchecked
	 */
	private static final AtomicReferenceFieldUpdater<AbstractListenerFuture<?>, Executor>
			atomicExecutor =
			AtomicReferenceFieldUpdater.newUpdater((Class<AbstractListenerFuture<?>>) (Class<?>) AbstractListenerFuture.class,
					Executor.class,
					"executor");

	private volatile @Nullable Executor executor; // TODO: atomicExecutor

	protected AbstractListenerFuture(
			@Nullable AsyncContext context, @NonNull Executor executor)
	{
		super(context);
		this.executor = executor;
	}

	protected AbstractListenerFuture(
			@Nullable AsyncContext context, @NonNull Executor executor, long delay, TimeUnit delayUnit)
	{
		super(context, delay, delayUnit);
		this.executor = executor;
	}

	// if it wants to immediately complete the future, then it may call {@link setResult or setException or setComplete}
	abstract protected boolean shouldQueueExecutionAfterParentComplete(
			Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning);

	protected void onParentComplete(
			Future<?> future, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
	{
		try {
			Throwable oldThrowable = getExceptionProtected();
			Executor oldExecutor = this.executor;
			if (oldThrowable == SUCCESS_EXCEPTION) { // STATE_SUCCESS
				setException(new SucceededBeforeParentException());
			} else if (oldThrowable != null) { // STATE_FAILED
			} else if (oldExecutor == null) { // STATE_SCHEDULED, STATE_SUBMITTED, STATE_RUNNING, STATE_ASYNC
				setException(new ParentSucceededTwiceException());
			} else { // STATE_LISTENING
				if (!shouldQueueExecutionAfterParentComplete(future, result, exception, mayInterruptIfRunning)) {
					// TODO keep the executor around for a while. Use something else to distinguish double submission
				} else if (!atomicExecutor.compareAndSet(this, oldExecutor, null)) { // another thread changed the state
					setException(new ParentSucceededTwiceException());
				} else { // successful transition to STATE_SUBMITTED
					Throwable interrupt = getInterrupt();
					if (interrupt != null) { // interrupted.
						setException(interrupt, MAY_INTERRUPT);
					} else {
						oldExecutor.submit(this);
					}
				}
			}
		} catch (RuntimeException e) {
			setException(e);
		}
	}

	@CallSuper @Override protected void afterDone(
			@Nullable O result,
			@Nullable Throwable exception,
			boolean mayInterruptIfRunning,
			FutureListener<? super O> listener)
	{
		super.afterDone(result, exception, mayInterruptIfRunning, listener);
		atomicExecutor.lazySet(this, null);
	}

	@Override public void onFutureSucceeded(Future<?> future, @Nullable Object result) {
		onParentComplete(future, result, SUCCESS_EXCEPTION, NO_INTERRUPT);
	}

	@Override public void onFutureFailed(Future<?> future, @Nullable Throwable exception, boolean mayInterruptIfRunning) {
		onParentComplete(future, FAILED_RESULT, exception, mayInterruptIfRunning);
	}

	@CallSuper protected void toStringAppendState(
			@Nullable O result, @Nullable Throwable exception, @Nullable Future<? extends O> setAsync, StringBuilder sb)
	{
		Executor executor = this.executor;
		super.toStringAppendState(result, exception, setAsync, sb);
		if (executor != null) {
			sb.append(" executor=").append(executor);
		}
	}

	public static class SucceededBeforeParentException extends IllegalStateException {
		public SucceededBeforeParentException() {}

		public SucceededBeforeParentException(String message) {
			super(message);
		}

		public SucceededBeforeParentException(@Nullable Throwable cause) {
			super(cause);
		}

		public SucceededBeforeParentException(String message, @Nullable Throwable cause) {
			super(message, cause);
		}
	}

	public static class ParentSucceededTwiceException extends IllegalStateException {
		public ParentSucceededTwiceException() {}

		public ParentSucceededTwiceException(String message) {
			super(message);
		}

		public ParentSucceededTwiceException(@Nullable Throwable cause) {
			super(cause);
		}

		public ParentSucceededTwiceException(String message, @Nullable Throwable cause) {
			super(message, cause);
		}
	}

}
