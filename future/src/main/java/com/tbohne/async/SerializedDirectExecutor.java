package com.tbohne.async;


import com.tbohne.async.impl.SettableValueFutureStep;
import com.tbohne.async.impl.SettableVoidFutureStep;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Supplier;

public class SerializedDirectExecutor implements Executor {
	private static SerializedDirectExecutor instance = new SerializedDirectExecutor();
	private ThreadLocal<Queue<Runnable>> queues;

	public SerializedDirectExecutor() {}

	public static SerializedDirectExecutor getSerializedDirectExecutor() {
		return instance;
	}

	private boolean shouldRunNow(Runnable runnable) {
		Queue<Runnable> queue = queues.get();
		if (queue == null) {
			queue = new ArrayDeque<>();
			queues.set(queue);
		}
		queue.add(runnable);
		return queue.size() == 1;
	}

	private Runnable nextRunnable() {
		Queue<Runnable> queue = queues.get();
		queue.poll();
		return queue.peek();
	}

	private void restoreInterruptState(boolean wasInterrupted) {
		if (wasInterrupted) {
			Thread.currentThread().interrupt();
		} else {
			//noinspection ResultOfMethodCallIgnored
			Thread.interrupted();
		}
	}

	private void processRunnables() {
		Queue<Runnable> queue = queues.get();
		Runnable next;
		while ((next = queue.poll()) != null) {
			boolean interrupted = Thread.interrupted();
			try {
				next.run();
			} finally {
				restoreInterruptState(interrupted);
			}
		}
	}

	private void processRunnable(Runnable runnable) {
		if (!shouldRunNow(runnable)) {
			return;
		}
		processRunnables();
	}

	@Override
	public void submit(RunnableFuture runnable) {
		processRunnable(runnable);
	}

	@Override
	public VoidFuture submit(Runnable runnable) {
		SettableVoidFutureStep future = new SettableVoidFutureStep();
		Runnable realRunnable = () -> {
			try {
				runnable.run();
				future.setResult();
			} catch (RuntimeException e) {
				future.setFailed(e);
			}
		};
		processRunnable(realRunnable);
		return future;
	}

	@Override
	public <T> ValueFuture<T> submit(Supplier<T> runnable) {
		SettableValueFutureStep<T> future = new SettableValueFutureStep<>();
		Runnable realRunnable = () -> {
			try {
				future.setResult(runnable.get());
			} catch (RuntimeException e) {
				future.setFailed(e);
			}
		};
		processRunnable(realRunnable);
		return future;
	}

	@Override
	public boolean isShuttingDown() {
		return false;
	}

	@Override
	public VoidFuture shutdown() {
		throw new UnsupportedOperationException("SerializedDirectExecutor is unstoppable");
	}

	@Override
	public void shutdownNow() {
		throw new UnsupportedOperationException("SerializedDirectExecutor is unstoppable");
	}
}
