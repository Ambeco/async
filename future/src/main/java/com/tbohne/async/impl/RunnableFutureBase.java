package com.tbohne.async.impl;

import com.tbohne.async.RunnableFuture;
import com.tbohne.async.VoidFuture;
import com.tbohne.async.VoidFuture.FutureProducer;

import java.util.List;
import java.util.concurrent.CancellationException;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;

public abstract class RunnableFutureBase<R> extends SettableFutureStep<R>
		implements RunnableFuture {

	public static final FutureProducer<Void> NO_OP_VOID_CALLBACK = new FutureProducer<Void>(){
		@Override
		public Void onSuccess() {
			return null;
		}

		@Override
		public Void onFailure(RuntimeException t) {
			throw t;
		}
	};

	private boolean started;
	private Thread thread;

	protected boolean isStarted() {
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
			R result;
			try {
				result = execute();
			} finally {
				synchronized (lock) {
					thread = null;
				}
			}
			setResult(result);
		} catch (RuntimeException e) {
			setFailed(e);
		} catch (InterruptedException e) {
			setFailed(new RuntimeException(e));
		}
	}

	@Override
	public boolean cancel(CancellationException exception) {
		if (exception == null) {
			exception = new CancellationException("cancelled");
		}
		if (!setFailed(exception)){
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
	public VoidFuture childrenCannotCancel() {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(this);
		return step;
	}
}
