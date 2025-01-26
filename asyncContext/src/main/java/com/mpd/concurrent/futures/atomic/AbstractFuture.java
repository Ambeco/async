package com.mpd.concurrent.futures.atomic;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.StackSize;
import com.mpd.concurrent.executors.MoreExecutors;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.atomic.AbstractListenerFuture.SucceededBeforeParentException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// FutureListener<Object>, because derived classes listen to multiple other futures of various types in addition
public abstract class AbstractFuture<O> implements Future<O>, FutureListener<Object> {
	private static final FluentLogger log = FluentLogger.forEnclosingClass();
	// Enables additional logging for leaked futures, at the expense of dramatically slower constructors.
	private static final boolean DEBUG_LEAKED_FUTURES = true;

	protected static final long NOT_SCHEDULED = Long.MIN_VALUE;
	protected static final RuntimeException SUCCESS_EXCEPTION = new SuccessException();
	private static final int NANOS_PER_MILLI = 1000000;
	private static final int NANOS_PER_SECOND = 1000000000;
	private static final ListenerAlreadyDispatched LISTENER_ALREADY_DISPATCHED = new ListenerAlreadyDispatched();
	/**
	 * @noinspection unchecked
	 */
	private static final AtomicReferenceFieldUpdater<AbstractFuture<?>, FutureListener<?>>
			atomicListener =
			AtomicReferenceFieldUpdater.newUpdater((Class<AbstractFuture<?>>) (Class<?>) AbstractFuture.class,
					(Class<FutureListener<?>>) (Class<?>) FutureListener.class,
					"listener");
	// the generic type
	/**
	 * @noinspection unchecked
	 */
	private static final AtomicReferenceFieldUpdater<AbstractFuture<?>, Future<?>>
			atomicSetAsync =
			AtomicReferenceFieldUpdater.newUpdater((Class<AbstractFuture<?>>) (Class<?>) AbstractFuture.class,
					(Class<Future<?>>) (Class<?>) Future.class,
					"setAsync");
	/**
	 * @noinspection unchecked
	 */
	private static final AtomicReferenceFieldUpdater<AbstractFuture<?>, Throwable>
			atomicException =
			AtomicReferenceFieldUpdater.newUpdater((Class<AbstractFuture<?>>) (Class<?>) AbstractFuture.class,
					Throwable.class,
					"exception");

	@SuppressWarnings("ConstantConditions") protected final @Nullable O FAILED_RESULT = null; //pseudo-static

	// TODO listener + setAsync to use stubs instead of Nullable?
	private final long scheduledNanos;
	private final @MonotonicNonNull RuntimeException futureConstructionLocation;
	private volatile @Nullable Future<? extends O> setAsync = null; // TODO: atomicSetAsync
	private volatile @MonotonicNonNull Throwable exception = null; // TODO: atomicExeption
	private volatile @MonotonicNonNull O result = null;
	private volatile @MonotonicNonNull RuntimeException wrappedException = null;
	private volatile @MonotonicNonNull Throwable wasInterrupted = null;
	private volatile @MonotonicNonNull FutureListener<? super O> listener = null; // TODO: atomicListener

	protected AbstractFuture() {
		scheduledNanos = NOT_SCHEDULED;
		futureConstructionLocation = DEBUG_LEAKED_FUTURES ? new LeakedFutureException("Future created here") : null;
	}

	protected AbstractFuture(long delay, TimeUnit delayUnit) {
		scheduledNanos = System.nanoTime() + delayUnit.toNanos(delay);
		futureConstructionLocation = DEBUG_LEAKED_FUTURES ? new LeakedFutureException("Future created here") : null;
	}

	protected AbstractFuture(@Nullable O result) {
		this.exception = SUCCESS_EXCEPTION;
		this.wrappedException = SUCCESS_EXCEPTION;
		this.result = result;
		scheduledNanos = NOT_SCHEDULED;
		futureConstructionLocation = DEBUG_LEAKED_FUTURES ? new LeakedFutureException("Future created here") : null;
	}

