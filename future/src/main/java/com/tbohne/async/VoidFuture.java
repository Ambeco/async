package com.tbohne.async;



public interface VoidFuture extends Future {
	VoidFuture then(Executor executor, FutureListener followup);
	<T> ValueFuture<T> then(Executor executor, FutureProducer<T> followup);

	VoidFuture andAfter(VoidFuture other);
	<R> ValueFuture<R> andAfter(ValueFuture<R> other);
	<T,U> BiValueFuture<T,U> andAfter(BiValueFuture<T, U> other);


	interface FutureListener {
		void onSuccess(Future future);
		void onFailure(Future future, RuntimeException t); //common implementation is merely to rethrow to children futures
	}
	interface FutureProducer<R> {
		R onSuccess();
		R onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}
}
