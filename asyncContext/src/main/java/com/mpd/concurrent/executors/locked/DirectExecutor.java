package com.mpd.concurrent.executors.locked;

import static com.mpd.concurrent.executors.Executor.threadInExecutorEnum;

import androidx.annotation.NonNull;

import com.mpd.concurrent.futures.SubmittableFuture;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DirectExecutor implements AndAlsoJavaExecutor {
	public static final DirectExecutor instance = new DirectExecutor();
	private final boolean isShutdown = false;

	DirectExecutor() {}

	public DirectExecutor directExecutor() {
		return instance;
	}

	@Override public int getWidth() {
		return 1;
	}

	@Override public synchronized @ThreadInExecutorEnum int ownsThread(Thread thread) {
		return threadInExecutorEnum(thread == Thread.currentThread());
	}

	@Override public boolean isIdleNow() {
		return false;
	}

	@Override public void registerListener(ExecutorListener onIdleCallback) {
		throw new UnsupportedOperationException();
	}

	@Override public boolean unregisterListener(ExecutorListener onIdleCallback) {
		throw new UnsupportedOperationException();
	}

	@Override public void awaitIdle(long timeout, TimeUnit unit) {
		throw new UnsupportedOperationException();
	}

	@Override public void shutdown() {
	}

	@Override public <O> SubmittableFuture<O> execute(SubmittableFuture<O> runnable) {
		runnable.run();
		return runnable;
	}

	@Override public void close() {
	}

	@Override public List<Runnable> shutdownNow() {
		return Collections.emptyList();
	}

	@Override public boolean isShutdown() {
		return false;
	}

	@Override public boolean isTerminated() {
		return false;
	}

	@Override public boolean awaitTermination(long l, TimeUnit timeUnit) {
		return true;
	}


	@NonNull @Override public String toString() {
		return getClass().getSimpleName() + '@' + System.identityHashCode(this);
	}
}
