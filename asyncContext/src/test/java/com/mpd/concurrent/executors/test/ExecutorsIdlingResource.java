package com.mpd.concurrent.executors.test;

import androidx.test.espresso.IdlingResource;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.executors.Executor.AllExecutorListListener;
import com.mpd.concurrent.executors.Executor.ExecutorListener;
import com.mpd.concurrent.futures.SubmittableFuture;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutorsIdlingResource implements IdlingResource, AllExecutorListListener, ExecutorListener {
	private final AtomicInteger idleCheckId = new AtomicInteger(0);

	private volatile ResourceCallback callback;

	public ExecutorsIdlingResource() {
		Executor.allExecutorListeners.add(new WeakReference<>(this));
	}

	@Override public String getName() {
		return "ExecutorsIdlingResource";
	}

	@Override public boolean isIdleNow() {
		int idleCheckId;
		do {
			idleCheckId = this.idleCheckId.incrementAndGet();
			for (WeakReference<Executor> weak : Executor.allExecutors) {
				Executor ex = weak.get();
				if (ex != null && !ex.isIdleNow()) {
					return false;
				}
			}
		} while (idleCheckId == this.idleCheckId.get());
		return true;
	}

	@Override public void registerIdleTransitionCallback(ResourceCallback callback) {
		this.callback = callback;
		if (isIdleNow()) {
			this.callback.onTransitionToIdle();
		}
	}

	@Override public void onNewExecutor(Executor e) {
		e.registerListener(this);
		if (!e.isIdleNow()) {
			idleCheckId.incrementAndGet();
		}
	}

	@Override public void beforeExecute(SubmittableFuture<?> r) {
		ExecutorListener.super.beforeExecute(r);
		idleCheckId.incrementAndGet();
	}

	@Override public void onIdle() {
		if (isIdleNow()) {
			this.callback.onTransitionToIdle();
		}
	}
}