	protected AbstractFuture(Throwable exception) {
		this.exception = exception;
		this.wrappedException =
				(exception instanceof RuntimeException)
						? ((RuntimeException) exception)
						: (new AsyncCheckedException(exception));
		scheduledNanos = NOT_SCHEDULED;
		futureConstructionLocation = DEBUG_LEAKED_FUTURES ? new LeakedFutureException("Future created here") : null;
	}

	protected static void toStringAppendLimitedRecursion(StringBuilder sb, @Nullable Object object) {
		if (object == null) {
			sb.append((String) null);
		} else if (sb.length() > 512) {
			sb.append(object.getClass());
		} else {
			String text = object.toString();
			if (sb.length() + text.length() > 512) {
				sb.append(object.getClass());
			} else {
				sb.append(object);
			}
		}
	}

	@CallSuper protected void onCompletingLocked(
			@Nullable O result, Throwable exception, boolean mayInterruptIfRunning)
	{
		this.result = result;
		wasInterrupted = mayInterruptIfRunning ? exception : null;
		wrappedException = exception instanceof RuntimeException ? (RuntimeException) exception : new AsyncCheckedException(
				exception);
	}

	@CallSuper protected boolean setComplete(
			@Nullable O result, Throwable exception, boolean mayInterruptIfRunning)
	{
		try {
			Throwable oldException;
			// This is the only real lock in the Futures. It ensures that #result, #interrupted, and #wrapped
			// are stable after #execption is finalized. It also gives us a way to release Threads blocked by #get.

			synchronized (this) {
				oldException = atomicException.get(this);
				// exception is the "source of truth" for future completeness
				if (oldException == null) {
					if (exception == SUCCESS_EXCEPTION) {
						log.atFinest().log("Completing %s with success %s", this, result);
					} else {
						log.atFinest().log(
								"Completing %s with exception %s (interrupt=%s)",
								this,
								exception,
								mayInterruptIfRunning);
					}
					onCompletingLocked(result, exception, mayInterruptIfRunning);
					atomicException.set(this, exception);
					this.notifyAll();
				}
			}
			// we successfully completed the future
			if (oldException == null) {
				afterDone(result, exception, mayInterruptIfRunning, getListener());
				return true;
			}

			// this future was already completed:
			if (exception == SUCCESS_EXCEPTION) {
				if (oldException == SUCCESS_EXCEPTION) { // if already succeeded, throw FutureSucceededTwiceException
					Future.futureConfig.onUnhandledException(new FutureSucceededTwiceException(
							"setComplete tried to succeed with \""
									+ result
									+ "\" (@"
									+ System.identityHashCode(result)
									+ ") but future \""
									+ this
									+ "\" had already succeeded with \""
									+ this.result
									+ "\" (@"
									+ System.identityHashCode(result)
									+ ")"));
				} //else was already failed: silently drop result
			} else if (exception == oldException) {
				log.atFinest().log("%s interrupted with same exception twice. Ignored.", this);
			} else if (!(exception instanceof CancellationException)) { // if already failed, new exception gets handled
				Future.futureConfig.onUnhandledException(new SetExceptionCalledAfterCompleteException(
						"setComplete tried to fail with \"" + exception + " but future \"" + this + "\" had already succeeded",
						exception));
			} // CancellationException can be silently dropped
			return false;

		} catch (RuntimeException e) { // if anything goes wrong: it's an unhandled exception
			if (exception != SUCCESS_EXCEPTION) {
				e.addSuppressed(exception);
			}
			Future.futureConfig.onUnhandledException(e);
			return false;
		}
	}

	@CallSuper protected void afterDone(
			@Nullable O result, Throwable exception, boolean mayInterruptIfRunning, FutureListener<? super O> listener)
	{
		if (mayInterruptIfRunning) {
			log.atFinest().log("%s  interrupting with %s", this, exception);
			interruptTask(exception);
		}
		if (exception instanceof CancellationException) {
			log.atFinest().log("%s cancelling with %s", this, exception);
			onCancelled(((CancellationException) exception), mayInterruptIfRunning);
		}
		if (listener != null && atomicListener.compareAndSet(this, listener, LISTENER_ALREADY_DISPATCHED)) {
			log.atFinest().log("%s completed. Notifying %s", this, listener);
			if (exception == SUCCESS_EXCEPTION) {
				listener.onFutureSucceeded(this, result);
			} else {
				listener.onFutureFailed(this, exception, mayInterruptIfRunning);
			}
		}
		atomicSetAsync.lazySet(this, null);
	}

