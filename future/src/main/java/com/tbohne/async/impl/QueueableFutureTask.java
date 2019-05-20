package com.tbohne.async.impl;

import com.tbohne.async.Executor;
import com.tbohne.async.Future;
import com.tbohne.async.Future.FutureListener;
import com.tbohne.async.PrereqStrategy;
import com.tbohne.async.TaskCallbacks.ProducerTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * Future implementation that can wait for prerequisites to be satisfied before queing itself
 */
abstract public class QueueableFutureTask<R> extends RunnableFutureTask<R>
		implements FutureListener {

	private Set<Future> prerequisites;
	private PrereqStrategy prereqStrategy;
	private Executor executor;
	private boolean submitted;
	private RuntimeException inputThrowable;
	private ProducerTask<R> runnable;

	protected QueueableFutureTask(Executor executor, ProducerTask<R> runnable) {
		this.executor = executor;
		this.runnable = runnable;
	}

	protected final boolean isSubmitted() {
		return submitted;
	}

	public void setPrerequisites(Set<Future> prerequisites, PrereqStrategy prereqStrategy) {
		synchronized (lock) {
			if (submitted) {
				throw new IllegalStateException("setPrerequisites called after execution queued");
			}
			if (this.prereqStrategy != null) {
				throw new IllegalStateException("setPrerequisites called twice");
			}
			if (prerequisites == null) {
				throw new NullPointerException("prerequisites cannot be null");
			}
			if (prereqStrategy == null) {
				throw new NullPointerException("prereqStrategy cannot be null");
			}
			this.prerequisites = prerequisites;
			this.prereqStrategy = prereqStrategy;
		}
		for (Future prerequisite : prerequisites) {
			prerequisite.addListener(this);
		}
	}

	public void setPrerequisites(Future only) {
		HashSet<Future> prereqs = new HashSet<>(1);
		prereqs.add(only);
		setPrerequisites(prereqs, PrereqStrategy.ALL_PREREQS_COMPLETE);
	}

	public void setPrerequisites(Future first, Future second, PrereqStrategy prereqStrategy) {
		HashSet<Future> prereqs = new HashSet<>(2);
		prereqs.add(first);
		prereqs.add(second);
		setPrerequisites(prereqs, prereqStrategy);
	}

	@Override
	public void onSuccess(Future future) {
		Executor executor = null;
		synchronized (lock) {
			if (submitted) {
				return;
			}
			if (prereqStrategy == null || future == null || prereqStrategy.areReady(prerequisites,
					future,
					true)) {
				prerequisites = null;
				executor = this.executor;
				this.executor = null;
				submitted = true;
			}
		}
		if (executor != null) {
			executor.submit(this);
		}
	}

	@Override
	public void onFailure(Future future, RuntimeException exception) {
		Executor executor = null;
		synchronized (lock) {
			if (submitted) {
				return;
			}
			if (inputThrowable == null) {
				inputThrowable = exception;
			} else {
				inputThrowable.addSuppressed(exception);
			}
			if (prereqStrategy == null || prereqStrategy.areReady(prerequisites, future, true)) {
				prerequisites = null;
				executor = this.executor;
				this.executor = null;
				submitted = true;
			}
		}
		if (executor != null) {
			executor.submit(this);
		}
	}

	@Override
	protected R execute() {
		RuntimeException inputThrowable;
		synchronized (lock) {
			inputThrowable = this.inputThrowable;
			this.inputThrowable = null;
		}
		if (inputThrowable == null) {
			return runnable.onSuccess();
		} else {
			return runnable.onFailure(inputThrowable);
		}
	}

	@Override
	public boolean cancel(CancellationException exception) {
		if (!super.cancel(exception)) {
			return false;
		}
		Set<Future> prerequisites;
		synchronized (lock) {
			prerequisites = this.prerequisites;
			this.prerequisites = null;
		}
		if (prerequisites == null) {
			return true;
		}
		for (Future future : prerequisites) {
			future.cancelListener(this, exception);
		}
		return true;
	}

	@Override
	public void fillStackTraces(List<StackTraceElement[]> stacks) {
		super.fillStackTraces(stacks);
		Set<Future> prereqCopy;
		synchronized (lock) {
			if (prerequisites == null) {
				return;
			}
			prereqCopy = new HashSet<>(prerequisites);
		}
		for (Future prereq : prereqCopy) {
			prereq.fillStackTraces(stacks);
		}
	}

	@Override
	public boolean isPrerequisite(Future future) {
		return prerequisites.contains(future);
	}

	@Override
	public Future addListener(FutureListener listener) {
		if (listener instanceof Future && !isPrerequisite((Future) listener)) {
			throw new IllegalStateException(
					"this must already be a prerequisite before calling 'then'");
		}
		return super.addListener(listener);
	}

}
