package com.mpd.concurrent.executors;

import androidx.annotation.NonNull;

import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.SubmittableFuture;
import com.mpd.concurrent.futures.impl.AbstractListenerFutures.SingleParentImmediateListenerFuture;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Serializes future-chains, rather than individual runnables.
 *
 * In general, most code should be using this, rather than {@link SequentialExecutor}.
 */
public class TaskSequencer {
	protected final BlockingQueue<SubmittableFuture<?>> queue;
	private final Executor delegate;
	private final int width;
	private int inFlight = 0;

	public TaskSequencer(Executor delegate, int width, BlockingQueue<SubmittableFuture<?>> queue) {
		this.delegate = delegate;
		this.width = width;
		this.queue = queue;
	}

	// Factory methods
	public static TaskSequencer serialized(Executor delegate) {
		return new TaskSequencer(delegate, 1, new LinkedBlockingQueue<>());
	}

	public static TaskSequencer cpuParallel(Executor delegate) {
		return new TaskSequencer(delegate, Runtime.getRuntime().availableProcessors(), new LinkedBlockingQueue<>());
	}

	public static TaskSequencer bandwidthParallel(Executor delegate) {
		return new TaskSequencer(delegate, 2, new LinkedBlockingQueue<>());
	}

	public int getWidth() {
		return width;
	}

	public <O> SubmittableFuture<O> submit(SubmittableFuture<O> task) {
		SubmittableFuture<O> wrapped = new SingleParentImmediateListenerFuture<O, O>(task) {
			@Override protected void execute(Future<? extends O> parent) {
				afterExecute(parent);
			}
		};
		boolean shouldSubmit;
		synchronized (queue) {
			++inFlight;
			if (inFlight <= width) {
				shouldSubmit = true;
			} else {
				shouldSubmit = false;
				queue.add(wrapped);
			}
		}
		if (shouldSubmit) {
			try {
				delegate.submit(wrapped);
			} catch (RuntimeException e) {
				wrapped.setException(e);
			}
		}
		return wrapped;
	}

	protected void afterExecute(Future<?> task) {
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
					+ ']';
		}
	}
}