	@CallSuper protected boolean setResult(O result) {
		return setComplete(result, SUCCESS_EXCEPTION, NO_INTERRUPT);
	}

	@CallSuper protected boolean setResult(Future<? extends O> asyncWork) {
		Throwable oldException = atomicException.get(this);
		boolean didSetAsync = (oldException == null) && atomicSetAsync.compareAndSet(this, null, asyncWork);
		oldException = atomicException.get(this);
		Future<?> oldListener = atomicSetAsync.get(this);
		if (!didSetAsync && oldListener == asyncWork) {
			log.atFinest().log("Future#setResult called twice on %s with the same %s. Weird, but ok", this, asyncWork);
			return true;
		} else if (oldException == SUCCESS_EXCEPTION) {
			atomicSetAsync.compareAndSet(this, asyncWork, null);
			SetResultCalledAfterSuccessException e = new SetResultCalledAfterSuccessException(
					"setResult tried to set the result of \""
							+ this
							+ "\" with the result of \""
							+ asyncWork
							+ "\" but this has already succeeded with \""
							+ this.result
							+ "\"");
			handleSetResultFailure(asyncWork, e);
			Future.futureConfig.onUnhandledException(e);
			return false;
		} else if (oldException != null) {
			atomicSetAsync.compareAndSet(this, asyncWork, null);
			log.atFinest().log("%s #setResult(%s) called after already failed, but sometimes that just happens during "
					+ "cancellation, so we'll just ensure uncaught exceptions get handled", this, asyncWork);
			SetResultCalledAfterFailureException e = new SetResultCalledAfterFailureException(
					"setResult tried to set the result of \""
							+ this
							+ "\" with the result of \""
							+ asyncWork
							+ "\" but this has already succeeded, so the exception went uncaught");
			log.atFinest().log("%s #setResult(%s) called "
					+ "after already failed, but sometimes that just happens during cancellation, so we'll just ensure uncaught"
					+ " exceptions get handled", this, asyncWork);
			handleSetResultFailure(asyncWork, e);
			return false;
		} else if (!didSetAsync) {
			log.atFinest().log("setResult tried to set the result of \"%s\" with the result of \"%s\"", this, asyncWork);
			SetResultCalledTwiceException e = new SetResultCalledTwiceException("setResult tried to set the result of \""
					+ this + " with the result of \"" + asyncWork + "\"");
			handleSetResultFailure(asyncWork, e);
			setException(e);
			return false;
		} else {
			try {
				log.atFinest().log("%s #setResult(%s) succeeded. Registering self as the listener", this, asyncWork);
				asyncWork.setListener(this);
				return true;
			} catch (Throwable e) {
				setException(e);
				return false;
			}
		}
	}

	/**
	 * Ensures that exceptions thrown from asyncWork are passed to the uncaughtExceptionHandler, but wrapped in an outer
	 * exception that explains why setResult failed at all.
	 */
	private void handleSetResultFailure(Future<? extends O> asyncWork, IllegalStateException e) {
		asyncWork.catchingAsync(Throwable.class, e2 -> {
			e.initCause(e2);
			throw e;
		}, MoreExecutors.directExecutor()).end();
	}

	protected @Nullable Future<? extends O> getSetAsync() {
		//noinspection unchecked
		return (@Nullable Future<? extends O>) atomicSetAsync.get(this);
	}

	@Override public boolean isDone() {
		return atomicException.get(this) != null;
	}

	@Override public O resultNow() {
		RuntimeException exception = wrappedException;
		if (exception == null) {
			throw new FutureNotCompleteException("resultNow called on incomplete future " + this);
		} else if (exception == SUCCESS_EXCEPTION) {
			return result;
		} else {
			log.atFiner().withStackTrace(StackSize.SMALL).log("Future resultNow throwing %s", exception);
			throw exception;
		}
	}

