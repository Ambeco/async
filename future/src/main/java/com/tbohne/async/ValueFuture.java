package com.tbohne.async;

import java.util.function.Consumer;
import java.util.function.Function;

public interface ValueFuture<R> extends Future, FutureResult<R> {
	VoidFuture then(Executor executor, FutureValueConsumer<R> followup);

	VoidFuture then(Executor executor, Consumer<FutureResult<R>> followup);

	<R2> ValueFuture<R2> then(Executor executor, FutureValueTransformer<R, R2> followup);

	<R2> ValueFuture<R2> then(Executor executor, Function<FutureResult<R>, R2> followup);


	interface FutureValueConsumer<T> {
		void onSuccess(T result);

		void onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	interface FutureValueTransformer<T, R> {
		R onSuccess(T result);

		R onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	abstract class SimpleFutureTransformer<T, R> implements FutureValueTransformer<T, R> {
		public R onFailure(RuntimeException t) {
			throw t;
		}
	}
}
