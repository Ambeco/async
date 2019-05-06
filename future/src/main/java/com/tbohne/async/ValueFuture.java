package com.tbohne.async;

import com.tbohne.async.VoidFuture.FutureListener;
import com.tbohne.async.VoidFuture.FutureProducer;


import java.util.function.Consumer;
import java.util.function.Function;

public interface ValueFuture<R> extends Future, FutureResult<R> {
	VoidFuture thenIgnore();
	VoidFuture thenIgnore(Executor executor, FutureListener followup);
	VoidFuture thenIgnore(Executor executor, FutureProducer<Void> followup);

	VoidFuture then(Executor executor, FutureValueConsumer<R> followup);
	VoidFuture then(Executor executor, Consumer<FutureResult<R>> followup);
	<R2> ValueFuture<R2> then(Executor executor, FutureValueTransformer<R, R2> followup);
	<R2> ValueFuture<R2> then(Executor executor, Function<FutureResult<R>, R2> followup);

	ValueFuture<R> andAfter(VoidFuture other);
	<U> BiValueFuture<R, U> andAfter(ValueFuture<U> other);

	interface FutureValueConsumer<T> {
		void onSuccess(T result);
		void onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}
	interface FutureValueTransformer<T,R> {
		R onSuccess(T result);
		R onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}
}