	@Override public @Nullable Throwable exceptionNow() {
		Throwable exception = atomicException.get(this);
		if (exception == null) {
			throw new FutureNotCompleteException("exceptionNow called on incomplete future " + this);
		} else if (exception == SUCCESS_EXCEPTION) {
			return null;
		} else {
			return exception;
		}
	}

	@Override @CallSuper @SuppressWarnings("UnusedReturnValue") public boolean setException(Throwable exception) {
		return setComplete(FAILED_RESULT, exception, NO_INTERRUPT);
	}

	@Override @CallSuper @SuppressWarnings("UnusedReturnValue")
	public boolean setException(Throwable exception, boolean mayInterruptIfRunning) {
		return setComplete(FAILED_RESULT, exception, mayInterruptIfRunning);
	}

	@Override public O get(long timeout, TimeUnit unit) throws TimeoutException {
		long timeoutNs = unit.toNanos(timeout);
		long startTimeNanos = System.nanoTime();
		try {
			long untilNs = startTimeNanos + timeoutNs;
			synchronized (this) {
				while (true) {
					RuntimeException exception = wrappedException;
					if (exception == SUCCESS_EXCEPTION) {
						return result;
					} else if (exception != null) {
						log.atFiner().withStackTrace(StackSize.SMALL).log("Future get throwing %s", exception);
						throw exception;
					}
					long remainingNs = untilNs - System.nanoTime();
					if (remainingNs > 0) {
						log.atFine().log(
								"%s blocking for up to %d.%ds, out of a maximum of %d.%ds, waiting for %s to complete",
								Thread.currentThread(),
								remainingNs / NANOS_PER_SECOND,
								remainingNs % NANOS_PER_SECOND,
								timeoutNs / NANOS_PER_SECOND,
								timeoutNs % NANOS_PER_SECOND,
								this);
						this.wait(remainingNs / NANOS_PER_MILLI, (int) (remainingNs % NANOS_PER_MILLI));
					} else {
						long blockedTime = System.nanoTime() - startTimeNanos;
						throw new TimeoutException(Thread.currentThread()
								+ " timed out after "
								+ blockedTime
								+ "ns out of a "
								+ "maximum of "
								+ timeoutNs
								+ "ns, waiting for "
								+ this
								+ " to complete");
					}
				}
			}
		} catch (InterruptedException e) {
			long remainingNanos = System.nanoTime() - startTimeNanos;
			log.atFine().log(
					"%s interrupted after %d.%ds out of a maximum of %d.%dns, while waiting for %s to complete",
					Thread.currentThread(),
					remainingNanos / NANOS_PER_SECOND,
					remainingNanos % NANOS_PER_SECOND,
					timeoutNs / NANOS_PER_SECOND,
					timeoutNs % NANOS_PER_SECOND,
					this);
			Thread.currentThread().interrupt();
			throw new AsyncCheckedException(e);
		}
	}

	@CallSuper @SuppressWarnings("UnusedReturnValue") @Override
	public boolean cancel(CancellationException exception, boolean mayInterruptIfRunning) {
		return setComplete(FAILED_RESULT, exception, mayInterruptIfRunning);
	}

	@CallSuper @Override public <Listener extends FutureListener<? super O>> Listener setListener(Listener listener) {
		if (!atomicListener.compareAndSet(this, null, listener)) {
			FutureListener<?> oldListener = atomicListener.get(this);
			if (oldListener == listener) {
				log.atFinest().log("%s #setListener(%s) called twice. Weird, but safe", this, listener);
				return listener;
			} else if (listener instanceof EndListener) {
				log.atFinest().log("%s #setListener(%s) unnecessarily called called multiple times.", this, listener);
				return listener;
			}
			throw new SetListenerCalledTwiceException("setListener called with \""
					+ listener
					+ "\" but future \""
					+ this
					+ "\" already had listener \""
					+ oldListener);
		}
		Throwable exception = atomicException.get(this);
		// if this was already complete, then notify the listener immediately
		if (exception != null && atomicListener.compareAndSet(this, listener, LISTENER_ALREADY_DISPATCHED)) {
			log.atFinest().log("%s #setListener(%s) succeeded. Since this future was already complete, notifying immediately",
					this,
					listener);
			if (exception == SUCCESS_EXCEPTION) {
				listener.onFutureSucceeded(this, result);
			} else {
				listener.onFutureFailed(this, exception, wasInterrupted != null);
			}
		} else {
			log.atFinest().log("%s #setListener(%s) succeeded. Waiting for completion", this, listener);
		}
		return listener;
	}

