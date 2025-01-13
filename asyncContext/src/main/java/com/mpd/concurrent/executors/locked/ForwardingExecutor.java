package com.mpd.concurrent.executors.locked;

import androidx.annotation.NonNull;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.mpd.concurrent.AsyncCallable;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.SubmittableFuture;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class ForwardingExecutor implements AndAlsoJavaExecutor {
	protected final ForwardingExecutor delegate;

	protected ForwardingExecutor(ForwardingExecutor delegate) {
		this.delegate = delegate;
	}

	public ForwardingExecutor getDelegate() {
		return delegate;
	}

	@Override public int getWidth() {
		return delegate.getWidth();
	}

	@Override public synchronized @ThreadInExecutorEnum int ownsThread(Thread thread) {
		return delegate.ownsThread(thread);
	}

	@Override public boolean isIdleNow() {
		return delegate.isIdleNow();
	}

	@Override public void registerListener(ExecutorListener onIdleCallback) {
		delegate.registerListener(onIdleCallback);
	}

	@Override public boolean unregisterListener(ExecutorListener onIdleCallback) {
		return delegate.unregisterListener(onIdleCallback);
	}

	@Override public void awaitIdle(long timeout, TimeUnit unit) throws TimeoutException {
		delegate.awaitIdle(timeout, unit);
	}

	@Override public void shutdown() {
		delegate.shutdown();
	}

	@Override public @CanIgnoreReturnValue <O> SubmittableFuture<O> execute(SubmittableFuture<O> task) {
		return delegate.execute(task);
	}

	@Override public Future<?> submit(Runnable task) {
		return delegate.submit(task);
	}

	@Override public <O> Future<O> submit(Runnable task, O result) {
		return delegate.submit(task, result);
	}

	@Override public <O> Future<O> submit(Callable<O> task) {
		return delegate.submit(task);
	}

	@Override public <O> Future<O> submitAsync(AsyncCallable<O> task) {
		return delegate.submitAsync(task);
	}

	@Override public @Deprecated void execute(Runnable task) {
		delegate.execute(task);
	}

	@Override public Future<?> schedule(Runnable task, long delay, TimeUnit unit) {
		return delegate.schedule(task, delay, unit);
	}

	@Override public <O> Future<O> schedule(Callable<O> task, long delay, TimeUnit unit) {
		return delegate.schedule(task, delay, unit);
	}

	@Override public <O> Future<O> scheduleAsync(AsyncCallable<O> task, long delay, TimeUnit unit) {
		return delegate.scheduleAsync(task, delay, unit);
	}

	@Override public void close() {
		delegate.close();
	}

	@Override public <T> List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> collection)
			throws InterruptedException
	{
		return delegate.invokeAll(collection);
	}

	@Override public <T> List<java.util.concurrent.Future<T>> invokeAll(
			Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit) throws InterruptedException
	{
		return delegate.invokeAll(collection, l, timeUnit);
	}

	@Override public <T> T invokeAny(Collection<? extends Callable<T>> collection)
			throws ExecutionException, InterruptedException
	{
		return delegate.invokeAny(collection);
	}

	@Override public <T> T invokeAny(Collection<? extends Callable<T>> collection, long l, TimeUnit timeUnit)
			throws ExecutionException, TimeoutException, InterruptedException
	{
		return delegate.invokeAny(collection, l, timeUnit);
	}

	@Override public ScheduledFuture<?> scheduleAtFixedRate(
			Runnable command, long initialDelay, long period, TimeUnit unit)
	{
		return delegate.scheduleAtFixedRate(command, initialDelay, period, unit);
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
		return delegate.scheduleWithFixedDelay(command, initialDelay, delay, unit);
	}

	@Override public List<Runnable> shutdownNow() {
		return delegate.shutdownNow();
	}

	@Override public boolean isShutdown() {
		return delegate.isShutdown();
	}

	@Override public boolean isTerminated() {
		return delegate.isTerminated();
	}

	@Override public boolean awaitTermination(long timeout, TimeUnit unit) {
		return delegate.awaitTermination(timeout, unit);
	}

	@Override public int hashCode() {
		return delegate.hashCode();
	}

	/**
	 * @noinspection EqualsWhichDoesntCheckParameterClass
	 */
	@Override public boolean equals(Object obj) {
		return delegate.equals(obj);
	}

	@NonNull @Override public String toString() {
		StringBuilder sb = new StringBuilder();
		toString(sb, /* includeState=*/ true);
		return sb.toString();
	}

	@Override public void toString(StringBuilder sb, boolean includeState) {
		synchronized (this) {
			sb.append(getClass().getSimpleName()).append('@').append(System.identityHashCode(this));
			if (includeState) {
				sb.append("[delegate=");
				delegate.toString(sb, /*includeState=*/false);
				sb.append(']');
			}
		}
	}
}
