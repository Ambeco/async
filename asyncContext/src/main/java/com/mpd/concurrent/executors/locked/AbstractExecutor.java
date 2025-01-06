package com.mpd.concurrent.executors.locked;

import android.os.SystemClock;

import androidx.annotation.CallSuper;

import com.google.common.flogger.FluentLogger;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.SubmittableFuture;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractExecutor implements AndAlsoJavaExecutor {
	private static final FluentLogger log = FluentLogger.forEnclosingClass();

	protected final CopyOnWriteArrayList<ExecutorListener> listeners = new CopyOnWriteArrayList<>();
	protected int state;
	protected BlockingQueue<SubmittableFuture<?>> queue;
	// TODO: Also add a DelayQueue for scheduled work?

	protected AbstractExecutor(int state) {
		this.state = state;
	}

	protected AbstractExecutor(int state, BlockingQueue<SubmittableFuture<?>> queue) {
		this.state = state;
		this.queue = queue;
	}

	protected BlockingQueue<SubmittableFuture<?>> getQueue() {
		return queue;
	}

	@Override public void registerListener(ExecutorListener onIdleCallback) {
		listeners.add(onIdleCallback);
	}

	@Override public boolean unregisterListener(ExecutorListener onIdleCallback) {
		return listeners.remove(onIdleCallback);
	}

	@Override public void awaitIdle(long timeout, TimeUnit unit) throws TimeoutException {
		ExecutorIdleLatchListener listener = new ExecutorIdleLatchListener();
		long timeoutMillis = unit.toMillis(timeout);
		long startTimeMillis = SystemClock.uptimeMillis();
		try {
			registerListener(listener);
			if (listener.getCount() == 1) {
				log.atFine().log(
						"Thread %s blocking for up to %dms, waiting for Executor %s to become idle",
						Thread.currentThread(),
						timeoutMillis,
						this);
				if (!listener.await(timeout, unit)) {
					log.atFine().log(
							"Thread %s timed out after %dms out of a maximum of %dms, waiting for Executor %s to become idle",
							Thread.currentThread(),
							SystemClock.uptimeMillis() - startTimeMillis,
							timeoutMillis,
							this);
					throw new TimeoutException();
				}
			}
		} catch (InterruptedException e) {
			log.atFine().log(
					"Thread %s interrupted after %dms out of a maximum of %dms, waiting for Executor %s to become idle",
					Thread.currentThread(),
					SystemClock.uptimeMillis() - startTimeMillis,
					timeoutMillis,
					this);
			Thread.currentThread().interrupt();
		} finally {
			unregisterListener(listener);
		}
	}

	@Override @CallSuper public void shutdown() {
		log.atFine().log("Thread %s telling Executor %s to safely stop async", Thread.currentThread(), this);
		state = ExecutorState.STATE_STOPPING;
	}

	protected void executeRunnable(SubmittableFuture<?> runnable) {
		try {
			try {
				// before execute
				for (ExecutorListener listener : listeners) {
					listener.beforeExecute(runnable);
				}

				// execute
				runnable.run();
			} catch (RuntimeException e) {
				runnable.setException(e);
			}

			// afterExecute
			for (ExecutorListener listener : listeners) {
				listener.afterExecute(runnable);
			}

			// handle exceptions from listeners
		} catch (RuntimeException e) {
			try {
				runnable.setException(e);

				// handle exceptions with the executor+future classes themselves
			} catch (RuntimeException e2) {
				Future.futureConfig.onUnhandledException(e2);
			}
		}
	}

	protected void callOnIdle() {
		for (ExecutorListener listener : listeners) {
			listener.onIdle();
		}
	}

}