package com.mpd.concurrent.futures.locked;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.FutureListener;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public abstract class AbstractFuture<O> implements Future<O> {
	@Nullable protected static final Throwable NO_EXCEPTION = null;
	@SuppressWarnings("ConstantConditions") protected final @Nullable O FAILED_RESULT = null;

	private final long delay;
	private final TimeUnit delayUnit;
	private @Nullable FutureListener<? super O> listener = null;
	private boolean isDone = false;
	private @Nullable Future<? extends O> setAsync;
	private @MonotonicNonNull O result = null;
	private @MonotonicNonNull Throwable exception = null;
	private @MonotonicNonNull RuntimeException wrappedException = null;
	private boolean wasInterrupted = false;

	protected AbstractFuture() {
		delay = -1;
		delayUnit = TimeUnit.MILLISECONDS;
	}

	protected AbstractFuture(long delay, TimeUnit delayUnit) {
		this.delay = delay;
		this.delayUnit = delayUnit;
	}

	protected AbstractFuture(@Nullable O result) {
		this.result = result;
		this.isDone = true;
		delay = -1;
		delayUnit = TimeUnit.MILLISECONDS;
	}

	protected AbstractFuture(Throwable exception) {
		this.exception = exception;
		this.wrappedException =
				(exception instanceof RuntimeException)
						? ((RuntimeException) exception)
						: (new AsyncCheckedException(exception));
		this.isDone = true;
		delay = -1;
		delayUnit = TimeUnit.MILLISECONDS;
	}

	protected static void appendMaxRecursion(StringBuilder sb, @Nullable Object object) {
		if (object == null) {
			sb.append(object);
		} else if (sb.length() > 256) {
			sb.append(object.getClass());
		} else {
			sb.append(object);
		}
	}

	protected FutureListener<? super O> getListenerLocked() {
		return listener;
	}

	protected boolean setComplete(
			@Nullable O result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
	{
		try {
			FutureListener<? super O> listener;
			synchronized (this) {
				boolean alreadyDone = (exception != null || result == null);
				if (alreadyDone && !mayInterruptIfRunning) {
					return false;
				}
				if (!onCompletingLocked(result, exception, mayInterruptIfRunning)) {
					return false;
				}
				listener = this.listener;
				if (alreadyDone) {
					return false;
				}
			}
			afterDone(result, exception, mayInterruptIfRunning, listener);
		} catch (RuntimeException e) {
			Future.futureConfig.onUnhandledException(e);
		}
		return true;
	}

	// derived classes may clear no longer needed variables here to release memory
	protected void onCompletedLocked(@Nullable Throwable exception) {}

	// This is a dangerous method. onCompletingLocked should be called within a lock, and if it returns true, then
	// after the lock is exited, the caller must call onCompleteUnlocked.
	protected boolean onCompletingLocked(@Nullable O result, @Nullable Throwable exception, boolean mayInterruptIfRunning)
	{
		this.wasInterrupted = mayInterruptIfRunning;
		if (isDone) {
			if (exception != null) {  // second exception goes to onUnhandledException
				if (!mayInterruptIfRunning) {
					Future.futureConfig.onUnhandledException(exception);
				}
			} else if (this.exception == null) { // two successes is FutureSucceededTwiceException
				Future.futureConfig.onUnhandledException(new FutureSucceededTwiceException());
			}
			return mayInterruptIfRunning;
		}
		this.result = result;
		this.exception = exception;
		this.wrappedException =
				(exception instanceof RuntimeException)
						? ((RuntimeException) exception)
						: (new AsyncCheckedException(exception));
		isDone = true;
		this.notifyAll();
		onCompletedLocked(exception);
		return true;
	}

	// This is a dangerous method. onCompletingLocked should be called within a lock, and if it returns true, then
	// after the lock is exited, the caller must call onCompleteUnlocked.
	protected void afterDone(
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

	protected boolean wasInterrupted() {
		return wasInterrupted;
	}

	protected void interruptTask(Throwable exception) {}

	protected void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {}

	protected boolean setResult(O result) {
		return setComplete(result, NO_EXCEPTION, NO_INTERRUPT);
	}

	protected void setResult(Future<? extends O> result) {
		try {
			synchronized (this) {
				setAsync = result;
			}
			result.setListener(new FutureListener<O>() {
				@Override public void onFutureSucceeded(Future<? extends O> future, O result) {
					setResult(result);
				}

				@Override
				public void onFutureFailed(Future<? extends O> future, Throwable exception, boolean mayInterruptIfRunning) {
					setException(exception, mayInterruptIfRunning);
				}
			});
		} catch (Throwable e) {
			setException(e);
		}
	}

	@Override public synchronized boolean isDone() {
		return isDone;
	}

	@Override public O get(long timeout, TimeUnit unit) {
		long until = System.nanoTime() + unit.toNanos(timeout);
		synchronized (this) {
			try {
				while (true) {
					if (exception != null) {
						throw wrappedException;
					} else if (isDone) {
						return result;
					} else {
						long remaining = until - System.nanoTime();
						this.wait(remaining / TimeUnit.MILLISECONDS.toNanos(1),
								(int) (remaining % TimeUnit.MILLISECONDS.toNanos(1)));
					}
				}
			} catch (InterruptedException e) {
				if (!isDone) {
					// ignored result because we check isDone before calling
					setComplete(FAILED_RESULT, e, MAY_INTERRUPT);
				}
			}
			if (exception != null) {
				throw wrappedException;
			} else {
				return result;
			}
		}
	}

	@Override public synchronized O resultNow() {
		return getDoneLocked();
	}

	@SuppressWarnings("UnusedReturnValue") @Override
	public boolean cancel(CancellationException exception, boolean mayInterruptIfRunning) {
		return setException(exception, mayInterruptIfRunning);
	}

	@SuppressWarnings("UnusedReturnValue") public boolean setException(Throwable exception) {
		return setComplete(FAILED_RESULT, exception, NO_INTERRUPT);
	}

	@SuppressWarnings("UnusedReturnValue")
	public boolean setException(Throwable exception, boolean mayInterruptIfRunning) {
		return setComplete(FAILED_RESULT, exception, mayInterruptIfRunning);
	}

	@Override public synchronized @Nullable Throwable exceptionNow() {
		return getExceptionLocked();
	}

	@Override public void setListener(FutureListener<? super O> listener) {
		boolean wasDone;
		synchronized (this) {
			if (this.listener == listener) {
				return;
			}
			if (this.listener != null) {
				throw new SetListenerCalledTwiceException();
			}
			this.listener = listener;
			wasDone = isDone;
		}
		if (wasDone) {
			if (exception == null) {
				listener.onFutureSucceeded(this, result);
			} else {
				listener.onFutureFailed(this, exception, wasInterrupted);
			}
		}
	}

	@Override public Future<O> withTimeout(
			long timeout, TimeUnit unit, @Nullable Throwable exceptionOnTimeout, boolean interruptOnTimeout)
	{
		return new FutureTimeout<>(this, timeout, unit, exceptionOnTimeout, interruptOnTimeout);
	}

	@Override public long getScheduledTimeNanos() {
		throw new UnsupportedOperationException();
	}

	@Override @CallSuper public void addPendingString(StringBuilder sb, int maxDepth) {
		sb.append("\n  at ");
		Object source = toStringSource();
		if (source != null) {
			sb.append(source.getClass().getCanonicalName())
					.append(".apply(")
					.append(source.getClass().getSimpleName())
					.append(":0)");
		} else {
			sb.append(getClass().getCanonicalName()).append(".run(Unknown Source)");
		}
		sb.append(" //");
		toString(sb, TO_STRING_NO_STATE);
	}

	protected O getDoneLocked() {
		if (!isDone) {
			throw new FutureNotCompleteException();
		} else if (exception != null) {
			throw wrappedException;
		} else {
			return result;
		}
	}

	protected synchronized @Nullable RuntimeException getWrappedException() {
		if (!isDone) {
			throw new FutureNotCompleteException();
		} else {
			return wrappedException;
		}
	}

	protected boolean isDoneLocked() {
		return isDone;
	}

	protected @Nullable Throwable getExceptionLocked() {
		if (!isDone) {
			throw new FutureNotCompleteException();
		} else {
			return exception;
		}
	}

	@Override public synchronized long getDelay(TimeUnit timeUnit) {
		if (delay < 0) {
			throw new UnsupportedOperationException("not a scheduled future");
		}
		return delayUnit.convert(delay, timeUnit);
	}

	@Override public void toString(StringBuilder sb, boolean includeState) {
		Future<? extends O> setAsync;
		boolean isDone;
		Throwable exception;
		O result;
		synchronized (this) {
			isDone = this.isDone;
			exception = this.exception;
			result = this.result;
			setAsync = this.setAsync;
		}
		sb.append(getClass().getSimpleName());
		Object source = toStringSource();
		if (source != null) {
			sb.append('<').append(source).append('>');
		} else {
			sb.append('@').append(System.identityHashCode(this));
		}
		if (includeState) {
			sb.append('[');
			toStringAppendState(isDone, result, exception, setAsync, sb);
			sb.append(']');
		}
	}

	@Override public synchronized int compareTo(Delayed delayed) {
		return Long.compare(delayUnit.toNanos(delay), delayed.getDelay(TimeUnit.NANOSECONDS));
	}

	protected @Nullable Object toStringSource() {
		return null;
	}

	@CallSuper protected void toStringAppendState(
			boolean isDone,
			@Nullable O result,
			@Nullable Throwable exception,
			@Nullable Future<? extends O> setAsync,
			StringBuilder sb)
	{
		if (isDone && exception instanceof CancellationException) {
			sb.append("cancelled=");
			appendMaxRecursion(sb, exception);
		} else if (isDone && exception != null) {
			sb.append("failure=");
			appendMaxRecursion(sb, exception);
		} else if (isDone && result != null) {
			sb.append("success=");
			appendMaxRecursion(sb, result);
		} else if (isDone) {
			sb.append("succeeded=null");
		} else if (setAsync != null) {
			sb.append("setAsync=").append(setAsync.getClass().getSimpleName());
			boolean hasSource = false;
			if (setAsync instanceof AbstractFuture) {
				Object source = ((AbstractFuture<?>) setAsync).toStringSource();
				if (source != null) {
					sb.append('<').append(source).append('>');
					hasSource = true;
				}
			}
			if (!hasSource) {
				sb.append('@').append(System.identityHashCode(setAsync));
			}
		}
	}

	@NonNull @Override public String toString() {
		StringBuilder sb = new StringBuilder();
		toString(sb, TO_STRING_WITH_STATE);
		return sb.toString();
	}
}
