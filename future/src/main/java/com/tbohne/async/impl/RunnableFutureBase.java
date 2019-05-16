package com.tbohne.async.impl;

import com.tbohne.async.RunnableFuture;
import com.tbohne.async.VoidFuture;
import com.tbohne.async.VoidFuture.FutureListener;
import com.tbohne.async.VoidFuture.FutureProducer;
import com.tbohne.async.impl.FutureStep.PrereqStrategy;

import java.util.Collections;
import java.util.List;

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
	private boolean cancelled;

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
				thread = Thread.currentThread();
				if (cancelled) {
					throw new InterruptedException("Cancelled");
				}
			}
			setResult(execute());
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
	public boolean isCancelled() {
		synchronized (lock) {
			return cancelled;
		}
	}

	@Override
	public boolean cancel() {
		synchronized (lock) {
			if (isCompleted()) {
				return false;
			}
			cancelled = true;
			if (thread != null) {
				thread.interrupt();
			}
			return true;
		}
	}

	@Override
	public void callbackWasCancelled(FutureListener callback) {
		if (removeCallbackGetEmpty(callback)) {
			cancel();
		}
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
		step.setPrerequisites(Collections.singletonList(this), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}
}
