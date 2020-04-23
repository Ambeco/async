package com.tbohne.asynclocked;

import com.tbohne.asynclocked.TaskCallbacks.ProducerTask;
import com.tbohne.asynclocked.TaskCallbacks.SideEffectTask;

public interface VoidFuture extends Future {
	VoidFuture thenDo(Executor executor, SideEffectTask followup);

	<T> ValueFuture<T> thenDo(Executor executor, ProducerTask<T> followup);
}
