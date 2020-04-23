package com.tbohne.asyncatomic;

import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

public interface Executor {
	void submit(RunnableFuture runnable) throws RejectedExecutionException;

	VoidFuture submit(Runnable runnable) throws RejectedExecutionException;

	<T> ValueFuture<T> submit(Supplier<T> runnable) throws RejectedExecutionException;

	boolean isShuttingDown();

	VoidFuture shutdown();

	void shutdownNow() throws InterruptedException;

	interface RejectedExecutionHandler {
		void rejectedExecution(Runnable runnable, ThreadPoolExecutor threadPoolExecutor)
				throws RejectedExecutionException;
	}
}
