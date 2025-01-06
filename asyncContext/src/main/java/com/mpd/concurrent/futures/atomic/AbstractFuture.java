package com.mpd.concurrent.futures.atomic;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

// FutureListener<Object>, because derived classes listen to multiple other futures of various types in addition
public abstract class AbstractFuture<O> implements Future<O>, FutureListener<Object> {
	protected static final RuntimeException SUCCESS_EXCEPTION = new RuntimeException("Future succeeded");
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
	/**
	 * @noinspection unchecked
	 */
	private static final AtomicReferenceFieldUpdater<AbstractFuture<?>, Object>
			atomicResult =
			AtomicReferenceFieldUpdater.newUpdater((Class<AbstractFuture<?>>) (Class<?>) AbstractFuture.class,
					Object.class,
					"result");
	/**
	 * @noinspection unchecked
	 */
	private static final AtomicReferenceFieldUpdater<AbstractFuture<?>, RuntimeException>
			atomicWrapped =
			AtomicReferenceFieldUpdater.newUpdater((Class<AbstractFuture<?>>) (Class<?>) AbstractFuture.class,
					RuntimeException.class,
					"wrappedException");
	/**
	 * @noinspection unchecked
	 */
	private static final AtomicReferenceFieldUpdater<AbstractFuture<?>, Throwable>
			atomicInterrupted =
			AtomicReferenceFieldUpdater.newUpdater((Class<AbstractFuture<?>>) (Class<?>) AbstractFuture.class,
					Throwable.class,
					"wasInterrupted");
	@SuppressWarnings("ConstantConditions") protected final @Nullable O FAILED_RESULT = null; //pseudo-static
	private final long scheduledNanos;
	private volatile @Nullable FutureListener<? super O> listener = null; // TODO: atomicListener
	private volatile @Nullable Future<? extends O> setAsync = null; // TODO: atomicSetAsync
	private volatile @MonotonicNonNull Throwable exception = null; // TODO: atomicExeption
	private volatile @MonotonicNonNull O result = null; // TODO: atomicResult
	private volatile @MonotonicNonNull RuntimeException wrappedException = null; // TODO: atomicWrapped
	private volatile @MonotonicNonNull Throwable wasInterrupted = null; // TODO: atomicInterrupted

	protected AbstractFuture() {
		scheduledNanos = -1;
	}

	protected AbstractFuture(long delay, TimeUnit delayUnit) {
		scheduledNanos = delayUnit.toNanos(delay);
	}

	protected AbstractFuture(@Nullable O result) {
		this.exception = SUCCESS_EXCEPTION;
		this.result = result;
		scheduledNanos = -1;
	}

	protected AbstractFuture(Throwable exception) {
		this.exception = exception;
		this.wrappedException =
				(exception instanceof RuntimeException)
						? ((RuntimeException) exception)
						: (new AsyncCheckedException(exception));
		scheduledNanos = -1;
	}

	protected static void toStringAppendLimitedRecursion(StringBuilder sb, @Nullable Object object) {
		if (object == null) {
			sb.append((String) null);
		} else if (sb.length() > 256) {
			sb.append(object.getClass());
		} else {
			String text = object.toString();
			if (sb.length() + text.length() > 256) {
				sb.append(object.getClass());
			} else {
				sb.append(object);
			}
		}
	}

	@CallSuper protected void onCompletingLocked(
			@Nullable O result, Throwable exception, boolean mayInterruptIfRunning)
	{
		atomicResult.set(this, result);
		atomicInterrupted.set(this, mayInterruptIfRunning ? exception : null);
		atomicWrapped.set(this,
				(exception instanceof RuntimeException)
						? ((RuntimeException) exception)
						: (new AsyncCheckedException(exception)));
		atomicSetAsync.lazySet(this, null);
	}

