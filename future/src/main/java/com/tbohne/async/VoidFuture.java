package com.tbohne.async;

public interface VoidFuture extends Future {
	VoidFuture then(Executor executor, FutureEffect followup);

	<T> ValueFuture<T> then(Executor executor, FutureProducer<T> followup);

	interface FutureEffect {
		void onSuccess();

		void onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	interface FutureProducer<R> {
		R onSuccess();

		R onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	abstract class SimpleFutureProducer<R> implements FutureProducer<R> {
		public R onFailure(RuntimeException t) {
			throw t;
		}
	}
}
