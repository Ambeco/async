package com.tbohne.async;

import com.tbohne.async.impl.SettableValueFuture;
import com.tbohne.async.impl.SettableVoidFuture;

import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

public abstract class ExecutorBase implements Executor {
	abstract protected void queueRunnable(Runnable runnable) throws RejectedExecutionException;

	@Override
	public void submit(RunnableFuture runnable) throws RejectedExecutionException {
		queueRunnable(runnable);
	}

	@Override
	public VoidFuture submit(Runnable runnable) throws RejectedExecutionException {
		if (runnable instanceof VoidFuture) {
			queueRunnable(runnable);
			return (VoidFuture) runnable;
		}
		SettableVoidFuture future = new SettableVoidFuture();
		Runnable realRunnable = () -> {
			try {
				runnable.run();
				future.setResult();
			} catch (RuntimeException e) {
				future.setFailed(e);
			}
		};
		queueRunnable(realRunnable);
		return future;
	}

	@Override
	public <T> ValueFuture<T> submit(Supplier<T> runnable) throws RejectedExecutionException {
		SettableValueFuture<T> future = new SettableValueFuture<>();
		Runnable realRunnable = () -> {
			try {
				future.setResult(runnable.get());
			} catch (RuntimeException e) {
				future.setFailed(e);
			}
		};
		queueRunnable(realRunnable);
		return future;
	}
}
