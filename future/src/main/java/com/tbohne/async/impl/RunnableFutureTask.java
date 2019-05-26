package com.tbohne.async.impl;

import com.tbohne.async.PrereqStrategy;
import com.tbohne.async.RunnableFuture;

import java.util.List;
import java.util.concurrent.CancellationException;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;
import static com.tbohne.async.impl.FutureProducers.NO_OP_VOID_CALLBACK;

/**
 * Helper class that represents a RunnableFuture. Not particularly useful in itself, except for organization.
 */
public abstract class RunnableFutureTask<R> extends SettableFuture<R> implements RunnableFuture {

	private boolean started;
	private Thread thread;

	protected final boolean isStarted() {
		return started;
	}

	protected abstract R execute() throws InterruptedException;

	@Override
	public final void run() {
		try {
			synchronized (lock) {
				if (started) {
					throw new IllegalStateException("RunnableFuture run twice");
				}
				started = true;
				if (isCompleted()) { //already processed an exception, usually a cancellation
					return;
				}
				thread = Thread.currentThread();
			}
			R result = execute();
			setResult(result);
		} catch (RuntimeException e) {
			setFailed(e);
		} catch (InterruptedException e) {
			setFailed(new RuntimeException(e));
		} finally {
			synchronized (lock) {
				thread = null;
			}
		}
	}

	@Override
	public boolean cancel(CancellationException exception) {
		if (exception == null) {
			exception = new CancellationException("cancelled");
		}
		if (!setFailed(exception)) {
			return false;
		}
		synchronized (lock) {
			if (thread != null) {
				thread.interrupt();
			}
		}
		return true;
	}

	@Override
	public void fillStackTraces(List<StackTraceElement[]> stacks) {
		Thread thread;
		synchronized (lock) {
			thread = this.thread;
		}
		if (thread != null) {
			stacks.add(thread.getStackTrace());
		}
	}

	@Override
	public void childrenCannotCancel() {
		QueueableVoidFuture newChild = new QueueableVoidFuture(PrereqStrategy.ALL_PREREQS_SUCCEED,
				QueueableFutureTask.toSet(this),
				getDirectExecutor(),
				NO_OP_VOID_CALLBACK);
	}
}
