package com.tbohne.async;

import com.tbohne.async.TaskCallbacks.ProducerTask;
import com.tbohne.async.TaskCallbacks.SideEffectTask;

public interface VoidFuture extends Future {
	VoidFuture thenDo(Executor executor, SideEffectTask followup);

	<T> ValueFuture<T> thenDo(Executor executor, ProducerTask<T> followup);
}
