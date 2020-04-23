package com.tbohne.asyncatomic;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Queues work at the moment its queued for the thread that queued it, but work is executed serially.
 * <p>
 * Be warned that this may have unexpected results if the queued work itself queues more work, and
 * needs the result of that second work. This may cause a deadlock, since the work is serialized.
 * If you queue workers that themselves queue more workers that you do need to wait for, consider
 * {@link DirectExecutor} instead, which actually does execute the subsequent work before the
 * current runnable finishes.
 * <p>
 * SerializedDirectExecutor cannot be shut down
 */
public class SerializedDirectExecutor extends ExecutorBase {
	private static SerializedDirectExecutor instance = new SerializedDirectExecutor();
	private ThreadLocal<Queue<Runnable>> queues;

	private SerializedDirectExecutor() {}

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
				if (next instanceof RunnableFuture && !((RunnableFuture) next).completed()) {
					throw new IllegalStateException(
							"RunnableFuture did not complete itself when run");
				}
			} catch (RuntimeException e) {
				if (next instanceof RunnableFuture) {
					throw new IllegalStateException("RunnableFuture.run threw an exception", e);
				}
			} finally {
				restoreInterruptState(interrupted);
			}
		}
	}

	protected void queueRunnable(Runnable runnable) {
		if (!shouldRunNow(runnable)) {
			return;
		}
		processRunnables();
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
