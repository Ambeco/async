package com.mpd.concurrent.executors.locked;

import android.os.Build.VERSION_CODES;
import androidx.annotation.IntDef;
import androidx.annotation.RequiresApi;
import com.mpd.concurrent.AsyncCallable;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.SubmittableFuture;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface AndAlsoJavaExecutor extends Executor, java.util.concurrent.ScheduledExecutorService {
	@Override default <O> SubmittableFuture<O> execute(SubmittableFuture<O> task) {
		return null;
	}

	@Override default Future<?> submit(Runnable task) {
		return Executor.super.submit(task);
	}

	@Override default <O> Future<O> submit(Runnable task, O result) {
		return Executor.super.submit(task, result);
	}

	@Override default <O> Future<O> submit(Callable<O> task) {
		return Executor.super.submit(task);
	}

	@Override default <O> Future<O> submitAsync(AsyncCallable<O> task) {
		return Executor.super.submitAsync(task);
	}

	@Override @Deprecated default void execute(Runnable task) {
		Executor.super.execute(task);
	}

	@RequiresApi(api = VERSION_CODES.O) @Override default Future<?> schedule(Runnable task, Instant time) {
		return Executor.super.schedule(task, time);
	}

	@Override default Future<?> schedule(Runnable task, long delay, TimeUnit unit) {
		return Executor.super.schedule(task, delay, unit);
	}

	@RequiresApi(api = VERSION_CODES.O) @Override default <O> Future<O> schedule(Callable<O> task, Instant time) {
		return Executor.super.schedule(task, time);
	}

	@Override default <O> Future<O> schedule(Callable<O> task, long delay, TimeUnit unit) {
		return Executor.super.schedule(task, delay, unit);
	}

	@RequiresApi(api = VERSION_CODES.O) @Override
	default <O> Future<O> scheduleAsync(AsyncCallable<O> task, Instant time) {
		return Executor.super.scheduleAsync(task, time);
	}

	@Override default <O> Future<O> scheduleAsync(AsyncCallable<O> task, long delay, TimeUnit unit) {
		return Executor.super.scheduleAsync(task, delay, unit);
	}

	@Override default void close() {
		Executor.super.close();
	}

	@Override default <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> collection)
			throws InterruptedException
	{
		List<java.util.concurrent.Future<T>> list = new ArrayList<>(collection.size());
		for (Callable<T> callable : collection) {
			list.add(submit(callable));
		}
		return list;
	}

	@Override default <T> List<java.util.concurrent.Future<T>> invokeAll(
			Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit) throws InterruptedException
	{
		List<java.util.concurrent.Future<T>> list = new ArrayList<>(collection.size());
		for (Callable<T> callable : collection) {
			list.add(submit(callable).withTimeout(l, timeUnit));
		}
		return list;
	}

	@Override default <T> T invokeAny(Collection<? extends Callable<T>> collection)
			throws ExecutionException, InterruptedException
	{
		throw new UnsupportedOperationException();
	}

	@Override default <T> T invokeAny(Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit)
			throws ExecutionException, TimeoutException, InterruptedException
	{
		throw new UnsupportedOperationException();
	}

	@Override default ScheduledFuture<?> scheduleAtFixedRate(
			Runnable command, long initialDelay, long period, TimeUnit unit)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	default ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
		throw new UnsupportedOperationException();
	}

	@Retention(RetentionPolicy.SOURCE) @IntDef(
			{
					ExecutorState.STATE_STARTING,
					ExecutorState.STATE_STARTED,
					ExecutorState.STATE_STOPPING,
					ExecutorState.STATE_TERMINATED}) @interface ExecutorState {
		int STATE_STARTING = 1;
		int STATE_STARTED = 2;
		int STATE_STOPPING = 3;
		int STATE_TERMINATED = 4;
	}
}
