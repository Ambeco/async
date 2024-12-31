package com.mpd.concurrent.executors.impl;

import androidx.annotation.NonNull;

import com.mpd.concurrent.futures.SubmittableFuture;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class JavaAsMpdExecutor implements AndAlsoJavaExecutor {
	private final java.util.concurrent.Executor delegate;
	private final int width;

	public JavaAsMpdExecutor(java.util.concurrent.Executor executor, int width) {
		delegate = executor;
		this.width = width;
	}

	@Override public int getWidth() {
		return width;
	}

	@Override public synchronized @ThreadInExecutorEnum int ownsThread(Thread thread) {
		return ThreadInExecutorEnum.THREAD_UNKNOWN_IN_EXEC;
	}

	@Override public boolean isIdleNow() {
		throw new UnsupportedOperationException();
	}

	@Override public void registerListener(ExecutorListener onIdleCallback) {
		throw new UnsupportedOperationException();
	}

	@Override public boolean unregisterListener(ExecutorListener onIdleCallback) {
		throw new UnsupportedOperationException();
	}

	@Override public void awaitIdle(long timeout, TimeUnit unit) throws TimeoutException {
		throw new UnsupportedOperationException();
	}

	@Override public void shutdown() {
		((ExecutorService) delegate).shutdown();
	}

	@Override public <O> SubmittableFuture<O> execute(SubmittableFuture<O> runnable) {
		delegate.execute(runnable);
		return runnable;
	}

	@Override public void close() {
		((ExecutorService) delegate).shutdown();
	}

	@Override public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> collection)
			throws InterruptedException
	{
		return ((ExecutorService) delegate).invokeAll(collection);
	}

	@Override public <T> List<java.util.concurrent.Future<T>> invokeAll(
			Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit) throws InterruptedException
	{
		return ((ExecutorService) delegate).invokeAll(collection, l, timeUnit);
	}

	@Override public <T> T invokeAny(Collection<? extends Callable<T>> collection)
			throws ExecutionException, InterruptedException
	{
		return ((ScheduledExecutorService) delegate).invokeAny(collection);
	}

	@Override public <T> T invokeAny(Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit)
			throws ExecutionException, TimeoutException, InterruptedException
	{
		return ((ScheduledExecutorService) delegate).invokeAny(collection, l, timeUnit);
	}

	@Override @Deprecated public ScheduledFuture<?> scheduleAtFixedRate(
			Runnable command, long initialDelay, long period, TimeUnit unit)
	{
		return ((ScheduledExecutorService) delegate).scheduleAtFixedRate(command, initialDelay, period, unit);
	}

	@Override @Deprecated
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
		return ((ScheduledExecutorService) delegate).scheduleWithFixedDelay(command, initialDelay, delay, unit);
	}

	@Override public List<Runnable> shutdownNow() {
		return ((ExecutorService) delegate).shutdownNow();
	}

	@Override public boolean isShutdown() {
		return ((ExecutorService) delegate).isShutdown();
	}

	@Override public boolean isTerminated() {
		return ((ExecutorService) delegate).isTerminated();
	}

	@Override public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
		return ((ExecutorService) delegate).awaitTermination(l, timeUnit);
	}

	@NonNull @Override public String toString() {
		return getClass().getSimpleName() + '@' + System.identityHashCode(this) + "[delegate=" + delegate + ']';
	}
}
