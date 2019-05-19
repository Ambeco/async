package com.tbohne.async;

import java.util.function.Supplier;

/**
 * Executes work at the moment its queued in the thread that queued it.
 * <p>
 * Be warned that this may have unexpected results if the queued work itself queues more work, in
 * that the first effectively pauses while the second runs. The work is not serialized.
 * If you queue workers that themselves queue more workers that you don't wait for, consider
 * {@link SerializedDirectExecutor} instead, which actually does queue the subsequent work for after
 * the current runnable finishes.
 * <p>
 * DirectExecutor cannot be shut down
 */
public class DirectExecutor implements Executor {
	private static DirectExecutor instance = new DirectExecutor();

	private DirectExecutor() {}

	public static DirectExecutor getDirectExecutor() {
		return instance;
	}

	private void restoreInterruptState(boolean interrupted) {
		if (interrupted) {
			Thread.currentThread().interrupt();
		} else {
			//noinspection ResultOfMethodCallIgnored
			Thread.interrupted();
		}
	}

	@Override
	public void submit(RunnableFuture runnable) {
		boolean interrupted = Thread.interrupted();
		try {
			runnable.run();
			if (!runnable.completed()) {
				throw new IllegalStateException("RunnableFuture did not complete itself when run");
			}
		} catch (RuntimeException e) {
			throw new IllegalStateException("RunnableFuture.run threw an exception", e);
		} finally {
			restoreInterruptState(interrupted);
		}
	}

	@Override
	public VoidFuture submit(Runnable runnable) {
		boolean interrupted = Thread.interrupted();
		try {
			runnable.run();
			return Async.immediateFuture();
		} catch (RuntimeException e) {
			return Async.failedVoidFuture(e);
		} finally {
			restoreInterruptState(interrupted);
		}
	}

	@Override
	public <T> ValueFuture<T> submit(Supplier<T> runnable) {
		boolean interrupted = Thread.interrupted();
		try {
			return Async.immediateFuture(runnable.get());
		} catch (RuntimeException e) {
			return Async.failedValueFuture(e);
		} finally {
			restoreInterruptState(interrupted);
		}
	}

	@Override
	public boolean isShuttingDown() {
		return false;
	}

	@Override
	public VoidFuture shutdown() {
		throw new UnsupportedOperationException("DirectExecutor is unstoppable");
	}

	@Override
	public void shutdownNow() {
		throw new UnsupportedOperationException("DirectExecutor is unstoppable");
	}
}

