package com.tbohne.async.impl;

import com.tbohne.async.Future;
import com.tbohne.async.VoidFuture;
import com.tbohne.async.VoidFuture.FutureListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class SettableFutureStep<R>
		implements Future {

	protected final Object lock = new Object();
	protected R result;
	protected boolean completed;
	protected RuntimeException outputThrowable;
	protected List<FutureListener> callbacks;

	protected SettableFutureStep() {
	}

	protected void setResult(R result) {
		synchronized (lock) {
			if (completed) {
				throw new IllegalStateException("Settable future set twice");
			}
			this.completed = true;
			this.result = result;
		}
	}

	public void setFailed(RuntimeException exception) {
		synchronized (lock) {
			if (completed) {
				throw new IllegalStateException("Settable future set twice");
			}
			this.completed = true;
			this.outputThrowable = exception;
		}
	}

	@Override
	public boolean finished() {
		synchronized (lock) {
			return completed;
		}
	}

	@Override
	public boolean succeeded() {
		synchronized (lock) {
			return completed && outputThrowable == null;
		}
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public boolean cancel() {
		throw new UnsupportedOperationException("Cannot cancel SettableFuture");
	}

	protected R getNow() {
		synchronized (lock) {
			if (!completed) {
				throw new IllegalStateException("async is incomplete");
			}
			if (outputThrowable != null) {
				throw outputThrowable;
			}
			return result;
		}
	}

	public RuntimeException getThrownException() {
		synchronized (lock) {
			if (!completed) {
				throw new IllegalStateException("async is incomplete");
			}
			return outputThrowable;
		}
	}

	@Override
	public void callbackWasCancelled(FutureListener callback) {
	}

	@Override
	public void fillStackTraces(List<StackTraceElement[]> stacks) {
	}

	@Override
	public boolean isPrerequisite(Future future) {
		return false;
	}

	@Override
	public <T extends Future & FutureListener> T then(T followup) {
		boolean isComplete;
		synchronized (lock) {
			if (!completed) {
				if (callbacks == null) {
					callbacks = new CopyOnWriteArrayList<>();
				}
				callbacks.add(followup);
			}
			isComplete = completed;
		}
		if (isComplete) {
			if (outputThrowable != null) {
				followup.onFailure(outputThrowable);
			} else {
				followup.onSuccess();
			}
		}
		return followup;
	}

	@Override
	public VoidFuture childrenCannotCancel() {
		throw new UnsupportedOperationException("Cannot cancel SettableFuture");
	}
}