	@CallSuper protected boolean setComplete(
			@Nullable O result, Throwable exception, boolean mayInterruptIfRunning)
	{
		try {
			Throwable oldException;
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
				afterDone(result, exception, mayInterruptIfRunning, listener);
				return true;
			}
			// this future was already completed:
			if (exception == SUCCESS_EXCEPTION) {
				if (oldException == SUCCESS_EXCEPTION) { // if already succeeded, throw FutureSucceededTwiceException
					Future.futureConfig.onUnhandledException(new FutureSucceededTwiceException(exception));
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
			@Nullable O result,
			@Nullable Throwable exception,
			boolean mayInterruptIfRunning,
			FutureListener<? super O> listener)
	{
		if (mayInterruptIfRunning) {
			interruptTask(exception);
		}
		if (exception instanceof CancellationException) {
			onCancelled(((CancellationException) exception), mayInterruptIfRunning);
		}
		if (listener != null) {
			if (exception == null) {
				listener.onFutureSucceeded(this, result);
			} else {
				listener.onFutureFailed(this, exception, mayInterruptIfRunning);
			}
		}
	}

	@CallSuper protected boolean setResult(O result) {
		return setComplete(result, SUCCESS_EXCEPTION, NO_INTERRUPT);
	}

	@CallSuper protected boolean setResult(Future<? extends O> asyncWork) {
		try {
			Throwable oldException;
			boolean didSetAsync;
			synchronized (this) {
				oldException = atomicException.get(this);
				didSetAsync = (oldException == null) && atomicSetAsync.compareAndSet(this, null, asyncWork);
			}
			if (oldException == SUCCESS_EXCEPTION) {
				setException(new SetResultCalledAfterSuccessException(asyncWork.isDone() ? asyncWork.exceptionNow() : null));
			} else if (oldException == null && !didSetAsync) {
				setException(new SetResultCalledATwiceException(asyncWork.isDone() ? asyncWork.exceptionNow() : null));
			} else if (oldException == null) {
				try {
					asyncWork.setListener(this);
				} catch (SetListenerCalledTwiceException e) {
					setException(e);
					didSetAsync = false;
				}
			} // else this already failed, but that's not an exceptional case
			if (!didSetAsync) {
				asyncWork.end();
			}
			return didSetAsync;
		} catch (RuntimeException e) {
			setException(e);
			return false;
		}
	}

	protected AbstractFuture<? extends O> getSetAsync() {
		//noinspection unchecked
		return (AbstractFuture<? extends O>) atomicSetAsync.get(this);
	}

	@Override public boolean isDone() {
		return atomicException.get(this) != null;
	}

	@Override public O get(long timeout, TimeUnit unit) {
		try {
			long until = System.nanoTime() + unit.toNanos(timeout);
			synchronized (this) {
				while (true) {
					RuntimeException exception = atomicWrapped.get(this);
					if (exception == SUCCESS_EXCEPTION) {
						//noinspection unchecked
						return (O) atomicResult.get(this);
					} else if (exception != null) {
						throw exception;
					}
					long remaining = until - System.nanoTime();
					this.wait(remaining / TimeUnit.MILLISECONDS.toNanos(1), (int) (remaining % TimeUnit.MILLISECONDS.toNanos(1)));
				}
			}
		} catch (InterruptedException e) {
			throw new AsyncCheckedException(e);
		}
	}

	@Override public O resultNow() {
		RuntimeException exception = atomicWrapped.get(this);
		if (exception == null) {
			throw new FutureNotCompleteException();
		} else if (exception == SUCCESS_EXCEPTION) {
			//noinspection unchecked
			return (O) atomicResult.get(this);
		} else {
			throw exception;
		}
	}

	@Override @CallSuper @SuppressWarnings("UnusedReturnValue") public boolean setException(Throwable exception) {
		return setComplete(FAILED_RESULT, exception, NO_INTERRUPT);
	}

	@Override @CallSuper @SuppressWarnings("UnusedReturnValue")
	public boolean setException(Throwable exception, boolean mayInterruptIfRunning) {
		return setComplete(FAILED_RESULT, exception, mayInterruptIfRunning);
	}

	@Override public @Nullable Throwable exceptionNow() {
		Throwable exception = atomicException.get(this);
		if (exception == null) {
			throw new FutureNotCompleteException();
		} else if (exception == SUCCESS_EXCEPTION) {
			return null;
		} else {
			return exception;
		}
	}

	@CallSuper @SuppressWarnings("UnusedReturnValue") @Override
	public boolean cancel(CancellationException exception, boolean mayInterruptIfRunning) {
		return setComplete(FAILED_RESULT, exception, mayInterruptIfRunning);
	}

	@Override public void onFutureSucceeded(Future<?> future, Object result) {
		//noinspection unchecked
		setResult((O) result);
	}

	@Override public void onFutureFailed(Future<?> future, Throwable exception, boolean mayInterruptIfRunning) {
		if (exception instanceof CancellationException) {
			cancel((CancellationException) exception, mayInterruptIfRunning);
		} else {
			setException(exception);
		}
	}

	protected @Nullable Throwable getExceptionProtected() {
		return atomicException.get(this);
	}

	protected @Nullable RuntimeException getWrappedExceptionProtected() {
		return atomicWrapped.get(this);
	}

	protected @Nullable Throwable getInterrupt() {
		return atomicInterrupted.get(this);
	}

	@CallSuper protected void interruptTask(Throwable exception) {
		atomicInterrupted.set(this, exception);
	}

	@CallSuper protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
		Future<? extends O> setAsync = this.setAsync;
		if (setAsync != null) {
			setAsync.cancel(exception, mayInterruptIfRunning);
		}
	}

	protected @Nullable FutureListener<? super O> getListener() {
		return listener;
	}

	@CallSuper @Override public void setListener(FutureListener<? super O> listener) {
		Throwable exception;
		synchronized (this) {
			if (!atomicListener.compareAndSet(this, null, listener)) {
				if (atomicListener.get(this) != listener) {
					throw new SetListenerCalledTwiceException();
				}
				return;
			}
			exception = atomicException.get(this);
		}
		if (exception != null) { // if this was already complete, then notify the listener immediately
			if (exception == SUCCESS_EXCEPTION) {
				//noinspection unchecked
				listener.onFutureSucceeded(this, (O) atomicResult.get(this));
			} else {
				listener.onFutureFailed(this, exception, atomicInterrupted.get(this) != null);
			}
		}
	}

	@Override public long getScheduledTimeNanos() {
		return scheduledNanos;
	}

	@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
		sb.append("\n  at ");
		Class<?> sourceClass = sourceClass();
		String method = sourceMethodName();
		sb.append(sourceClass.getCanonicalName());
		if (method != null) {
			sb.append('.').append(method);
		}
		sb.append('(').append(sourceClass.getSimpleName()).append(":0)");
		sb.append(" //");
		toString(sb, TO_STRING_WITH_STATE);
		Future<? extends O> setAsync = this.setAsync;
		if (setAsync != null) {
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
			sb.append('<').append(sourceClass.getCanonicalName()).append('>');
		} else {
			sb.append('@').append(System.identityHashCode(this));
		}
		if (includeState) {
			sb.append('[');
			toStringAppendState(result, exception, setAsync, sb);
			sb.append(']');
		}
	}

