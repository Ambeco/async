package com.mpd.concurrent.futures.atomic;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.StackSize;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// FutureListener<Object>, because derived classes listen to multiple other futures of various types in addition
public abstract class AbstractFuture<O> implements Future<O>, FutureListener<Object> {
	protected static final long NOT_SCHEDULED = Long.MIN_VALUE;

	protected static final RuntimeException SUCCESS_EXCEPTION = new SuccessException();
	private static final FluentLogger log = FluentLogger.forEnclosingClass();
	private static final int NANOS_PER_MILLI = 1000000;
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
	private volatile @Nullable FutureListener<? super O> listener = null; // TODO: atomicListener
	private volatile @Nullable Future<? extends O> setAsync = null; // TODO: atomicSetAsync
	private volatile @MonotonicNonNull Throwable exception = null; // TODO: atomicExeption
	private volatile @MonotonicNonNull O result = null;
	private volatile @MonotonicNonNull RuntimeException wrappedException = null;
	private volatile @MonotonicNonNull Throwable wasInterrupted = null;
	private volatile boolean exceptionPropagated = false;

	protected AbstractFuture() {
		scheduledNanos = NOT_SCHEDULED;
	}

	protected AbstractFuture(long delay, TimeUnit delayUnit) {
		scheduledNanos = System.nanoTime() + delayUnit.toNanos(delay);
	}

	protected AbstractFuture(@Nullable O result) {
		this.exception = SUCCESS_EXCEPTION;
		this.wrappedException = SUCCESS_EXCEPTION;
		this.result = result;
		scheduledNanos = NOT_SCHEDULED;
	}

