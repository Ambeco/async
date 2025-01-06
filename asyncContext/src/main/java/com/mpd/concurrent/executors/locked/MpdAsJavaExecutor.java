package com.mpd.concurrent.executors.locked;

import androidx.annotation.NonNull;

import com.google.common.collect.ImmutableList;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.futures.SubmittableFuture;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MpdAsJavaExecutor implements AndAlsoJavaExecutor {
	private final Executor delegate;
	private boolean isShutdown = false;

	public MpdAsJavaExecutor(Executor executor) {
		delegate = executor;
	}

	@Override public int getWidth() {
		return delegate.getWidth();
	}

	@Override public @ThreadInExecutorEnum int ownsThread(Thread thread) {
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
		delegate.close();
		isShutdown = true;
	}

	@Override public <O> SubmittableFuture<O> execute(SubmittableFuture<O> runnable) {
		return delegate.execute(runnable);
	}

	@Override public void close() {
		delegate.close();
		isShutdown = true;
	}

	@Override public List<Runnable> shutdownNow() {
		if (delegate instanceof java.util.concurrent.ScheduledExecutorService) {
			return ((java.util.concurrent.ScheduledExecutorService) delegate).shutdownNow();
		} else {
			delegate.close();
			return ImmutableList.of();
		}
	}

	@Override public boolean isShutdown() {
		if (delegate instanceof java.util.concurrent.ScheduledExecutorService) {
			return ((java.util.concurrent.ScheduledExecutorService) delegate).isShutdown();
		} else {
			return isShutdown;
		}
	}

	@Override public boolean isTerminated() {
		if (delegate instanceof java.util.concurrent.ScheduledExecutorService) {
			return ((java.util.concurrent.ScheduledExecutorService) delegate).isTerminated();
		} else {
			return isShutdown;
		}
	}

	@Override public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
		if (delegate instanceof java.util.concurrent.ScheduledExecutorService) {
			return ((java.util.concurrent.ScheduledExecutorService) delegate).awaitTermination(l, timeUnit);
		} else {
			close();
			return true;
		}
	}

	@NonNull @Override public String toString() {
		return getClass().getSimpleName() + '@' + System.identityHashCode(this) + '[' + delegate.toString() + ']';
	}
}
