package com.tbohne.async;

import java.util.function.BiFunction;

public interface BiValueFuture<T, U> extends Future {
	ValueFuture<T> getFirst();

	ValueFuture<U> getSecond();

	<R> ValueFuture<R> then(Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> followup);

	<R> ValueFuture<R> then(Executor executor, BiFutureTransformer<T, U, R> followup);

	VoidFuture then(Executor executor, BiFutureConsumer<T, U> followup);

	interface BiFutureConsumer<T, U> {
		void onSuccess(T first, U second);

		void onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	interface BiFutureTransformer<T, U, R> {
		R onSuccess(T result, U second);

		R onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	abstract class SimpleBiFutureTransformer<T, U, R> implements BiFutureTransformer<T, U, R> {
		public R onFailure(RuntimeException t) {
			throw t;
		}
	}
}
