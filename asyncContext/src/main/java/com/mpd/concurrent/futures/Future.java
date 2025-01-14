package com.mpd.concurrent.futures;

import androidx.annotation.NonNull;
import com.mpd.concurrent.AsyncFunction;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.FutureListener.RunnableListener;
import com.mpd.concurrent.futures.atomic.EndListener;
import com.mpd.concurrent.futures.atomic.FutureAsyncFunction;
import com.mpd.concurrent.futures.atomic.FutureCatchingAsyncFunction;
import com.mpd.concurrent.futures.atomic.FutureCatchingFunction;
import com.mpd.concurrent.futures.atomic.FutureFunction;
import com.mpd.concurrent.futures.atomic.FutureTimeout;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// Based on Guava FluentFuture, except that each future can only have one listener,
// and cancellationException results in a CancellationException
public interface Future<O> extends java.util.concurrent.ScheduledFuture<O> {
	FutureConfig futureConfig = new FutureConfig.DefaultFutureConfig();

	boolean MAY_INTERRUPT = true;
	boolean NO_INTERRUPT = false;
	boolean TO_STRING_WITH_STATE = true;
	boolean TO_STRING_NO_STATE = false;

	boolean isDone();

	default boolean isSuccessful() {
		return isDone() && exceptionNow() == null;
	}

	@SuppressWarnings("UnusedReturnValue") default boolean setException(Throwable exception) {
		return setException(exception, NO_INTERRUPT);
	}

	boolean setException(Throwable exception, boolean mayInterruptIfRunning);

	O resultNow(); //or throws FutureNotCompleteException, or the completed RuntimeException, or AsyncCheckedException

	@SuppressWarnings("UnusedReturnValue") default boolean cancel(boolean mayInterruptIfRunning) {
		return cancel(new CancellationException(), mayInterruptIfRunning);
	}

	default boolean isCancelled() {
		return isDone() && exceptionNow() instanceof CancellationException;
	}

	@MonotonicNonNull Throwable exceptionNow(); //or throws FutureNotCompleteException

	/**
	 * @noinspection DeprecatedIsStillUsed
	 */
	@Deprecated default O get() {
		//noinspection deprecation
		return get(Integer.MAX_VALUE, TimeUnit.NANOSECONDS);
	}

	/**
	 * @noinspection DeprecatedIsStillUsed
	 */
	@Deprecated O get(long timeout, TimeUnit unit);

	@SuppressWarnings("UnusedReturnValue") boolean cancel(CancellationException exception, boolean mayInterruptIfRunning);

	void setListener(FutureListener<? super O> task);

	default void setListener(Runnable task, Executor executor) {
		if (task instanceof FutureListener) {
			//noinspection unchecked
			setListener((FutureListener<O>) task);
		} else {
			setListener(new RunnableListener<>(task, executor));
		}
	}

	long getScheduledTimeNanos();

	default <U, FU extends U> Future<U> transform(Function<? super O, FU> function) {
		return transform(function, futureConfig.getDefaultExecutor());
	}

	default <U> Future<U> transform(Function<? super O, ? extends U> function, Executor executor) {
		return new FutureFunction<>(this, function, executor);
	}

	default <U> Future<U> transformAsync(AsyncFunction<? super O, U> function, Executor executor) {
		return new FutureAsyncFunction<>(this, function, executor);
	}

	default <E extends Throwable, FV extends O> Future<O> catching(
			Class<E> exception, Function<? super E, FV> fallback)
	{
		return catching(exception, fallback, futureConfig.getDefaultExecutor());
	}

	default <E extends Throwable, FV extends O> Future<O> catching(
			Class<E> exceptionClass, Function<? super E, FV> function, Executor executor)
	{
		return new FutureCatchingFunction<>(this, function, exceptionClass, executor);
	}

	default <E extends Throwable> Future<O> catchingAsync(
			Class<E> exceptionClass, AsyncFunction<? super E, O> function, Executor executor)
	{
		return new FutureCatchingAsyncFunction<>(exceptionClass, this, function, executor);
	}

	default Future<O> withTimeout(
			long timeout, TimeUnit unit, @Nullable Throwable exceptionOnTimeout, boolean interruptOnTimeout)
	{
		return new FutureTimeout<>(this, timeout, unit, exceptionOnTimeout, interruptOnTimeout);
	}

	default Future<O> withTimeout(long timeout, TimeUnit unit) {
		return withTimeout(timeout, unit, null, NO_INTERRUPT);
	}

	default Future<O> withTimeout(long timeout, TimeUnit unit, Throwable exceptionOnTimeout) {
		return withTimeout(timeout, unit, exceptionOnTimeout, NO_INTERRUPT);
	}

	default void end() {
		setListener(EndListener.INSTANCE);
	}

	void addPendingString(StringBuilder sb, int maxDepth);

	default String getPendingString(int maxDepth) {
		StringBuilder sb = new StringBuilder();
		addPendingString(sb, maxDepth);
		return sb.toString();
	}

	void toString(StringBuilder sb, boolean includeState);

	@Override @NonNull String toString();

	class FutureNotCompleteException extends IllegalStateException {
		public FutureNotCompleteException() {}

		public FutureNotCompleteException(String message) {
			super(message);
		}

		public FutureNotCompleteException(String message, Throwable cause) {
			super(message, cause);
		}

		public FutureNotCompleteException(Throwable cause) {
			super(cause);
		}
	}

	class AsyncCheckedException extends RuntimeException {
		public AsyncCheckedException(Throwable e) {
			super(e);
		}
	}

	class SetListenerCalledTwiceException extends IllegalStateException {
		public SetListenerCalledTwiceException() {}

		public SetListenerCalledTwiceException(String message) {
			super(message);
		}

		public SetListenerCalledTwiceException(String message, Throwable cause) {
			super(message, cause);
		}

		public SetListenerCalledTwiceException(Throwable cause) {
			super(cause);
		}
	}

	class FutureSucceededTwiceException extends IllegalStateException {
		public FutureSucceededTwiceException() {}

		public FutureSucceededTwiceException(String message) {
			super(message);
		}

		public FutureSucceededTwiceException(String message, Throwable cause) {
			super(message, cause);
		}

		public FutureSucceededTwiceException(Throwable cause) {
			super(cause);
		}
	}
}
