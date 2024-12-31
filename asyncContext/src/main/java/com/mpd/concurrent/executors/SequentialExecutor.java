package com.mpd.concurrent.executors;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.mpd.concurrent.executors.impl.AndAlsoJavaExecutor;
import com.mpd.concurrent.futures.SubmittableFuture;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/*
 * Serializes runnables to a given bandwidth
 *
 * This can be used to emulate "synchronized" locking without ever blocking an OS thread.
 *
 * Outside of "synchronized" lock emulation, most code probably wants to serialize future-chains, rather than
 * individual runnables, and thus should be using {@link TaskSequencer}.
 */
public class SequentialExecutor implements AndAlsoJavaExecutor, Executor.ExecutorListener {
	private final Executor delegate;
	private final int width;
	private final BlockingQueue<SubmittableFuture<?>> queue;
	private int inFlight = 0;

	public SequentialExecutor(Executor delegate, int width, BlockingQueue<SubmittableFuture<?>> queue) {
		this.delegate = delegate;
		this.width = width;
		this.queue = queue;
	}

	// Factory methods
	public static SequentialExecutor serialized(Executor delegate) {
		return new SequentialExecutor(delegate, 1, new LinkedBlockingQueue<>());
	}

	public static SequentialExecutor cpuParallel(Executor delegate) {
		return new SequentialExecutor(delegate, Runtime.getRuntime().availableProcessors(), new LinkedBlockingQueue<>());
	}

	public static SequentialExecutor bandwidthParallel(Executor delegate) {
		return new SequentialExecutor(delegate, 2, new LinkedBlockingQueue<>());
	}

	@Override public int getWidth() {
		return width;
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
		delegate.shutdown();
	}

	@Override public <O> SubmittableFuture<O> execute(SubmittableFuture<O> task) {
		boolean shouldSubmit;
		synchronized (queue) {
			++inFlight;
			if (inFlight <= width) {
				shouldSubmit = true;
			} else {
				shouldSubmit = false;
				queue.add(task);
			}
		}
		if (shouldSubmit) {
			try {
				delegate.submit(task);
			} catch (RuntimeException e) {
				task.setException(e);
			}
		}
		return task;
	}

	@Override public void close() {
		delegate.close();
		delegate.unregisterListener(this);
	}

	@RestrictTo(RestrictTo.Scope.SUBCLASSES) @Override public void afterExecute(SubmittableFuture<?> task) {
		@Nullable Runnable next;
		synchronized (queue) {
			--inFlight;
			next = queue.remove();
		}
		if (next != null) {
			try {
				delegate.submit(next);
			} catch (RuntimeException e) {
				task.setException(e);
			}
		}
	}

	@Override public List<Runnable> shutdownNow() {
		if (delegate instanceof java.util.concurrent.ExecutorService) {
			return ((java.util.concurrent.ExecutorService) delegate).shutdownNow();
		} else {
			delegate.shutdown();
			return new ArrayList<>();
		}
	}

	@Override public boolean isShutdown() {
		if (delegate instanceof java.util.concurrent.ExecutorService) {
			return ((java.util.concurrent.ExecutorService) delegate).isShutdown();
		} else {
			return false;
		}
	}

	@Override public boolean isTerminated() {
		if (delegate instanceof java.util.concurrent.ExecutorService) {
			return ((java.util.concurrent.ExecutorService) delegate).isShutdown();
		} else {
			return false;
		}
	}

	@Override public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
		if (delegate instanceof java.util.concurrent.ExecutorService) {
			return ((java.util.concurrent.ExecutorService) delegate).awaitTermination(l, timeUnit);
		} else {
			close();
			return true;
		}
	}

	@NonNull @Override public String toString() {
		synchronized (queue) {
			return getClass().getSimpleName()
					+ '@'
					+ System.identityHashCode(this)
					+ "[delegate="
					+ delegate.toString()
					+ ", width="
					+ width
					+ ", queueSize="
					+ queue.size()
					+ ", isShutdown="
					+ isShutdown()
					+ ']';
		}
	}
}
