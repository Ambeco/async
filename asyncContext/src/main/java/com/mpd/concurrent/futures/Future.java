package com.mpd.concurrent.futures;

import androidx.annotation.NonNull;

import com.mpd.concurrent.AsyncFunction;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.FutureListener.RunnableListener;
import com.mpd.concurrent.futures.impl.EndListener;
import com.mpd.concurrent.futures.impl.FutureAsyncFunction;
import com.mpd.concurrent.futures.impl.FutureCatchingAsyncFunction;
import com.mpd.concurrent.futures.impl.FutureCatchingFunction;
import com.mpd.concurrent.futures.impl.FutureFunction;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

// Based on Guava FluentFuture, except that each future can only have one listener,
// and cancellationException results in a CancellationException
public interface Future<O> extends java.util.concurrent.ScheduledFuture<O> {
	FutureConfig futureConfig = new FutureConfig.DefaultFutureConfig();

	boolean MAY_INTERRUPT = true;
	boolean NO_INTERRUPT = false;

	@SuppressWarnings("UnusedReturnValue") default boolean cancel(boolean mayInterruptIfRunning) {
		return setException(new CancellationException(), mayInterruptIfRunning);
	}

	default boolean isCancelled() {
		return isDone() && exceptionNow() instanceof CancellationException;
	}

	boolean isDone();

	@Deprecated default O get() {
		return get(Integer.MAX_VALUE, TimeUnit.DAYS);
	}

	@Deprecated O get(long timeout, TimeUnit unit);

	@SuppressWarnings("UnusedReturnValue")
	default boolean cancel(CancellationException exception, boolean mayInterruptIfRunning) {
		return setException(exception, mayInterruptIfRunning);
	}

	default boolean isSuccessful() {
		return isDone() && exceptionNow() == null;
	}

	O resultNow(); //or throws FutureNotCompleteException, or the completed RuntimeException, or AsyncCheckedException

	@SuppressWarnings("UnusedReturnValue") boolean setException(Throwable exception);

	@SuppressWarnings("UnusedReturnValue")
	default boolean setException(Throwable exception, boolean mayInterruptIfRunning) {
		return setException(exception, mayInterruptIfRunning);
	}

	@MonotonicNonNull Throwable exceptionNow(); //or throws FutureNotCompleteException

	void setListener(FutureListener<? super O> task);

	default void setListener(Runnable task, Executor executor) {
		if (task instanceof FutureListener) {
			setListener((FutureListener<O>) task);
		} else {
			setListener(new RunnableListener<>(task, executor));
		}
	}

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

	default void end() {
		setListener(EndListener.INSTANCE);
	}

	Future<O> withTimeout(
			long timeout, TimeUnit unit, @Nullable Throwable exceptionOnTimeout, boolean interruptOnTimeout);

	default Future<O> withTimeout(long timeout, TimeUnit unit) {
		return withTimeout(timeout, unit, null, NO_INTERRUPT);
	}

	default Future<O> withTimeout(long timeout, TimeUnit unit, Throwable exceptionOnTimeout) {
		return withTimeout(timeout, unit, exceptionOnTimeout, NO_INTERRUPT);
	}

	void addPendingString(StringBuilder sb, int maxDepth);

	void toString(StringBuilder sb);

	@NonNull @Override String toString();

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
