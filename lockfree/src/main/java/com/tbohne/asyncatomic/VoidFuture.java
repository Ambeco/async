package com.tbohne.asyncatomic;

import com.tbohne.asyncatomic.TaskCallbacks.ProducerTask;
import com.tbohne.asyncatomic.TaskCallbacks.SideEffectTask;

public interface VoidFuture extends Future {
	VoidFuture thenDo(Executor executor, SideEffectTask followup);

	<T> ValueFuture<T> thenDo(Executor executor, ProducerTask<T> followup);
}