	@Override public long getDelay(TimeUnit timeUnit) {
		if (scheduledNanos < 0) {
			throw new UnsupportedOperationException("not a scheduled future");
		}
		return timeUnit.convert(System.currentTimeMillis() - scheduledNanos, TimeUnit.NANOSECONDS);
	}

	@Override public int compareTo(Delayed delayed) {
		if (delayed instanceof AbstractFuture) {
			return Long.compare(scheduledNanos, ((AbstractFuture<?>) delayed).scheduledNanos);
		} else {
			long selfRemain = scheduledNanos - System.currentTimeMillis();
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
		} else if (scheduledNanos > 0) {
			sb.append(" scheduledNanos=").append(scheduledNanos);
		}
	}

	@NonNull @Override public String toString() {
		StringBuilder sb = new StringBuilder();
		toString(sb, TO_STRING_WITH_STATE);
		return sb.toString();
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

	public static class SetResultCalledATwiceException extends IllegalStateException {
		public SetResultCalledATwiceException() {}

		public SetResultCalledATwiceException(String message) {
			super(message);
		}

		public SetResultCalledATwiceException(Throwable throwable) {
			super(throwable);
		}

		public SetResultCalledATwiceException(String message, @Nullable Throwable throwable) {
			super(message, throwable);
		}
	}
}
