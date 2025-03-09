package com.mpd.concurrent.executors.test;

import androidx.test.espresso.IdlingResource;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.executors.Executor.AllExecutorsIdleListener;
import java.lang.ref.WeakReference;

public class ExecutorsIdlingResource implements IdlingResource, AllExecutorsIdleListener {
	private volatile ResourceCallback callback;

	public ExecutorsIdlingResource() {
		Executor.allExecutorsIdleListeners.add(new WeakReference<>(this));
	}

	@Override public String getName() {
		return "ExecutorsIdlingResource";
	}

	@Override public boolean isIdleNow() {
		return Executor.nonIdleExecutorCount.get() == 0;
	}

	@Override public void registerIdleTransitionCallback(ResourceCallback callback) {
		this.callback = callback;
		if (isIdleNow()) {
			this.callback.onTransitionToIdle();
		}
	}

	@Override public void onIdle() {
		if (isIdleNow()) {
			this.callback.onTransitionToIdle();
		}
	}
}
