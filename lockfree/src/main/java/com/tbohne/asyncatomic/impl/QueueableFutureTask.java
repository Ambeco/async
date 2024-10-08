package com.tbohne.asyncatomic.impl;

import com.tbohne.asyncatomic.Executor;
import com.tbohne.asyncatomic.Future;
import com.tbohne.asyncatomic.Future.FutureListener;
import com.tbohne.asyncatomic.PrereqStrategy;
import com.tbohne.asyncatomic.TaskCallbacks.ProducerTask;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

/**
 * Future implementation that can wait for prerequisites to be satisfied before queing itself
 */
abstract public class QueueableFutureTask<R> extends RunnableFutureTask<R>
		implements FutureListener {
	public static final Set<Future> NO_PREREQS = Collections.emptySet();

	private Set<Future> prerequisites;
	private PrereqStrategy prereqStrategy;
	private Executor executor;
	private boolean submitted;
	private RuntimeException inputThrowable;
	private ProducerTask<R> runnable;


	protected QueueableFutureTask(PrereqStrategy prereqStrategy,
			Set<Future> prerequisites,
			Executor executor,
			ProducerTask<R> runnable) {
		this.executor = executor;
		this.runnable = runnable;
		this.prerequisites = prerequisites;
		this.prereqStrategy = prereqStrategy;
		for (Future prerequisite : prerequisites) {
			prerequisite.addListener(this);
		}
		if (prereqStrategy == null) {
			throw new NullPointerException("prereqStrategy cannot be null");
		}
	}

	public static Set<Future> toSet(Future only) {
		HashSet<Future> prereqs = new HashSet<>(1);
		prereqs.add(only);
		return prereqs;
	}

	public static Set<Future> toSet(Future first, Future second) {
		HashSet<Future> prereqs = new HashSet<>(2);
		prereqs.add(first);
		prereqs.add(second);
		return prereqs;
	}

	public static Set<Future> toSet(Future[] futures) {
		HashSet<Future> prerequisites = new HashSet<>(futures.length);
		Collections.addAll(prerequisites, futures);
		return prerequisites;
	}

	protected final boolean isSubmitted() {
		return submitted;
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
					"this must already be a prerequisite before calling 'thenDo'");
		}
		return super.addListener(listener);
	}

}
