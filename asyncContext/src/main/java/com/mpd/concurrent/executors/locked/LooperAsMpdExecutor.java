package com.mpd.concurrent.executors.locked;

import static com.mpd.concurrent.executors.Executor.threadInExecutorEnum;

import android.os.Handler;
import android.os.Looper;
import android.os.MessageQueue;
import android.os.MessageQueue.IdleHandler;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.mpd.concurrent.futures.SchedulableFuture;
import com.mpd.concurrent.futures.SubmittableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LooperAsMpdExecutor implements AndAlsoJavaExecutor, IdleHandler {
	public static final LooperAsMpdExecutor UI_THREAD_EXECUTOR = new LooperAsMpdExecutor(Looper.getMainLooper());

	private static final FluentLogger log = FluentLogger.forEnclosingClass();

	private final Handler handler;
	private final MessageQueue queue;
	private final long maxScheduledUptimeMillis = 0;


	private final List<ExecutorListener> idleCallbacks = new ArrayList<>();
	private int state = ExecutorState.STATE_STARTED;

	public LooperAsMpdExecutor(Looper looper) {
		this.handler = new Handler(looper);
		queue = handler.getLooper().getQueue();
		queue.addIdleHandler(this);
	}

	@Override public int getWidth() {
		return 1;
	}

	@Override public <O> SchedulableFuture<O> schedule(SchedulableFuture<O> task) {
		long delayMs = task.getDelay(TimeUnit.MILLISECONDS);
		Preconditions.checkArgument(delayMs > 0);
		if (!handler.postDelayed(task, delayMs)) {
			task.setException(new RejectedExecutionException());
		}
		return task;
	}

	@Override public synchronized @ThreadInExecutorEnum int ownsThread(Thread thread) {
		return threadInExecutorEnum(handler.getLooper().getThread() == thread);
	}

	@Override public boolean isIdleNow() {
		return handler.getLooper().getQueue().isIdle();
	}

	@Override public synchronized void registerListener(ExecutorListener onIdleCallback) {
		idleCallbacks.add(onIdleCallback);
	}

	@Override public synchronized boolean unregisterListener(ExecutorListener onIdleCallback) {
		return idleCallbacks.remove(onIdleCallback);
	}

	@Override public void awaitIdle(long timeout, TimeUnit unit) throws TimeoutException {
		if (handler.getLooper().getThread().equals(Thread.currentThread())) {
			throw new CannotWaitForOwnThreadException();
		}
		long initialUptimeMillis = SystemClock.uptimeMillis();
		long timeoutMillis = unit.toMillis(timeout);
		long currentUptimeMillis = initialUptimeMillis;
		long maxUptimeMillis = currentUptimeMillis + timeoutMillis;
		synchronized (this) {
			try {
				while (currentUptimeMillis < maxScheduledUptimeMillis || !queue.isIdle()) {
					long remainingWaitMillis = maxUptimeMillis - currentUptimeMillis;
					if (remainingWaitMillis < 0) {
						log.atFine().log(
								"Thread %s was blocked for %dms out of a maximum of %dms, waiting for Looper %s to become "
										+ "idle, and has now timed out",
								Thread.currentThread(),
								currentUptimeMillis - initialUptimeMillis,
								timeoutMillis,
								handler.getLooper());
						throw new TimeoutException();
					}
					log.atFiner().log(
							"Thread %s has blocked for  %dms out of a maximum of %dms. It will block for at most "
									+ "%dms more, waiting for Looper %s to become idle",
							Thread.currentThread(),
							currentUptimeMillis - initialUptimeMillis,
							timeoutMillis,
							remainingWaitMillis,
							handler.getLooper());
					wait(remainingWaitMillis);
					currentUptimeMillis = SystemClock.uptimeMillis();
				}
				log.atFine().log(
						"Thread %s now unblocked after %dms out of a maximum of %dms, waiting for Looper %s to become idle",
						Thread.currentThread(),
						currentUptimeMillis - initialUptimeMillis,
						timeoutMillis,
						handler.getLooper());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				currentUptimeMillis = SystemClock.uptimeMillis();
				log.atFine().withCause(e).log(
						"Thread %s was blocked for %dms out of a maximum of %dms, waiting for Looper %s "
								+ "to become idle, but was interrupted",
						Thread.currentThread(),
						currentUptimeMillis - initialUptimeMillis,
						timeoutMillis,
						handler.getLooper());
			}
		}
	}

	@Override synchronized public void shutdown() {
		if (handler.getLooper() == Looper.getMainLooper()) {
			throw new MainThreadShouldNotShutDownException();
		}
		log.atFine().log("Thread %s is safely shutting down Looper %s", Thread.currentThread(), handler.getLooper());
		handler.post(this::terminate);
		handler.getLooper().quitSafely();
		state = ExecutorState.STATE_STOPPING;
	}

	@Override public boolean queueIdle() {
		List<ExecutorListener> callbacks;
		synchronized (this) {
			callbacks = new ArrayList<>(idleCallbacks);
		}
		for (ExecutorListener runnable : callbacks) {
			runnable.onIdle();
		}
		return true;
	}

	@Override public <O> SubmittableFuture<O> execute(SubmittableFuture<O> future) {
		if (!handler.post(future)) {
			RejectedExecutionException e = new RejectedExecutionException();
			future.setException(e);
			throw e;
		}
		return future;
	}

	@Override synchronized public void close() {
		shutdown();
		queue.removeIdleHandler(this);
		state = ExecutorState.STATE_STOPPING;
	}

	@Override synchronized public List<Runnable> shutdownNow() {
		if (handler.getLooper() == Looper.getMainLooper()) {
			throw new MainThreadShouldNotShutDownException();
		}
		log.atFine().log("Thread %s is unsafely ending Looper %s", Thread.currentThread(), handler.getLooper());
		handler.post(this::terminate);
		handler.getLooper().quit();
		state = ExecutorState.STATE_STOPPING;
		return ImmutableList.of();
	}

	@Override synchronized public boolean isShutdown() {
		return state == ExecutorState.STATE_STOPPING || state == ExecutorState.STATE_TERMINATED;
	}

	@Override synchronized public boolean isTerminated() {
		return state == ExecutorState.STATE_TERMINATED;
	}

	@Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		if (handler.getLooper().getThread().equals(Thread.currentThread())) {
			throw new CannotWaitForOwnThreadException();
		}
		long initialUptimeMillis = SystemClock.uptimeMillis();
		long timeoutMillis = unit.toMillis(timeout);
		long currentUptimeMillis = initialUptimeMillis;
		long maxUptimeMillis = currentUptimeMillis + timeoutMillis;
		synchronized (this) {
			shutdown();
			while (state != ExecutorState.STATE_TERMINATED) {
				long remainingWaitMillis = maxUptimeMillis - currentUptimeMillis;
				if (remainingWaitMillis < 0) {
					log.atFine().log(
							"Thread %s was blocked for %dms out of a maximum of %dms, waiting for Looper %s to terminate, and has"
									+ " now timed out",
							Thread.currentThread(),
							currentUptimeMillis - initialUptimeMillis,
							timeoutMillis,
							handler.getLooper());
					throw new InterruptedException();
				}
				log.atFiner().log(
						"Thread %s was blocked for %dms out of a maximum of %dms. It will block for at most %dms, waiting for "
								+ "Looper %s to terminate",
						Thread.currentThread(),
						currentUptimeMillis - initialUptimeMillis,
						timeoutMillis,
						remainingWaitMillis,
						handler.getLooper());
				try {
					wait(remainingWaitMillis);
				} catch (InterruptedException e) {
					currentUptimeMillis = SystemClock.uptimeMillis();
					log.atFine().withCause(e).log(
							"Thread %s was blocked for %dms out of a maximum of %dms, waiting for Looper %s "
									+ "to terminate, but was interrupted",
							Thread.currentThread(),
							currentUptimeMillis - initialUptimeMillis,
							timeoutMillis,
							handler.getLooper());
					throw e;
				}
				currentUptimeMillis = SystemClock.uptimeMillis();
			}
			log.atFine().log(
					"Thread %s now unblocked after %dms out of a maximum of %dms, waiting for Looper %s to terminate",
					Thread.currentThread(),
					currentUptimeMillis - initialUptimeMillis,
					timeoutMillis,
					handler.getLooper());
			return true;
		}
	}

	synchronized private void terminate() {
		state = ExecutorState.STATE_TERMINATED;
		notifyAll();
	}

	@NonNull @Override public synchronized String toString() {
		return getClass().getSimpleName() + '@' + System.identityHashCode(this) + "[thread=" + handler.getLooper()
				.getThread()
				.getId() + ", isShutdown=" + isShutdown() + ']';
	}

	public static class MainThreadShouldNotShutDownException extends IllegalThreadStateException {}

	public static class CannotWaitForOwnThreadException extends IllegalThreadStateException {}

}
