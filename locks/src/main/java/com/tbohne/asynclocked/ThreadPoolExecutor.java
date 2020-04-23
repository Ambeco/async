package com.tbohne.asynclocked;

import com.tbohne.asynclocked.impl.SettableVoidFuture;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ThreadPoolExecutor extends ExecutorBase {
	private static final long MIN_KEEP_ALIVE_MILLIS = 15;

	private final Object threadLock = new Object();
	private final int corePoolSize;
	private final int quickQueueSize;
	private final int maximumPoolSize;
	private final long keepAliveNanos;
	private final BlockingQueue<Runnable> realQueue;
	private final ThreadFactory threadFactory;
	private final RejectedExecutionHandler rejectedHandler;
	private final List<Thread> threads = new CopyOnWriteArrayList<>();
	private AtomicLong threadDeathTimerNanos = new AtomicLong();
	private AtomicReference<SettableVoidFuture> stopping = new AtomicReference<>();
	private Runnable coreThreadRunnable = new ThreadRunnable(true);
	private Runnable flexThreadRunnable = new ThreadRunnable(false);
	private Runnable POISON = () -> {};

	public ThreadPoolExecutor(int corePoolSize,
			int maximumPoolSize,
			long keepAliveTimeMillis,
			int quickQueueSize,
			BlockingQueue<Runnable> realQueue,
			ThreadFactory threadFactory,
			RejectedExecutionHandler rejectedHandler) {
		this.corePoolSize = corePoolSize;
		this.quickQueueSize = quickQueueSize;
		this.maximumPoolSize = maximumPoolSize;
		this.realQueue = realQueue;
		this.threadFactory = threadFactory;
		this.rejectedHandler = rejectedHandler != null
				? rejectedHandler
				: (runnable, threadPoolExecutor) -> {
					throw new RejectedExecutionException("rejected");
				};
		if (keepAliveTimeMillis < MIN_KEEP_ALIVE_MILLIS) {
			keepAliveTimeMillis = MIN_KEEP_ALIVE_MILLIS;
		}
		this.keepAliveNanos = keepAliveTimeMillis * 1000000;
	}

	protected void queueRunnable(Runnable runnable) throws RejectedExecutionException {
		if (stopping.get() != null) {
			rejectedHandler.rejectedExecution(runnable, this);
			return;
		}
		threadDeathTimerNanos.set(System.nanoTime());
		if (!realQueue.offer(runnable)) {
			rejectedHandler.rejectedExecution(runnable, this);
			return;
		}
		Thread.yield(); // Give existing threads a chance to snag the work before we make another
		synchronized (threadLock) {
			if (!realQueue.contains(runnable)) {
				return; //existing thread already started the work
			}
			boolean addCore = threads.size() < corePoolSize;
			boolean addFlex = realQueue.size() > quickQueueSize && threads.size() < maximumPoolSize;
			if (addCore || addFlex) {
				threads.add(threadFactory.newThread(addCore
						? coreThreadRunnable
						: flexThreadRunnable));
			}
		}
	}

	@Override
	public boolean isShuttingDown() {
		return stopping.get() != null;
	}

	@Override
	public VoidFuture shutdown() {
		if (stopping.compareAndSet(null, new SettableVoidFuture())) {
			int threadCount;
			synchronized (threadLock) {
				threadCount = threads.size();
			}
			for (int i = 0; i < threadCount; i++) {
				realQueue.offer(POISON);
			}
		}
		return stopping.get();
	}

	@Override
	public void shutdownNow() throws InterruptedException {
		Async.blockThreadUntilComplete(shutdown());
	}

	class ThreadRunnable implements Runnable {
		private final boolean core;

		ThreadRunnable(boolean core) {
			this.core = core;
		}

		Runnable poll() {
			if (core) {
				return realQueue.poll();
			} else {
				try {
					return realQueue.poll(keepAliveNanos, TimeUnit.NANOSECONDS);
				} catch (InterruptedException e) {
					return null;
				}
			}
		}

		@Override
		public void run() {
			try {
				for (; ; ) {
					Runnable runnable = poll();
					if (stopping.get() != null) {
						return;
					}
					if (runnable != null) {
						runnable.run();
						boolean ignored = Thread.interrupted(); //clear interrupt bit
					} else {
						synchronized (threadLock) {
							if (threadDeathTimerNanos.get() + keepAliveNanos < System.nanoTime()) {
								threadDeathTimerNanos.set(System.nanoTime());
								return;
							} // else a runnable was added or a different thread died. Poll again.
						}
					}
				}
			} finally {
				synchronized (threadLock) {
					threads.remove(Thread.currentThread());
				}
			}
		}
	}
}
