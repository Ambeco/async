package com.tbohne.async;

import java.util.function.Supplier;

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

