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
			Executor oldExecutor = atomicExecutor.get(this);
			if (oldThrowable == SUCCESS_EXCEPTION) { // STATE_SUCCESS
				setException(new SucceededBeforeParentException("ListenerFuture \""
						+ this
						+ "\" already succeeded with \""
						+ getResultProtected()
						+ "\" before parent \""
						+ future
						+ "\" completed"));
			} else if (oldThrowable != null) { // STATE_FAILED
			} else if (oldExecutor == null) { // STATE_SCHEDULED, STATE_SUBMITTED, STATE_RUNNING, STATE_ASYNC
				setException(new ParentSucceededTwiceException("ListenerFuture \""
						+ this
						+ "\" already submitted to executor before parent \""
						+ future
						+ "\" completed"));
			} else { // STATE_LISTENING
				if (!shouldQueueExecutionAfterParentComplete(future, result, exception, mayInterruptIfRunning)) {
					// TODO keep the executor around for a while. Use something else to distinguish double submission
				} else if (!atomicExecutor.compareAndSet(this, oldExecutor, null)) { // another thread changed the state
					setException(new ParentSucceededTwiceException("ListenerFuture \""
							+ this
							+ "\" already submitted while processing completion of future "
							+ future
							+ "\", implying that the parent completed multiple times"));
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

	@Override public void onFutureSucceeded(Future<?> future, @Nullable Object result) {
		//TODO: OMG FIX :(
		onParentComplete(future, result, SUCCESS_EXCEPTION, NO_INTERRUPT);
	}

	@Override public void onFutureFailed(Future<?> future, @Nullable Throwable exception, boolean mayInterruptIfRunning) {
		//TODO: OMG FIX :(
		onParentComplete(future, FAILED_RESULT, exception, mayInterruptIfRunning);
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

	@CallSuper protected void toStringAppendState(
			@Nullable O result, @Nullable Throwable exception, @Nullable Future<? extends O> setAsync, StringBuilder sb)
	{
		Executor executor = atomicExecutor.get(this);
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
