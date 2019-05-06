package com.tbohne.async;

import java.util.function.Supplier;

import static com.tbohne.async.ImmediateVoidFuture.getImmediateVoidFuture;

public class DirectExecutor
		implements Executor {
	private static DirectExecutor instance = new DirectExecutor();
	public static DirectExecutor getDirectExecutor() {
		return instance;
	}

	public DirectExecutor() {}

	private void restoreInterruptState(boolean interrupted) {
		if (interrupted) {
			Thread.currentThread().interrupt();
		} else {
			//noinspection ResultOfMethodCallIgnored
			Thread.interrupted();
		}
	}

	@Override
	public VoidFuture submit(Runnable runnable) {
		boolean interrupted = Thread.interrupted();
		try {
			runnable.run();
			return getImmediateVoidFuture();
		} finally {
			restoreInterruptState(interrupted);
		}
	}

	@Override
	public <T> ValueFuture<T> submit(Supplier<T> runnable) {
		boolean interrupted = Thread.interrupted();
		try {
			return new ImmediateValueFuture<>(runnable.get());
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
	public void shutdownNow() throws InterruptedException {
		throw new UnsupportedOperationException("DirectExecutor is unstoppable");
	}
}

