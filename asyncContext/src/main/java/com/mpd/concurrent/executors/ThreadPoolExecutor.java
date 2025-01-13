package com.mpd.concurrent.executors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.mpd.concurrent.executors.Executor.threadInExecutorEnum;

import android.os.SystemClock;
import androidx.annotation.NonNull;
import com.google.common.collect.ImmutableList;
import com.mpd.concurrent.executors.locked.AndAlsoJavaExecutor;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.SubmittableFuture;
import com.mpd.concurrent.futures.atomic.FutureRunnable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This mirrors https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ScheduledThreadPoolExecutor.html
 */
public class ThreadPoolExecutor implements AndAlsoJavaExecutor {
	private static final long DEFAULT_KEEP_ALIVE_TIME = 10;
	private static final TimeUnit DEFAULT_KEEP_ALIVE_UNIT = TimeUnit.SECONDS;
	private static final int DEFAULT_POOL_SIZE = 8;
	private static final SubmittableFuture<?> TIMEOUT_PILL_RUNNABLE = new FutureRunnable<>(null, () -> {});
	private final List<Thread> threads;
	private final BlockingQueue<SubmittableFuture<?>> queue;
	private final ArrayList<ExecutorListener> listeners = new ArrayList<>();
	private int corePoolSize;
	private int maxPoolSize;
	private long keepAliveTimeMs;
	private ThreadFactory threadFactory;
	private int runnableCount = 0;
	private long lastIdleTimeout = 0;
	private boolean isShutdown = false;

	public ThreadPoolExecutor(int corePoolSize) {
		this(corePoolSize, corePoolSize, DEFAULT_KEEP_ALIVE_TIME, DEFAULT_KEEP_ALIVE_UNIT, new LinkedBlockingQueue<>(),
				//TODO: Use a queue that allocates less.
				Thread::new);
	}

