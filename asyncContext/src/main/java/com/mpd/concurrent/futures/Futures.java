package com.mpd.concurrent.futures;

import static com.mpd.concurrent.executors.MoreExecutors.directExecutor;

import com.google.common.collect.ImmutableList;
import com.mpd.concurrent.AsyncCallable;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future.FutureNotCompleteException;
import com.mpd.concurrent.futures.impl.AbstractFutureCompleteCombiner.VoidFutureCompleteCombiner;
import com.mpd.concurrent.futures.impl.AbstractFutureSuccessCombiner;
import com.mpd.concurrent.futures.impl.AbstractFutureSuccessCombiner.VoidFutureSuccessCombiner;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

public final class Futures {
	private Futures() {}

	public static <O> Future<List<O>> allAsList(Future<? extends O>... futures) {
		return allAsList(ImmutableList.copyOf(futures));
	}

	public static <O> Future<List<O>> allAsList(Collection<? extends Future<? extends O>> futures) {
		return new FuturesAsListCombiner<>(futures, directExecutor());
	}

	// This only exists for backwards compatibility with Guava
	public static <V> V getDone(Future<V> future) {
		return future.getDone();
	}

	public static <O> Future<O> immediateCancelledFuture() {
		return new ImmediateFuture<>(new CancellationException());
	}

	public static <O> Future<O> immediateFailedFuture(Throwable exception) {
		return new ImmediateFuture<>(exception);
	}

	public static <O> Future<O> immediateFuture(O value) {
		return new ImmediateFuture<>(value);
	}

	public static Future<Void> immediateVoidFuture() {
		return new ImmediateFuture<>((Void) null);
	}

	// This only exists for compatibility with Guava
	public static <O> Future<O> scheduleAsync(
			AsyncCallable<O> callable, long delay, TimeUnit timeUnit, Executor executorService)
	{
		return executorService.scheduleAsync(callable, delay, timeUnit);
	}

	// This only exists for compatibility with Guava
	public static <O> Future<O> submitAsync(AsyncCallable<O> callable, Executor executor) {
		return executor.submitAsync(callable);
	}

	public static Future<Void> whenAllComplete(Future<?>... futures) {
		return whenAllComplete(Arrays.asList(futures));
	}

	public static Future<Void> whenAllComplete(Collection<? extends Future<?>> futures) {
		return new VoidFutureCompleteCombiner<>(futures);
	}

	public static Future<Void> whenAllSucceeded(Future<?>... futures) {
		return whenAllSucceeded(Arrays.asList(futures));
	}

	public static Future<Void> whenAllSucceeded(Collection<? extends Future<?>> futures) {
		return new VoidFutureSuccessCombiner<>(futures);
	}

	public static <O> @Nullable Throwable getFutureExceptions(Future<?>... futures) {
		return getFutureExceptions(Arrays.asList(futures));
	}

	public static <O> @Nullable Throwable getFutureExceptions(Collection<? extends Future<? extends O>> futures) {
		// Group up all exceptions
		Throwable resultException = null;
		for (Future<?> future : futures) {
			Throwable nextException = future.isDone() ? future.getException() : new FutureNotCompleteException();
			if (nextException != null) {
				if (resultException == null) {
					resultException = nextException;
				} else {
					resultException.addSuppressed(nextException);
				}
			}
		}
		return resultException;
	}

	public static @Nullable Throwable getFutureExceptions(Future<?> future1, Future<?> future2) {
		Throwable e1 = future1.isDone() ? future1.getException() : new FutureNotCompleteException();
		Throwable e2 = future2.isDone() ? future2.getException() : new FutureNotCompleteException();
		if (e1 == null) {
			return e2;
		} else if (e2 == null) {
			return e1;
		} else {
			e1.addSuppressed(e2);
			return e1;
		}
	}

	public static class FuturesAsListCombiner<O> extends AbstractFutureSuccessCombiner<O, List<O>> {

		protected FuturesAsListCombiner(Future<? extends O>[] futures)
		{
			this(futures, null);
		}

		protected FuturesAsListCombiner(Collection<? extends Future<? extends O>> futures)
		{
			this(futures, null);
		}

		protected FuturesAsListCombiner(Future<? extends O>[] futures, @Nullable Executor executor)
		{
			this(ImmutableList.copyOf(futures), executor);
		}

		protected FuturesAsListCombiner(
				@NonNull Collection<? extends Future<? extends O>> futures, @Nullable Executor executor)
		{
			super(futures, executor);
		}

		@Override protected void execute() throws Exception {
			ImmutableList.Builder<O> list = new ImmutableList.Builder<>();
			for (Future<? extends O> future : getParents()) {
				list.add(future.getDone());
			}
			setResult(list.build());
		}
	}
}