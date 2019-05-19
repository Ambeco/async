package com.tbohne.async;

import com.tbohne.async.Listeners.FutureProducer;

public interface VoidFuture extends Future {
	VoidFuture then(Executor executor, Listeners.FutureEffect followup);

	<T> ValueFuture<T> then(Executor executor, FutureProducer<T> followup);
}