	public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize) {
		this(corePoolSize,
				maximumPoolSize,
				DEFAULT_KEEP_ALIVE_TIME,
				DEFAULT_KEEP_ALIVE_UNIT,
				new LinkedBlockingQueue<>(),
				Thread::new);
	}

	public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit) {
		this(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingQueue<>(), Thread::new);
	}

	public ThreadPoolExecutor(
			int corePoolSize,
			int maximumPoolSize,
			long keepAliveTime,
			TimeUnit unit,
			BlockingQueue<SubmittableFuture<?>> workQueue)
	{
		this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, Thread::new);
	}

	public ThreadPoolExecutor(
			int corePoolSize,
			int maximumPoolSize,
			long keepAliveTime,
			TimeUnit unit,
			BlockingQueue<SubmittableFuture<?>> workQueue,
			ThreadFactory threadFactory)
	{
		this.corePoolSize = corePoolSize;
		this.maxPoolSize = maximumPoolSize;
		this.keepAliveTimeMs = unit.toMillis(keepAliveTime);
		this.queue = workQueue;
		this.threadFactory = checkNotNull(threadFactory);
		if (corePoolSize > maxPoolSize) {
			throw new IllegalArgumentException("maximumPoolSize "
					+ maximumPoolSize
					+ "cannot be less than corePoolSize "
					+ corePoolSize);
		}
		int initialPoolSize = Math.min(Math.max(DEFAULT_POOL_SIZE, corePoolSize), maximumPoolSize);
		threads = new ArrayList<>(initialPoolSize);
	}

	// Factory methods
	public static ThreadPoolExecutor cpuParallel() {
		return new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
	}

	public static ThreadPoolExecutor bandwidthParallel() {
		return new ThreadPoolExecutor(2);
	}

	@Override public int getWidth() {
		return corePoolSize;
	}

	@Override public synchronized @ThreadInExecutorEnum int ownsThread(Thread thread) {
		return threadInExecutorEnum(threads.contains(thread));
	}

	@Override public boolean isIdleNow() {
		synchronized (threads) {
			return runnableCount == 0;
		}
	}

	@Override public void registerListener(ExecutorListener onIdleCallback) {
		boolean isIdle;
		synchronized (listeners) {
			listeners.add(onIdleCallback);
			isIdle = isIdleNow();
		}
		if (isIdle) {
			onIdleCallback.onIdle();
		}
	}

	@Override public boolean unregisterListener(ExecutorListener onIdleCallback) {
		synchronized (listeners) {
			return listeners.remove(onIdleCallback);
		}
	}

	@Override public void shutdown() {
		synchronized (threads) {
			isShutdown = true;
		}
	}

	@Override public List<Runnable> shutdownNow() {
		synchronized (threads) {
			isShutdown = true;
			List<Runnable> result = ImmutableList.copyOf(queue);
			queue.clear();
			for (Thread t : threads) {
				t.interrupt();
			}
			return result;
		}
	}

	@Override public boolean isShutdown() {
		synchronized (threads) {
			return isShutdown;
		}
	}

	@Override public boolean isTerminated() {
		synchronized (threads) {
			if (!isShutdown) {
				return false;
			}
			return threads.isEmpty();
		}
	}

	@Override public boolean awaitTermination(long timeout, TimeUnit unit) {
		try {
			awaitIdle(timeout, unit);
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public int getActiveCount() {
		synchronized (threads) {
			return Math.min(runnableCount, threads.size());
		}
	}

	public int getCorePoolSize() {
		return corePoolSize;
	}

	public void setCorePoolSize(int corePoolSize) {
		if (corePoolSize < 0) {
			throw new IllegalArgumentException("corePoolSize " + corePoolSize + " must be positive");
		}
		if (corePoolSize > maxPoolSize) {
			throw new IllegalArgumentException("corePoolSize "
					+ maxPoolSize
					+ " must be less than or equal to maxPoolSize "
					+ maxPoolSize);
		}
		this.corePoolSize = corePoolSize;
	}

	public long getKeepAliveTime(TimeUnit unit) {
		return unit.convert(keepAliveTimeMs, TimeUnit.MILLISECONDS);
	}

	public void setKeepAliveTime(long time, TimeUnit unit) {
		keepAliveTimeMs = unit.toMillis(time);
	}

	public int getMaximumPoolSize() {
		return maxPoolSize;
	}

	public void setMaximumPoolSize(int maximumPoolSize) {
		if (maximumPoolSize < corePoolSize) {
			throw new IllegalArgumentException("maximumPoolSize "
					+ maximumPoolSize
					+ " must be greater than or equal to "
					+ "corePoolSize "
					+ corePoolSize);
		}
		this.maxPoolSize = corePoolSize;
	}

	public int getPoolSize() {
		synchronized (threads) {
			return threads.size();
		}
	}

	public BlockingQueue<SubmittableFuture<?>> getQueue() {
		return queue;
	}

	public ThreadFactory getThreadFactory() {
		return threadFactory;
	}

	public void setThreadFactory(ThreadFactory threadFactory) {
		this.threadFactory = checkNotNull(threadFactory);
	}

	public int prestartAllCoreThreads() {
		synchronized (threads) {
			while (threads.size() < corePoolSize) {
				threads.add(threadFactory.newThread(this::threadRun));
			}
			return threads.size();
		}
	}

	public boolean prestartCoreThread() {
		synchronized (threads) {
			if (threads.size() >= corePoolSize) {
				return false;
			}
			threads.add(threadFactory.newThread(this::threadRun));
			return true;
		}
	}

	private boolean shouldStartNewThreadLocked() {
		if (runnableCount <= threads.size()) {
			return false; // There's an idle thread, so we don't need to add any
		} else if (runnableCount < corePoolSize) {
			return true; // can freely add more threads
		} else // we have max threads. just queue
			if (runnableCount <= threads.size() * 2) {
				return false;  //there's a queue, but let the existing threads handle it
			} else {
				return runnableCount < maxPoolSize;  // queue is piling up. add more threads until max
			}
	}

	@Override public <O> SubmittableFuture<O> execute(SubmittableFuture<O> runnable) {
		if (isShutdown) {
			throw new RejectedExecutionException("executor is stopping or stopped");
		}
		synchronized (threads) {
			++runnableCount;
			queue.add(runnable);
			if (shouldStartNewThreadLocked()) {
				threads.add(threadFactory.newThread(this::threadRun));
			}
		}
		return runnable;
	}

	@Override public void close() {
		try {
			synchronized (threads) {
				isShutdown = true;
				while (!threads.isEmpty()) {
					threads.wait();
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public void purge() {
		synchronized (threads) {
			queue.removeIf(Future::isCancelled);
		}
	}

	public boolean remove(SubmittableFuture<?> task) {
		synchronized (threads) {
			return queue.remove(task);
		}
	}

	private @Nullable SubmittableFuture<?> threadGetNextRunnable() {
		try {
			long ideleTimeoutMs;
			synchronized (threads) {
				if (isShutdown) {
					ideleTimeoutMs = 0;
				} else if (threads.size() <= corePoolSize) {
					ideleTimeoutMs = Long.MAX_VALUE;
				} else {
					ideleTimeoutMs = SystemClock.uptimeMillis() - lastIdleTimeout;
				}
			}
			return (ideleTimeoutMs < Long.MAX_VALUE) ? queue.poll(ideleTimeoutMs, TimeUnit.MILLISECONDS) : queue.poll();
		} catch (InterruptedException e) {
			synchronized (threads) {
				if (threads.size() > corePoolSize) {
					long currentUptimeMillis = SystemClock.uptimeMillis();
					if (currentUptimeMillis - lastIdleTimeout >= keepAliveTimeMs) {
						if (threads.remove(Thread.currentThread())) {
							lastIdleTimeout = currentUptimeMillis;
							threads.notifyAll();
						}
						return TIMEOUT_PILL_RUNNABLE;
					}
				}
				return null;
			}
		}
	}

	private void threadRun() {
		for (; ; ) {
			try {
				SubmittableFuture<?> runnable = threadGetNextRunnable();
				if (runnable == TIMEOUT_PILL_RUNNABLE) {
					return;
				}
				if (runnable == null) {
					continue;
				}
				try {
					for (ExecutorListener listener : listeners) {
						listener.beforeExecute(runnable);
					}
					runnable.run();
				} catch (RuntimeException e) {
					runnable.setException(e);
				}
				try {
					for (ExecutorListener listener : listeners) {
						listener.afterExecute(runnable);
					}
				} catch (RuntimeException e) {
					runnable.setException(e);
				}
			} catch (RuntimeException e) {
				Future.futureConfig.onUnhandledException(e);
			}
			runnableCount--; // TODO merge synchronized
		}
	}

	@Override public void toString(StringBuilder sb, boolean includeState) {
		synchronized (queue) {
			sb.append(getClass().getSimpleName()).append('@').append(System.identityHashCode(this));
			if (includeState) {
				sb.append("[poolSize=").append(threads.size()).append(", queueSize=").append(queue.size()).append(
						", isShutdown=").append(isShutdown).append(']');
			}
		}
	}

	@NonNull @Override public String toString() {
		StringBuilder sb = new StringBuilder();
		toString(sb, /* includeState=*/ true);
		return sb.toString();
	}

	protected void finalize() {
		close();
	}
}
