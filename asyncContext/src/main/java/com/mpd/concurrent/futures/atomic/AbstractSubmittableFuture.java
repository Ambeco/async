package com.mpd.concurrent.futures.atomic;

import static com.mpd.concurrent.asyncContext.AsyncContext.getCurrentExecutionContext;

import androidx.annotation.CallSuper;

import com.mpd.concurrent.asyncContext.AsyncContext;
import com.mpd.concurrent.executors.Executor.RunnablePriority;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.SchedulableFuture;
import com.mpd.concurrent.futures.SubmittableFuture;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

// A Future that can be submitted to an Executor, which may complete synchronously or asynchronously.
public abstract class AbstractSubmittableFuture<O> extends AbstractFuture<O>
		implements SubmittableFuture<O>, FutureListener<@Nullable Object>, SchedulableFuture<O>
{
	// This class used to have a `state` field, but keeping it synchronized was error-prone. Instead we now deduce state.
	// STATE_LISTENING can only be accurately distinguished by derived classes: we don't know how many parents there are.
	// STATE_SCHEDULED can be distinguished by `#getScheduledTimeNanos()`
	// STATE_SUBMITTED can't trivially be distinguished, but that doesn't seem to be a problem.
	// STATE_RUNNING can be distinguished via #thread
	// STATE_ASYNC can be distinguished via #getSetAsync()
	// STATE_SUCCESS and STATE_FAILED can be distinguished via #getExceptionProtected()

	/**
	 * @noinspection unchecked
	 */
	private static final AtomicReferenceFieldUpdater<AbstractSubmittableFuture<?>, Thread>
			atomicThread =
			AtomicReferenceFieldUpdater.newUpdater((Class<AbstractSubmittableFuture<?>>) (Class<?>) AbstractSubmittableFuture.class,
					Thread.class,
					"thread");

	private @Nullable AsyncContext context;
	private volatile @Nullable Thread thread; // TODO: atomicThread

	protected AbstractSubmittableFuture(
			@Nullable AsyncContext context)
	{
		this.context = (context == null) ? getCurrentExecutionContext() : context;
	}

	protected AbstractSubmittableFuture(
			@Nullable AsyncContext context, long delay, TimeUnit delayUnit)
	{
		super(delay, delayUnit);
		this.context = (context == null) ? getCurrentExecutionContext() : context;
	}

	@Override public RunnablePriority getRunnablePriority() {
		AsyncContext context = this.context;
		if (context == null) {
			return RunnablePriority.PRIORITY_DEFAULT;
		}
		return context.getOrDefault(RunnablePriority.class, RunnablePriority.PRIORITY_DEFAULT);
	}

	// consume must either complete the future or call setResult(future);
	protected abstract void execute() throws Exception;

	@CallSuper protected boolean startRunning() {
		if (!atomicThread.compareAndSet(this, null, Thread.currentThread())) { // was already STATE_RUNNING
			setException(new RunCalledTwiceException());
			return false;
		}
		Throwable oldException = getExceptionProtected();
		// STATE_FAILED
		if (oldException == SUCCESS_EXCEPTION || getSetAsync() != null) { // STATE_SUCCESS || STATE_ASYNC
			setException(new RunCalledTwiceException());
			return false;
		} else {
			return oldException == null;
		}
		// STATE_LISTENING || STATE_SCHEDULED || STATE_SUBMITTED
	}

	protected void endRunning() {
		Thread currentThread = Thread.currentThread();
		Throwable oldException = getExceptionProtected();
		if (oldException == null && getSetAsync() == null) { // NOT (STATE_SUCCESS || STATE_FAILED || STATE_ASYNC)
			if (atomicThread.get(this) == currentThread) { // STATE_RUNNING
				setException(new RunDidNotSetFutureCompletionException());
			} else { //STATE_LISTENING || STATE_SCHEDULED || STATE_SUBMITTED
				setException(new FutureStateMachineWentBackwardsException());
			}
		}
		atomicThread.set(this, null);
	}

	@Override public final void run() {
		AsyncContext oldContext = null;
		try {
			oldContext = AsyncContext.resumeExecutionContext(context);

			if (startRunning()) { // if changed to STATE_RUNNING
				execute();
			}
			endRunning(); // cleanup
		} catch (Throwable e) {
			setException(e);
		} finally {
			AsyncContext.pauseExecutionContext(context, oldContext);
		}
	}

	@CallSuper @Override protected void afterDone(
			@Nullable O result,
			@Nullable Throwable exception,
			boolean mayInterruptIfRunning,
			FutureListener<? super O> listener)
	{
		super.afterDone(result, exception, mayInterruptIfRunning, listener);
		context = null;
		atomicThread.lazySet(this, null);
	}

	@CallSuper protected boolean setResult(Future<? extends O> asyncWork) {
		Throwable oldException = getExceptionProtected();
		if (oldException != null) { // STATE_SUCCESS || STATE_FAILED
			return super.setResult(asyncWork);
		} else if (thread == null) { // STATE_LISTENING || STATE_SCHEDULED || STATE_SUBMITTED || STATE_ASYNC
			setException(new SetResultCalledBeforeRunException());
			return false;
		} else { // STATE_RUNNING
			return super.setResult(asyncWork);
		}
	}

	@CallSuper @Override public void onFutureSucceeded(Future<?> parent, @Nullable Object result) {
		Future<?> setAsync = getSetAsync();
		if (parent == setAsync) {
			//noinspection unchecked
			setComplete((O) result, SUCCESS_EXCEPTION, NO_INTERRUPT);
		} else {
			setComplete(FAILED_RESULT, new WrongParentFutureException(), NO_INTERRUPT);
		}
	}

	@CallSuper @Override
	public void onFutureFailed(Future<?> parent, Throwable exception, boolean mayInterruptIfRunning) {
		Future<?> setAsync = getSetAsync();
		if (parent == setAsync) {
			setComplete(FAILED_RESULT, exception, mayInterruptIfRunning);
		} else {
			setComplete(FAILED_RESULT, new WrongParentFutureException(), NO_INTERRUPT);
		}
	}

	@CallSuper @Override protected void interruptTask(Throwable exception) {
		Thread thread = atomicThread.get(this);
		if (thread != null) {
			thread.interrupt();
		}
		super.interruptTask(exception);
	}

	@CallSuper protected void toStringAppendState(
			@Nullable O result, @Nullable Throwable exception, @Nullable Future<? extends O> setAsync, StringBuilder sb)
	{
		Thread thread = this.thread;
		super.toStringAppendState(result, exception, setAsync, sb);
		if (thread != null) {
			sb.append(" thread=").append(thread);
		}
	}

	public static class FutureStateMachineWentBackwardsException extends IllegalStateException {
		public FutureStateMachineWentBackwardsException() {}

		public FutureStateMachineWentBackwardsException(String message) {
			super(message);
		}

		public FutureStateMachineWentBackwardsException(Throwable throwable) {
			super(throwable);
		}

		public FutureStateMachineWentBackwardsException(String message, @Nullable Throwable cause) {
			super(message, cause);
		}
	}

	public static class RunDidNotSetFutureCompletionException extends IllegalStateException {
		public RunDidNotSetFutureCompletionException() {}

		public RunDidNotSetFutureCompletionException(String message) {
			super(message);
		}

		public RunDidNotSetFutureCompletionException(Throwable throwable) {
			super(throwable);
		}

		public RunDidNotSetFutureCompletionException(String message, @Nullable Throwable cause) {
			super(message, cause);
		}
	}


	public static class SetResultCalledBeforeRunException extends IllegalStateException {
		public SetResultCalledBeforeRunException() {}

		public SetResultCalledBeforeRunException(String message) {
			super(message);
		}

		public SetResultCalledBeforeRunException(Throwable throwable) {
			super(throwable);
		}

		public SetResultCalledBeforeRunException(String message, @Nullable Throwable throwable) {
			super(message, throwable);
		}
	}

}
