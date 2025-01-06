package com.mpd.concurrent.futures.locked;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.mpd.concurrent.asyncContext.AsyncContext.getCurrentExecutionContext;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;

import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.executors.Executor.RunnablePriority;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.SchedulableFuture;
import com.mpd.concurrent.futures.SubmittableFuture;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

// Listens to parent(s), and then executes a body, which can execute synchronously or asynchronously.
public abstract class AbstractListenerFuture<O> extends AbstractFuture<O>
		implements SubmittableFuture<O>, FutureListener<Object>, SchedulableFuture<O>
{
	protected static final boolean SHOULD_QUEUE_WORK = true;
	protected static final boolean DO_NOT_QUEUE_WORK = true;
	private @Nullable AsyncContext context;
	private @ListenerFutureState int state;
	private @Nullable Executor executor;
	private @Nullable Future<? extends O> asyncWork = null;
	private @Nullable Thread thread;

	protected AbstractListenerFuture(@Nullable AsyncContext context, @ListenerFutureState int state) {
		this(context, null, state);
	}

	protected AbstractListenerFuture(
			@Nullable AsyncContext context, @Nullable Executor executor, @ListenerFutureState int state)
	{
		this.context = (context == null) ? getCurrentExecutionContext() : context;
		this.state = state;
		this.executor = executor;
	}

	protected AbstractListenerFuture(
			@Nullable AsyncContext context, long delay, TimeUnit delayUnit, @Nullable Executor executor)
	{
		super(delay, delayUnit);
		this.context = (context == null) ? getCurrentExecutionContext() : context;
		state = ListenerFutureState.STATE_SCHEDULED;
		this.executor = executor;
	}

	@Override public RunnablePriority getRunnablePriority() {
		AsyncContext context = this.context;
		if (context == null) {
			return RunnablePriority.PRIORITY_DEFAULT;
		}
		return context.getOrDefault(RunnablePriority.class, RunnablePriority.PRIORITY_DEFAULT);
	}

	@CallSuper @Override protected void onCompletedLocked(@Nullable Throwable e) {
		super.onCompletedLocked(e);
		executor = null;
		asyncWork = null;
		context = null;
		state = ListenerFutureState.STATE_COMPLETE;
	}

	@CallSuper @Override protected synchronized void interruptTask(Throwable exception) {
		if (thread != null) {
			thread.interrupt();
		}
	}

	@CallSuper @Override protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
		Future<?> asyncWork;
		synchronized (this) {
			asyncWork = this.asyncWork;
		}
		super.onCancelled(exception, mayInterruptIfRunning);
		if (asyncWork != null) {
			asyncWork.cancel(exception, mayInterruptIfRunning);
		}
	}

	protected void setResult(Future<? extends O> asyncWork) {
		boolean wasDone = false;
		boolean nowDone = false;
		Throwable completedException = null;
		FutureListener<? super O> listener = null;
		synchronized (this) {
			listener = this.getListenerLocked();
			wasDone = isDoneLocked();
			switch (state) {
				case ListenerFutureState.STATE_LISTENING:
				case ListenerFutureState.STATE_SCHEDULED:
					onCompletingLocked(FAILED_RESULT, new SetResultCalledAfterSuccessException(), NO_INTERRUPT);
					state = ListenerFutureState.STATE_COMPLETE;
					break;
				case ListenerFutureState.STATE_RUN_QUEUED:
					this.asyncWork = asyncWork;
					state = ListenerFutureState.STATE_ASYNC;
					break;
				case ListenerFutureState.STATE_RUNNING:
				case ListenerFutureState.STATE_ASYNC:
					onCompletingLocked(FAILED_RESULT, new SetListenerCalledTwiceException(), NO_INTERRUPT);
					state = ListenerFutureState.STATE_COMPLETE;
					break;
				case ListenerFutureState.STATE_COMPLETE:
					this.asyncWork = asyncWork;
					break;
			}
			nowDone = isDoneLocked();
			if (nowDone) {
				completedException = getExceptionLocked();
			}
		}
		if (!wasDone && nowDone) {
			afterDone(FAILED_RESULT, completedException, NO_INTERRUPT, listener);
		} else if (!nowDone) {
			try {
				asyncWork.setListener(this);
			} catch (Throwable t) {
				setException(t);
			}
		}
	}

	@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
		super.addPendingString(sb, maxDepth);
		Future<? extends O> asyncWork = this.asyncWork;
		if (asyncWork != null) {
			asyncWork.addPendingString(sb, maxDepth - 1);
		}
	}

	@Override public int compareTo(Delayed other) {
		return super.compareTo(other);
	}

	@CallSuper protected void toStringAppendState(
			boolean isDone,
			@Nullable O result,
			@Nullable Throwable exception,
			@Nullable Future<? extends O> setAsync,
			StringBuilder sb)
	{
		super.toStringAppendState(isDone, result, exception, setAsync, sb);
		int state;
		Executor executor;
		Thread thread;
		synchronized (this) {
			state = this.state;
			executor = this.executor;
			thread = this.thread;
		}
		switch (state) {
			case ListenerFutureState.STATE_LISTENING:
			case ListenerFutureState.STATE_ASYNC:
			case ListenerFutureState.STATE_COMPLETE:
				return;
			case ListenerFutureState.STATE_SCHEDULED:
				sb.append(" status=scheduled delay=").append(getDelay(TimeUnit.MILLISECONDS)).append("ms");
				return;
			case ListenerFutureState.STATE_RUN_QUEUED:
				sb.append(" status=queued executor=").append(executor);
				return;
			case ListenerFutureState.STATE_RUNNING:
				sb.append(" status=running executor=").append(executor).append(" tid=");
				if (thread != null) {
					sb.append(thread.getId());
				} else {
					sb.append("null");
				}
				return;
		}
		sb.append(" status=IllegalState(").append(state).append(')');
	}

	// if it wants to immediately complete the future, then it may call {@link onCompletingLocked}
	abstract protected boolean shouldQueueExecutionAfterParentComplete(
			Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning);

	private boolean onParentCompleteLocked(
			Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
	{
		switch (state) {
			case ListenerFutureState.STATE_LISTENING:
			case ListenerFutureState.STATE_SCHEDULED:
				return shouldQueueExecutionAfterParentComplete(parent, result, exception, mayInterruptIfRunning);
			case ListenerFutureState.STATE_RUN_QUEUED:
				if (exception != null) {
					onCompletingLocked(FAILED_RESULT, exception, mayInterruptIfRunning);
				} else {
					onCompletingLocked(FAILED_RESULT, new OnFutureCompleteCalledTwiceException(), NO_INTERRUPT);
				}
				return DO_NOT_QUEUE_WORK;
			case ListenerFutureState.STATE_RUNNING:
			case ListenerFutureState.STATE_ASYNC:
				if (parent == asyncWork) {
					onCompletingLocked((O) result, exception, mayInterruptIfRunning);
				} else if (exception != null) {
					onCompletingLocked(FAILED_RESULT, exception, mayInterruptIfRunning);
				} else {
					onCompletingLocked(FAILED_RESULT, new WrongParentFutureException(), NO_INTERRUPT);
				}
				return DO_NOT_QUEUE_WORK;
			case ListenerFutureState.STATE_COMPLETE:
				if (exception != null) {
					onCompletingLocked(FAILED_RESULT, exception, mayInterruptIfRunning);
				} // else parent succeeded after this failed. If so, no-op
				return DO_NOT_QUEUE_WORK;
		}
		onCompletingLocked(FAILED_RESULT,
				new UnknownFutureStateException(Integer.toString(state), exception),
				NO_INTERRUPT);
		return DO_NOT_QUEUE_WORK;
	}

	private void onParentComplete(
			Future<?> parent, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
	{
		Executor executor = null;
		boolean wasDone = false;
		boolean nowDone = false;
		boolean shouldQueueExecution = false;
		O completedResult = null;
		Throwable completedException = null;
		FutureListener<? super O> listener = null;
		synchronized (this) {
			try {
				executor = this.executor;
				wasDone = this.isDoneLocked();
				listener = this.getListenerLocked();
				shouldQueueExecution = onParentCompleteLocked(parent, result, exception, mayInterruptIfRunning);
				nowDone = this.isDoneLocked();
				if (nowDone) {
					completedException = getExceptionLocked();
					if (completedException == null) {
						completedResult = getDoneLocked();
					}
				}
			} catch (Throwable t) {
				onCompletingLocked(FAILED_RESULT, t, NO_INTERRUPT);
				nowDone = true;
				completedException = t;
			}
		}
		if (mayInterruptIfRunning || (!wasDone && nowDone)) {
			afterDone(completedResult, completedException, mayInterruptIfRunning, listener);
		} else if (!nowDone && shouldQueueExecution) {
			try {
				checkNotNull(executor).submit(this);
			} catch (Throwable t) {
				setException(t);
			}
		} //else either we were already done and still done, or we were still listening and will continue listening
	}

	// consume must either complete the future or call setResult(future);
	protected abstract void execute() throws Exception;

	@Override public final void run() {
		AsyncContext oldContext = null;
		try {
			oldContext = AsyncContext.resumeExecutionContext(context);
			boolean wasDone;
			boolean nowDone;
			FutureListener<? super O> listener;
			Throwable exception = null;
			// validate state and extract values
			synchronized (this) {
				thread = Thread.currentThread();
				wasDone = isDoneLocked();
				listener = getListenerLocked();
				if (wasDone && getExceptionLocked() != null) {
					return; // already failed. abort;
				} else if (isDone()) {
					onCompletingLocked(FAILED_RESULT, new RunCalledTwiceException(), NO_INTERRUPT);
					state = ListenerFutureState.STATE_COMPLETE;
				} else if (state != ListenerFutureState.STATE_RUN_QUEUED) {
					onCompletingLocked(FAILED_RESULT, new RunCalledTwiceException(), NO_INTERRUPT);
					state = ListenerFutureState.STATE_COMPLETE;
				} else {
					state = ListenerFutureState.STATE_ASYNC;
				}
				nowDone = isDoneLocked();
				if (nowDone) {
					exception = getExceptionLocked();
				}
			}
			if (!wasDone && nowDone) {
				afterDone(FAILED_RESULT, exception, NO_INTERRUPT, listener);
				return;
			} else if (state != ListenerFutureState.STATE_ASYNC) {
				return;
			}

			// outside of the lock, do the work
			execute();

			// relock, and double-check the state
			synchronized (this) {
				thread = null;
				wasDone = isDoneLocked();
				if (wasDone) {
					state = ListenerFutureState.STATE_COMPLETE;
				} else if (asyncWork != null) {
					state = ListenerFutureState.STATE_ASYNC;
				} else {
					onCompletingLocked(FAILED_RESULT, new ImplementationDidNotCompleteOrAsyncException(), NO_INTERRUPT);
					state = ListenerFutureState.STATE_COMPLETE;
				}
				nowDone = isDoneLocked();
				if (nowDone) {
					exception = getExceptionLocked();
				}
			}
			if (!wasDone && nowDone) {
				afterDone(FAILED_RESULT, exception, NO_INTERRUPT, listener);
			}
		} catch (Throwable e) {
			thread = null;
			setException(new AsyncCheckedException(e));
			state = ListenerFutureState.STATE_COMPLETE;
		} finally {
			AsyncContext.pauseExecutionContext(context, oldContext);
		}
	}

	@Override public void onFutureSucceeded(Future<?> future, Object result) {
		onParentComplete(future, result, NO_EXCEPTION, NO_INTERRUPT);
	}

	@Override public void onFutureFailed(Future<?> future, Throwable exception, boolean mayInterruptIfRunning) {
		onParentComplete(future, FAILED_RESULT, exception, mayInterruptIfRunning);
	}

	@Retention(RetentionPolicy.SOURCE) @IntDef(
			{
					ListenerFutureState.STATE_LISTENING,
					ListenerFutureState.STATE_SCHEDULED,
					ListenerFutureState.STATE_RUN_QUEUED,
					ListenerFutureState.STATE_RUNNING,
					ListenerFutureState.STATE_ASYNC,
					ListenerFutureState.STATE_COMPLETE}) protected @interface ListenerFutureState {
		int STATE_LISTENING = 1;
		int STATE_SCHEDULED = 2;
		int STATE_RUN_QUEUED = 3;
		int STATE_RUNNING = 4;
		int STATE_ASYNC = 5;
		int STATE_COMPLETE = 6;
	}

	public static class UnknownFutureStateException extends IllegalStateException {
		public UnknownFutureStateException(String message) {
			super(message);
		}

		public UnknownFutureStateException(String message, @Nullable Throwable cause) {
			super(message, cause);
		}
	}

	public static class SetResultCalledAtIllegalState extends IllegalStateException {}

	public static class SetResultCalledAfterSuccessException extends IllegalStateException {}

}
