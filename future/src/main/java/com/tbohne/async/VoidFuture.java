package com.tbohne.async;

import com.tbohne.async.TaskCallbacks.ProducerTask;
import com.tbohne.async.TaskCallbacks.SideEffectTask;

public interface VoidFuture extends Future {
	VoidFuture then(Executor executor, SideEffectTask followup);

	<T> ValueFuture<T> then(Executor executor, ProducerTask<T> followup);
}