	@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
		sb.append("\n  at ");
		Class<?> sourceClass = sourceClass();
		if (sourceClass == null) {
			sourceClass = getClass();
		}
		String method = sourceMethodName();
		String rawCanonicalName = sourceClass.getCanonicalName();
		String rawSimpleName = sourceClass.getSimpleName();
		String canonicalName = rawCanonicalName != null ? rawCanonicalName : rawSimpleName;
		sb.append(canonicalName);
		if (method != null) {
			sb.append('.').append(method);
		}
		sb.append('(');
		String fileName;
		if (rawCanonicalName != null) {
			fileName = rawSimpleName;
		} else {
			int simpleNameEnd = rawSimpleName.indexOf('$');
			int simpleNameStart = rawSimpleName.lastIndexOf('.', simpleNameEnd) + 1;
			if (simpleNameStart < simpleNameEnd) {
				fileName = rawSimpleName.substring(simpleNameStart, simpleNameEnd);
			} else {
				fileName = rawSimpleName;
			}
		}
		sb.append(fileName).append(":0) //");
		toString(sb, TO_STRING_WITH_STATE);
		Future<? extends O> setAsync = getSetAsync();
		if (setAsync != null && maxDepth > 1) {
			setAsync.addPendingString(sb, maxDepth - 1);
		}
	}

	@Override @CallSuper public void toString(StringBuilder sb, boolean includeState) {
		Package pkg = getClass().getPackage();
		if (pkg == AbstractFuture.class.getPackage() || pkg == Future.class.getPackage()) {
			sb.append(getClass().getSimpleName());
		} else {
			sb.append(getClass().getCanonicalName());
		}
		Class<?> sourceClass = sourceClass();
		if (sourceClass != null && sourceClass != getClass()) {
			sb.append('<');
			String sourceCanonicalName = sourceClass.getCanonicalName();
			sb.append(sourceCanonicalName != null ? sourceCanonicalName : sourceClass.getSimpleName());
			sb.append('>');
		} else {
			sb.append('@').append(System.identityHashCode(this));
		}
		if (includeState) {
			sb.append('[');
			toStringAppendState(getResultProtected(), getExceptionProtected(), getSetAsync(), sb);
			sb.append(']');
		}
	}

	protected @Nullable O getResultProtected() {
		return result;
	}

	protected @Nullable Throwable getExceptionProtected() {
		return atomicException.get(this);
	}

	protected @Nullable RuntimeException getWrappedExceptionProtected() {
		return wrappedException;
	}

	protected @Nullable Throwable getInterrupt() {
		return wasInterrupted;
	}

	/**
	 * @return true if the future was processed, false if the future was unknown
	 * @noinspection BooleanMethodIsAlwaysInverted
	 */
	@CallSuper protected boolean onParentComplete(
			Future<?> future, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
	{
		Throwable oldThrowable = getExceptionProtected();
		Future<?> setAsync = getSetAsync();
		if (oldThrowable == SUCCESS_EXCEPTION) { // STATE_SUCCESS
			Future.futureConfig.onUnhandledException(new SucceededBeforeParentException("ListenerFuture \""
					+ this
					+ "\" already succeeded with \""
					+ getResultProtected()
					+ "\" before Future \""
					+ future
					+ "\" completed"));
			return true;
		} else if (oldThrowable != null) { // STATE_FAILED
			if (exception == SUCCESS_EXCEPTION) {
				log.atFinest().log("%s notified %s of successful result %s,"
						+ " but that had already failed, so the result is being silently dropped", future, result, this);
				return true;
			} else {
				log.atFinest().log("%s notified %s of completion, but it was already failed, so the %s is unhandled",
						future,
						this,
						exception);
				setException(exception);
				return true;
			}
		} else if (future == setAsync) { //STATE_ASYNC
			if (exception == SUCCESS_EXCEPTION) {
				//noinspection unchecked
				setResult((O) result);
			} else {
				setException(exception, mayInterruptIfRunning);
			}
			return true;
		}
		return false;
	}

	@CallSuper protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
		Future<? extends O> setAsync = getSetAsync();
		if (setAsync != null) {
			setAsync.cancel(exception, mayInterruptIfRunning);
		}
	}

	protected @Nullable FutureListener<? super O> getListener() {
		//noinspection unchecked
		return (FutureListener<? super O>) atomicListener.get(this);
	}

	@Override public long getScheduledTimeNanos() {
		if (scheduledNanos == NOT_SCHEDULED) {
			throw new UnsupportedOperationException(this + " is not a scheduled future");
		}
		return scheduledNanos;
	}

	@CallSuper @Override public void onFutureSucceeded(Future<?> future, Object result) {
		try {
			if (!onParentComplete(future, result, SUCCESS_EXCEPTION, NO_INTERRUPT)) {
				setComplete(
						FAILED_RESULT,
						new WrongParentFutureException(future
								+ " notified "
								+ this
								+ " of success, but we were not expecting that future to notify. Silently "
								+ "dropping result "
								+ result
								+ " and marking "
								+ "this future as failed"),
						NO_INTERRUPT);
			}
		} catch (RuntimeException e) {
			log.atFinest().log("%s when %s notified %s of success", e, future, this);
			setException(e);
		}
	}

	protected long getScheduledTimeNanosProtected() {
		return scheduledNanos;
	}

	@CallSuper @Override
	public void onFutureFailed(Future<?> future, Throwable exception, boolean mayInterruptIfRunning) {
		try {
			if (!onParentComplete(future, null, exception, mayInterruptIfRunning)) {
				setComplete(
						FAILED_RESULT,
						new WrongParentFutureException(future
								+ " notified "
								+ this
								+ " of failure, but we"
								+ " were not expecting that future to notify. failing with "
								+ exception, exception),
						NO_INTERRUPT);
			}
		} catch (RuntimeException e) {
			log.atFinest().log("%s when %s notified %s of exception %s", e, future, this, exception);
			e.addSuppressed(exception);
			setException(e);
		}
	}

	@CallSuper protected void interruptTask(Throwable exception) {
		Throwable realException = atomicException.get(this);
		if (exception != realException) {
			log.atFinest().log("%s originally interrupted %s, but now interrupted with %s", this, realException, exception);
			setException(new OnFutureCompleteCalledTwiceException("interruptTask called on " + this + " with \""
					+ exception
					+ "\", but the future was "
					+ "actually interrupted with \""
					+ realException
					+ "\".\nIf caller was attempting to interrupt a future, "
					+ "then it should call #cancel or #setException or #setComplete instead of #interruptTask.", exception));
		}
		Future<?> setAsync = atomicSetAsync.get(this);
		if (setAsync != null) {
			setAsync.setException(exception, MAY_INTERRUPT);
		}
	}

	@Override public long getDelay(TimeUnit timeUnit) {
		if (scheduledNanos == NOT_SCHEDULED) {
			throw new UnsupportedOperationException(this + " is not a scheduled future");
		}
		return timeUnit.convert(scheduledNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
	}

	@Override public int compareTo(Delayed delayed) {
		if (delayed instanceof AbstractFuture) {
			return Long.compare(scheduledNanos, ((AbstractFuture<?>) delayed).scheduledNanos);
		} else {
			long selfRemain = scheduledNanos - System.nanoTime();
			long delayedRemain = delayed.getDelay(TimeUnit.NANOSECONDS);
			return Long.compare(selfRemain, delayedRemain);
		}
	}

	protected Class<?> sourceClass() {
		return getClass();
	}

	protected @Nullable String sourceMethodName() {
		return null;
	}

	@CallSuper protected void toStringAppendState(
			@Nullable O result, @Nullable Throwable exception, @Nullable Future<? extends O> setAsync, StringBuilder sb)
	{
		if (exception instanceof CancellationException) {
			sb.append(" cancelled=");
			toStringAppendLimitedRecursion(sb, exception);
		} else if (exception == SUCCESS_EXCEPTION) {
			sb.append(" success=");
			toStringAppendLimitedRecursion(sb, result);
		} else if (exception != null) {
			sb.append(" failure=");
			toStringAppendLimitedRecursion(sb, exception);
		} else if (setAsync != null) {
			sb.append(" setAsync=");
			setAsync.toString(sb, TO_STRING_NO_STATE);
		} else if (scheduledNanos > NOT_SCHEDULED) {
			sb.append(" scheduledNanos=").append(scheduledNanos);
		}
	}

	@NonNull @Override public String toString() {
		StringBuilder sb = new StringBuilder();
		toString(sb, TO_STRING_WITH_STATE);
		return sb.toString();
	}

	@Override protected void finalize() throws Throwable {
		if (atomicListener.get(this) != LISTENER_ALREADY_DISPATCHED) {
			Future.futureConfig.onUnhandledException(new LeakedFutureException(((atomicException.get(this) == null)
					? "Completed future \""
					: "Incomplete future \"")
					+ this
					+ "\" was leaked without having a listener set or #end() being called. This silently drops exceptions,"
					+ " which makes bugs virtually impossible to detect, diagnose, or debug.", futureConstructionLocation));
		}
		super.finalize();
	}

	protected static final class ListenerAlreadyDispatched implements FutureListener<Object> {
		@Override public void onFutureSucceeded(Future<?> future, Object result) {
			throw new UnsupportedOperationException("ListenerAlreadyDispatched should never be called");
		}

		@Override public void onFutureFailed(Future<?> future, Throwable exception, boolean mayInterruptIfRunning) {
			throw new UnsupportedOperationException("ListenerAlreadyDispatched should never be called");
		}
	}

	public static class SetResultCalledAfterSuccessException extends IllegalStateException {
		public SetResultCalledAfterSuccessException() {}

		public SetResultCalledAfterSuccessException(String message) {
			super(message);
		}

		public SetResultCalledAfterSuccessException(Throwable throwable) {
			super(throwable);
		}

		public SetResultCalledAfterSuccessException(String message, @Nullable Throwable throwable) {
			super(message, throwable);
		}
	}

	public static class SetResultCalledTwiceException extends IllegalStateException {
		public SetResultCalledTwiceException() {}

		public SetResultCalledTwiceException(String message) {
			super(message);
		}

		public SetResultCalledTwiceException(Throwable throwable) {
			super(throwable);
		}

		public SetResultCalledTwiceException(String message, @Nullable Throwable throwable) {
			super(message, throwable);
		}
	}

	public static class SetResultCalledAfterFailureException extends IllegalStateException {
		public SetResultCalledAfterFailureException() {}

		public SetResultCalledAfterFailureException(String message) {
			super(message);
		}

		public SetResultCalledAfterFailureException(Throwable throwable) {
			super(throwable);
		}

		public SetResultCalledAfterFailureException(String message, @Nullable Throwable throwable) {
			super(message, throwable);
		}
	}

	public static class SetExceptionCalledAfterCompleteException extends IllegalStateException {
		public SetExceptionCalledAfterCompleteException() {}

		public SetExceptionCalledAfterCompleteException(String message) {
			super(message);
		}

		public SetExceptionCalledAfterCompleteException(Throwable throwable) {
			super(throwable);
		}

		public SetExceptionCalledAfterCompleteException(String message, @Nullable Throwable throwable) {
			super(message, throwable);
		}
	}

	public static class LeakedFutureException extends IllegalStateException {
		public LeakedFutureException() {}

		public LeakedFutureException(String message) {
			super(message);
		}

		public LeakedFutureException(Throwable throwable) {
			super(throwable);
		}

		public LeakedFutureException(String message, @Nullable Throwable throwable) {
			super(message, throwable);
		}
	}

	private static class SuccessException extends RuntimeException {
		@Override public String getMessage() {
			return "SuccessException";
		}

		@NonNull @Override public String toString() {
			return "SuccessException";
		}
	}
}