	protected AbstractFuture(Throwable exception) {
		this.exception = exception;
		this.wrappedException =
				(exception instanceof RuntimeException)
						? ((RuntimeException) exception)
						: (new AsyncCheckedException(exception));
		scheduledNanos = NOT_SCHEDULED;
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
			} else if (!(exception instanceof CancellationException)) { // if already failed, new exception gets handled
				Future.futureConfig.onUnhandledException(exception);
			} // CancellationException can be silently dropped
			return false;

		} catch (RuntimeException e) { // if anything goes wrong: it's an unhandled exception
			Future.futureConfig.onUnhandledException(e);
			return false;
		}
	}

	@CallSuper protected void afterDone(
			@Nullable O result, Throwable exception, boolean mayInterruptIfRunning, FutureListener<? super O> listener)
	{
		if (mayInterruptIfRunning) {
			interruptTask(exception);
		}
		if (exception instanceof CancellationException) {
			onCancelled(((CancellationException) exception), mayInterruptIfRunning);
		}
		if (listener != null) {
			exceptionPropagated = true;
			if (exception == SUCCESS_EXCEPTION) {
				listener.onFutureSucceeded(this, result);
			} else {
				listener.onFutureFailed(this, exception, mayInterruptIfRunning);
			}
		}
		atomicListener.lazySet(this, null);
		atomicSetAsync.lazySet(this, null);
	}

	@CallSuper protected boolean setResult(O result) {
		return setComplete(result, SUCCESS_EXCEPTION, NO_INTERRUPT);
	}

	@CallSuper protected boolean setResult(Future<? extends O> asyncWork) {
		boolean didSetListener = false;
		try {
			Throwable oldException = atomicException.get(this);
			boolean didSetAsync = (oldException == null) && atomicSetAsync.compareAndSet(this, null, asyncWork);
			Future<?> oldListener = atomicSetAsync.get(this);
			if (oldException == null && !didSetAsync && oldListener == asyncWork) {
				// same result set twice. weird, but fine.
			} else if (oldException == SUCCESS_EXCEPTION) {
				setException(new SetResultCalledAfterSuccessException("setResult tried to set the result of future \""
						+ this
						+ "\" with the result of future \""
						+ asyncWork
						+ "\" but this has already succeeded with \""
						+ this.result
						+ "\"", asyncWork.isDone() ? asyncWork.exceptionNow() : null));
			} else if (oldException == null && !didSetAsync) {
				setException(new SetResultCalledTwiceException("setResult tried to set the result of future \""
						+ this
						+ " with the result of future \""
						+ asyncWork
						+ "\" (@"
						+ System.identityHashCode(asyncWork)
						+ ") but this already has listener \""
						+ oldListener
						+ "\" (@"
						+ System.identityHashCode(oldListener)
						+ ")", asyncWork.isDone() ? asyncWork.exceptionNow() : null));
			} else if (didSetAsync) {
				try {
					asyncWork.setListener(this);
					didSetListener = true;
				} catch (SetListenerCalledTwiceException e) {
					setException(e);
					didSetAsync = false;
				}
			} // else this had already failed, but that's not an exceptional case
			return didSetAsync;
		} catch (RuntimeException e) {
			setException(e);
			return false;
		} finally {
			if (!didSetListener) {
				asyncWork.end();
			}
		}
	}

	protected Future<? extends O> getSetAsync() {
		//noinspection unchecked
		return (Future<? extends O>) atomicSetAsync.get(this);
	}

	@Override public boolean isDone() {
		return atomicException.get(this) != null;
	}

	@Override public O resultNow() {
		RuntimeException exception = wrappedException;
		if (exception == null) {
			throw new FutureNotCompleteException("resultNow called on incomplete future" + this);
		} else if (exception == SUCCESS_EXCEPTION) {
			return result;
		} else {
			log.atFinest().withStackTrace(StackSize.SMALL).log("Future resultNow throwing %s", exception);
			throw exception;
		}
	}

	@Override public @Nullable Throwable exceptionNow() {
		Throwable exception = atomicException.get(this);
		if (exception == null) {
			throw new FutureNotCompleteException("exceptionNow called on incomplete future" + this);
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
						log.atFinest().withStackTrace(StackSize.SMALL).log("Future get throwing %s", exception);
						throw exception;
					}
					long remainingNs = untilNs - System.nanoTime();
					if (remainingNs > 0) {
						log.atFine().log(
								"Thread %s blocking for up to %dns, out of a maximum of %dns, waiting for future %s to complete",
								Thread.currentThread(),
								remainingNs,
								timeoutNs,
								this);
						this.wait(remainingNs / NANOS_PER_MILLI, (int) (remainingNs % NANOS_PER_MILLI));
					} else {
						log.atFine().log(
								"Thread %s timed out after %dns out of a maximum of %dns, waiting for future %s to complete",
								Thread.currentThread(),
								System.nanoTime() - startTimeNanos,
								timeoutNs,
								this);
						throw new TimeoutException();
					}
				}
			}
		} catch (InterruptedException e) {
			log.atFine().log(
					"Thread %s interrupted after %dns out of a maximum of %dns, while waiting for future %s to complete",
					Thread.currentThread(),
					System.nanoTime() - startTimeNanos,
					timeoutNs,
					this);
			Thread.currentThread().interrupt();
			throw new AsyncCheckedException(e);
		}
	}

	@CallSuper @SuppressWarnings("UnusedReturnValue") @Override
	public boolean cancel(CancellationException exception, boolean mayInterruptIfRunning) {
		return setComplete(FAILED_RESULT, exception, mayInterruptIfRunning);
	}

	/**
	 * @noinspection BooleanMethodIsAlwaysInverted
	 */
	@CallSuper protected boolean onParentComplete(
			Future<?> future, @Nullable Object result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
	{
		Throwable oldThrowable = getExceptionProtected();
		Future<?> setAsync = getSetAsync();
		if (oldThrowable == SUCCESS_EXCEPTION) { // STATE_SUCCESS
			setException(new AbstractListenerFuture.SucceededBeforeParentException("ListenerFuture \""
					+ this
					+ "\" already succeeded with \""
					+ getResultProtected()
					+ "\" before Future \""
					+ future
					+ "\" completed"));
			return true;
		} else if (oldThrowable != null) { // STATE_FAILED
			return true;
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

	@CallSuper @Override public void onFutureSucceeded(Future<?> future, Object result) {
		try {
			if (!onParentComplete(future, result, SUCCESS_EXCEPTION, NO_INTERRUPT)) {
				setComplete(FAILED_RESULT, new WrongParentFutureException(), NO_INTERRUPT);
			}
		} catch (RuntimeException e) {
			setException(e);
		}
	}

	@CallSuper @Override
	public void onFutureFailed(Future<?> future, Throwable exception, boolean mayInterruptIfRunning) {
		try {
			if (!onParentComplete(future, null, exception, mayInterruptIfRunning)) {
				setComplete(FAILED_RESULT, new WrongParentFutureException(), NO_INTERRUPT);
			}
		} catch (RuntimeException e) {
			e.addSuppressed(exception);
			setException(e);
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

	@CallSuper protected void interruptTask(Throwable exception) {
		Throwable realException = atomicException.get(this);
		if (exception != realException) {
			setException(new FutureNotCompleteException("interruptTask called with \""
					+ exception
					+ "\", but the future was "
					+ "actually interrupted with \""
					+ realException
					+ "\".\nIf caller was attempting to interrupt a future, "
					+ "then it should call #cancel or #setException or #setComplete instead of #interruptTask."));
		}
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

	@CallSuper @Override public void setListener(FutureListener<? super O> listener) {
		if (exceptionPropagated || !atomicListener.compareAndSet(this, null, listener)) {
			FutureListener<?> oldListener = atomicListener.get(this);
			if (oldListener == listener) {
				return; // called same listener twice. Weird, but safe.
			} else if (listener instanceof EndListener) {
				return; // #end() allowed to be called extra times, just to be safe.
			}
			throw new SetListenerCalledTwiceException("setListener called with \""
					+ listener
					+ "\" but future \""
					+ this
					+ "\" already had listener \""
					+ oldListener);
		}
		Throwable exception = atomicException.get(this);
		if (exception != null) { // if this was already complete, then notify the listener immediately
			exceptionPropagated = true;
			if (exception == SUCCESS_EXCEPTION) {
				listener.onFutureSucceeded(this, result);
			} else {
				listener.onFutureFailed(this, exception, wasInterrupted != null);
			}
			atomicListener.lazySet(this, null);
		}
	}

	@Override public long getScheduledTimeNanos() {
		if (scheduledNanos == NOT_SCHEDULED) {
			throw new UnsupportedOperationException("not a scheduled future");
		}
		return scheduledNanos;
	}

	protected long getScheduledTimeNanosProtected() {
		return scheduledNanos;
	}

	@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
		sb.append("\n  at ");
		Class<?> sourceClass = sourceClass();
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
		if (sourceClass != getClass()) {
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

	@Override public long getDelay(TimeUnit timeUnit) {
		if (scheduledNanos == NOT_SCHEDULED) {
			throw new UnsupportedOperationException("not a scheduled future");
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
		if (!exceptionPropagated) {
			Future.futureConfig.onUnhandledException(new LeakedFutureException("future \""
					+ this
					+ "\" was leaked without having a listener set or #end() being called. This silently drops exceptions,"
					+ " which makes bugs virtually impossible to detect, diagnose, or debug."));
		}
		super.finalize();
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
			throw new IllegalStateException("SUCCESS_EXCEPTION should never be accessed");
		}

		@NonNull @Override public String toString() {
			throw new IllegalStateException("SUCCESS_EXCEPTION should never be accessed");
		}
	}
}
