package com.mpd.concurrent.executors;

import androidx.annotation.NonNull;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.SubmittableFuture;
import com.mpd.concurrent.futures.atomic.AbstractListenerFutures.SingleParentImmediateListenerFuture;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.checkerframework.checker.nullness.qual.Nullable;

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
			@Override protected void execute() {
				afterExecute(getParent());
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

	void toString(StringBuilder sb, boolean includeState) {
		synchronized (queue) {
			sb.append(getClass().getSimpleName()).append('@').append(System.identityHashCode(this));
			if (includeState) {
				sb.append("[delegate=");
				delegate.toString(sb, /*includeState=*/false);
				sb.append(", width=").append(width).append(", queueSize=").append(queue.size()).append(']');
			}
		}
	}

	@NonNull @Override public String toString() {
		StringBuilder sb = new StringBuilder();
		toString(sb, /* includeState=*/ true);
		return sb.toString();
	}
}
