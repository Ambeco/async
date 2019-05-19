package com.tbohne.async;

import com.tbohne.async.Listeners.BiFutureConsumer;
import com.tbohne.async.Listeners.BiFutureTransformer;

import java.util.function.BiFunction;

public interface BiValueFuture<T, U> extends Future {
	ValueFuture<T> getFirst();

	ValueFuture<U> getSecond();

	<R> ValueFuture<R> then(Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> followup);

	<R> ValueFuture<R> then(Executor executor, BiFutureTransformer<T, U, R> followup);

	VoidFuture then(Executor executor, BiFutureConsumer<T, U> followup);
}
