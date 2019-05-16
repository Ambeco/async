package com.tbohne.async.impl;

import com.tbohne.async.Future;
import com.tbohne.async.VoidFuture;
import com.tbohne.async.VoidFuture.FutureListener;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class SettableFutureStep<R>
		implements Future {

	private static final Iterator<FutureListener> NO_CALLBACKS_ITERATOR =
			new CopyOnWriteArrayList<FutureListener>().iterator();

	protected final Object lock = new Object();
	private R result;
	private boolean completed;
	private RuntimeException outputThrowable;
	private List<FutureListener> callbacks;

	protected SettableFutureStep() {
	}

	protected boolean isCompleted() {
		return completed;
	}

	protected void setResult(R result) {
		Iterator<FutureListener> callbackIterator;
		synchronized (lock) {
			if (completed) {
				throw new IllegalStateException("Settable future set twice");
			}
			this.completed = true;
			this.result = result;
			//grab a snapshot of the callbacks at the moment we finish
			if (callbacks != null) {
				callbackIterator = callbacks.iterator();
			} else {
				callbackIterator = NO_CALLBACKS_ITERATOR;
			}
			callbacks = null;
		}
		while (callbackIterator.hasNext()) {
			callbackIterator.next().onSuccess();
		}
	}

	public void setFailed(RuntimeException exception) {
		Iterator<FutureListener> callbackIterator;
		synchronized (lock) {
			if (completed) {
				throw new IllegalStateException("Settable future set twice");
			}
			this.completed = true;
			this.outputThrowable = exception;
			//grab a snapshot of the callbacks at the moment we finish
			if (callbacks != null) {
				callbackIterator = callbacks.iterator();
			} else {
				callbackIterator = NO_CALLBACKS_ITERATOR;
			}
			callbacks = null;
		}
		while (callbackIterator.hasNext()) {
			callbackIterator.next().onFailure(exception);
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

	protected boolean removeCallbackGetEmpty(FutureListener callback) {
		synchronized (lock) {
			callbacks.remove(callback);
			return callbacks.isEmpty();
		}
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
