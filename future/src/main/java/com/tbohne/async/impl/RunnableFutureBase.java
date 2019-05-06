package com.tbohne.async.impl;

import com.tbohne.async.Future;
import com.tbohne.async.RunnableFuture;
import com.tbohne.async.VoidFuture;
import com.tbohne.async.VoidFuture.FutureListener;
import com.tbohne.async.VoidFuture.FutureProducer;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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

	static final Iterator<FutureListener> NO_CALLBACKS_ITERATOR =
			new CopyOnWriteArrayList<FutureListener>().iterator();

	protected boolean started;
	private Thread thread;
	private boolean cancelled;

	protected abstract R execute() throws InterruptedException;

	@Override
	public final void run() {
		try {
			R tempResult = null;
			RuntimeException tempException = null;
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
				tempResult = execute();
			} catch (RuntimeException e) {
				tempException = e;
			} catch (InterruptedException e) {
				tempException = new RuntimeException(e);
			}
			Iterator<FutureListener> callbackIterator;
			synchronized (lock) {
				outputThrowable = tempException;
				result = tempResult;
				completed = true;
				//grab a snapshot of the callbacks at the moment we finish
				if (callbacks != null) {
					callbackIterator = callbacks.iterator();
				} else {
					callbackIterator = NO_CALLBACKS_ITERATOR;
				}
			}
			while (callbackIterator.hasNext()) {
				if (outputThrowable == null) {
					callbackIterator.next().onSuccess();
				} else {
					callbackIterator.next().onFailure(outputThrowable);
				}
			}
		} finally {
			synchronized (lock) {
				thread = null;
				callbacks = null;
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
			if (completed) {
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
		boolean noCallbacks = false;
		synchronized (lock) {
			callbacks.remove(callback);
			noCallbacks = callbacks.isEmpty();
		}
		if (noCallbacks) {
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
	public <T extends Future & FutureListener> T then(T followup) {
		if (!isPrerequisite(followup)) {
			throw new IllegalStateException("this must already be a prerequisite for followup before calling 'then'");
		}
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
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(Collections.singletonList(this));
		return step;
	}
}
