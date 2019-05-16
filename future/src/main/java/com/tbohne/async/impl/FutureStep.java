package com.tbohne.async.impl;

import com.tbohne.async.Executor;
import com.tbohne.async.Future;
import com.tbohne.async.VoidFuture;
import com.tbohne.async.VoidFuture.FutureListener;
import com.tbohne.async.VoidFuture.FutureProducer;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


import static com.tbohne.async.DirectExecutor.getDirectExecutor;

public class FutureStep<R> extends RunnableFutureBase<R>
		implements FutureListener {

	private static final Iterator<Future> NO_PREREQ_ITERATOR
			= new CopyOnWriteArrayList<Future>().iterator();

	private List<? extends Future> prerequisites;
	private Executor executor;
	private RuntimeException inputThrowable;
	private FutureProducer<R> runnable;

	protected FutureStep(Executor executor, FutureProducer<R> runnable) {
		this.executor = executor;
		this.runnable = runnable;
	}

	public void setPrerequisites(List<? extends Future> prerequisites) {
		synchronized (lock) {
			if (submitted) {
				throw new IllegalStateException("setPrerequisites called after execution queued");
			}
			if (prerequisites == null) {
				throw new NullPointerException("prerequisites cannot be null");
			}
			if (this.prerequisites != null) {
				throw new IllegalStateException("setPrerequisites called twice");
			}
			this.prerequisites = prerequisites;
			for (Future prerequisite : prerequisites) {
				prerequisite.then(this);
			}
		}
	}

	public void setPrerequisites(Future first, Future second) {
		CopyOnWriteArrayList<Future> prereqs = new CopyOnWriteArrayList<>();
		prereqs.add(first);
		prereqs.add(second);
		setPrerequisites(prereqs);
	}

	private boolean allPrereqsFinished() {
		Iterator<? extends Future> prereqIterator;
		synchronized (lock) {
			if (prerequisites != null) {
				prereqIterator = prerequisites.iterator();
			} else {
				prereqIterator = NO_PREREQ_ITERATOR;
		}
		while (prereqIterator.hasNext())
			if (!prereqIterator.next().finished()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void onSuccess() {
		synchronized (lock) {
			if (allPrereqsFinished() && !submitted) {
				executor.submit(this);
				prerequisites = null;
				executor = null;
			}
		}
	}

	@Override
	public void onFailure(RuntimeException exception) {
		synchronized (lock) {
			if (inputThrowable == null) {
				inputThrowable = exception;
			} else {
				inputThrowable.addSuppressed(exception);
			}
			if (!submitted) {
				executor.submit(this);
				submitted = true;
				prerequisites = null;
				executor = null;
				inputThrowable = null;
			}
		}
	}

	@Override
	protected R execute() {
		try {
			if (inputThrowable == null) {
				return runnable.onSuccess();
			} else {
				return runnable.onFailure(inputThrowable);
			}
		} finally {
			synchronized (lock) {
				prerequisites = null;
				executor = null;
				inputThrowable = null;
			}
		}
	}

	@Override
	public boolean cancel() {
		Iterator<? extends Future> prerequisitesIterator;
		boolean result;
		synchronized (lock) {
			result = super.cancel();
			if (prerequisites != null) {
				prerequisitesIterator = prerequisites.iterator();
			} else {
				prerequisitesIterator = NO_PREREQ_ITERATOR;
			}
		}
		while (result && prerequisitesIterator.hasNext()) {
			prerequisitesIterator.next().callbackWasCancelled(this);
		}
		return result;
	}

	@Override
	public void fillStackTraces(List<StackTraceElement[]> stacks) {
		Iterator<? extends Future> prerequisitesIterator;
		synchronized (lock) {
			if (prerequisites != null) {
				prerequisitesIterator = prerequisites.iterator();
			} else {
				prerequisitesIterator = NO_PREREQ_ITERATOR;
			}
		}
		while (prerequisitesIterator.hasNext()) {
			prerequisitesIterator.next().fillStackTraces(stacks);
		}
	}

	@Override
	public boolean isPrerequisite(Future future) {
		return prerequisites.contains(future);
	}

	@Override
	public <T extends Future & FutureListener> T then(T followup) {
		if (!isPrerequisite(followup)) {
			throw new IllegalStateException("this must already be a prerequisite for followup before calling 'then'");
		}
		return super.then(followup);
	}

	@Override
	public VoidFuture childrenCannotCancel() {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(Collections.singletonList(this));
		return step;
	}
}
