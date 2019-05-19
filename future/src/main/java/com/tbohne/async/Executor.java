package com.tbohne.async;

import java.util.function.Supplier;

public interface Executor {
	void submit(RunnableFuture runnable);

	VoidFuture submit(Runnable runnable);

	<T> ValueFuture<T> submit(Supplier<T> runnable);

	boolean isShuttingDown();

	VoidFuture shutdown();

	void shutdownNow() throws InterruptedException;
}
