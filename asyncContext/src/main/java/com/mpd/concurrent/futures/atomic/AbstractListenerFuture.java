package com.mpd.concurrent.futures.atomic;

import androidx.annotation.CallSuper;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.StackSize;
import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.SchedulableFuture;
import com.mpd.concurrent.futures.SubmittableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// Listens to parent(s), and then executes itself on the executor.
public abstract class AbstractListenerFuture<O> extends AbstractSubmittableFuture<O>
		implements SubmittableFuture<O>, FutureListener<Object>, SchedulableFuture<O>
{
	private static final FluentLogger log = FluentLogger.forEnclosingClass();

	protected static final boolean DO_NOT_QUEUE_WORK = false;
	protected static final boolean SHOULD_QUEUE_WORK = true;

	/**
	 * @noinspection unchecked
	 */
	private static final AtomicReferenceFieldUpdater<AbstractListenerFuture<?>, Executor>
			atomicExecutor =
			AtomicReferenceFieldUpdater.newUpdater((Class<AbstractListenerFuture<?>>) (Class<?>) AbstractListenerFuture.class,
					Executor.class,
					"executor");

	// TODO executor to use stub instead of Nullable?
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

	/**
	 * @noinspection BooleanMethodIsAlwaysInverted
	 */
	protected boolean onParentComplete(
			Future<?> future, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
	{
		if (super.onParentComplete(future, result, exception, mayInterruptIfRunning)) {
			return true;
		}
		Executor oldExecutor = atomicExecutor.get(this);
		if (!shouldQueueExecutionAfterParentComplete(future, result, exception, mayInterruptIfRunning)) {
			// TODO keep the executor around for a while. Use something else to distinguish double submission
		} else if (!atomicExecutor.compareAndSet(this, oldExecutor, null)) { // another thread changed the state
			log.atFinest().log("%s notified %s of completion(%s, %s), but had already submitted itself to an executor");
			setException(new ParentSucceededTwiceException("ListenerFuture \""
					+ this
					+ "\" already submitted while processing completion of future "
					+ future
					+ "\", implying that the parent completed multiple times"));
		} else { // successful transition to STATE_SUBMITTED
			Throwable interrupt = getInterrupt();
			if (interrupt != null) { // interrupted.
				log.atFinest().log("%s notified %s of completion(%s, %s), but we were already interrupted, so set that now");
				setException(interrupt, MAY_INTERRUPT);
			} else {
				log.atFinest().withStackTrace(StackSize.SMALL).log("%s notified %s of completion(%s, %s), so submitting to %s",
						future,
						this,
						result,
						exception,
						oldExecutor);
				oldExecutor.submit(this);
			}
		}
		return true; // assume shouldQueueExecutionAfterParentComplete handles all futures
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
			sb.append(" executor=");
			executor.toString(sb, /*includeState=*/false);
		}
	}

	public static class ParentNotCompleteException extends IllegalStateException {
		public ParentNotCompleteException() {}

		public ParentNotCompleteException(String message) {
			super(message);
		}

		public ParentNotCompleteException(@Nullable Throwable cause) {
			super(cause);
		}

		public ParentNotCompleteException(String message, @Nullable Throwable cause) {
			super(message, cause);
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
