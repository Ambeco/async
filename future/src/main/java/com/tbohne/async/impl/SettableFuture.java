package com.tbohne.async.impl;

import com.tbohne.async.Future;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * Base implementation for all my futures.
 * <p>
 * When completed one way or another, then notifies listeners.
 */
abstract class SettableFuture<R> implements Future {

	protected final Object lock = new Object();
	private R result;
	private boolean completed;
	private RuntimeException outputThrowable;
	private Set<FutureListener> listeners;

	protected SettableFuture() {
	}

	protected final boolean isCompleted() {
		return completed;
	}

	public boolean setResult(R result) {
		return setResults(result, null);
	}

	public boolean setFailed(RuntimeException exception) {
		return setResults(null, exception);
	}

	private boolean setResults(R result, RuntimeException exception) {
		Set<FutureListener> listeners;
		synchronized (lock) {
			if (completed) {
				return false;
			}
			this.completed = true;
			this.result = result;
			this.outputThrowable = exception;
			//grab a snapshot of the listeners at the moment we finish
			listeners = this.listeners;
			this.listeners = null;
		}
		notifyListeners(listeners, outputThrowable);
		return true;
	}

	private void notifyListeners(Set<FutureListener> listeners, RuntimeException outputThrowable) {
		if (this.listeners != null) {
			throw new IllegalStateException(
					"Race condition notifying listeners while listeners member still set");
		}
		RuntimeException fromListenerExceptions = null;
		for (FutureListener listener : listeners) {
			try {
				if (outputThrowable == null) {
					listener.onSuccess(this);
				} else {
					listener.onFailure(this, outputThrowable);
				}
			} catch (RuntimeException e) {
				if (fromListenerExceptions == null) {
					fromListenerExceptions = e;
				} else {
					fromListenerExceptions.addSuppressed(e);
				}
			}
		}
		if (fromListenerExceptions != null) {
			throw fromListenerExceptions;
		}
	}

	@Override
	public final boolean completed() {
		synchronized (lock) {
			return completed;
		}
	}

	@Override
	public final boolean succeeded() {
		synchronized (lock) {
			return completed && outputThrowable == null;
		}
	}

	@Override
	public final boolean isCancelled() {
		return outputThrowable instanceof CancellationException;
	}

	@Override
	public boolean cancel(CancellationException exception) {
		if (!isCompleted()) {
			throw new UnsupportedOperationException("Cannot cancel SettableFuture");
		}
		return false;
	}

	public R getNow() {
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

	public final RuntimeException getThrownException() {
		synchronized (lock) {
			if (!completed) {
				throw new IllegalStateException("async is incomplete");
			}
			return outputThrowable;
		}
	}

	@Override
	public void cancelListener(FutureListener listener, CancellationException exception) {
		boolean cancelThis;
		synchronized (lock) {
			listeners.remove(listener);
			cancelThis = listeners.isEmpty();
		}
		if (cancelThis) {
			cancel(exception);
		}
	}

	@Override
	public void fillStackTraces(List<StackTraceElement[]> stacks) {
	}

	@Override
	public boolean isPrerequisite(Future future) {
		return false;
	}

	@Override
	public Future addListener(FutureListener listener) {
		boolean isComplete;
		synchronized (lock) {
			if (!completed) {
				if (listeners == null) {
					listeners = new HashSet<>();
				}
				listeners.add(listener);
			}
			isComplete = completed;
		}
		if (isComplete) {
			if (outputThrowable != null) {
				listener.onFailure(this, outputThrowable);
			} else {
				listener.onSuccess(this);
			}
		}
		if (listener instanceof Future) {
			return (Future) listener;
		} else {
			return new ListenerFuture(this, listener);
		}
	}

	@Override
	public void childrenCannotCancel() {
		throw new UnsupportedOperationException("Cannot cancel SettableFuture");
	}

	private static class ListenerFuture implements Future {
		private final Future attachedTo;
		private final FutureListener listener;
		private boolean cancelled;

		ListenerFuture(Future attachedTo, FutureListener listener) {
			this.attachedTo = attachedTo;
			this.listener = listener;
		}

		@Override
		public boolean completed() {
			return attachedTo.completed();
		}

		@Override
		public boolean succeeded() {
			return attachedTo.completed() && !attachedTo.isCancelled();
		}

		@Override
		public RuntimeException getThrownException() {
			return null;
		}

		@Override
		public boolean isCancelled() {
			return cancelled;
		}

		@Override
		public boolean cancel(CancellationException exception) {
			cancelled = true;
			attachedTo.cancelListener(listener, exception);
			return cancelled;
		}

		@Override
		public void cancelListener(FutureListener listener, CancellationException exception) {
			// meaningless when there cannot be children
		}

		@Override
		public void fillStackTraces(List<StackTraceElement[]> stacks) {
			attachedTo.fillStackTraces(stacks);
		}

		@Override
		public boolean isPrerequisite(Future future) {
			return false;
		}

		@Override
		public Future addListener(FutureListener followup) {
			throw new IllegalArgumentException("Cannot attach listeners to a listener callback");
		}

		@Override
		public void childrenCannotCancel() {
			// meaningless when there cannot be children
		}
	}
}
