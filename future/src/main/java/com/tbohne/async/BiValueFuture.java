package com.tbohne.async;

import com.tbohne.async.Listeners.BiFutureConsumer;
import com.tbohne.async.Listeners.BiFutureTransformer;

import java.util.function.BiFunction;

/**
 * Reference to two prior futures, that do not yet have a callback attached.
 * <p>
 * I doubt this is still useful, prefer to attach callbacks immediately, to reduce errors and
 * allocations.
 */
public interface BiValueFuture<T, U> extends Future {
	ValueFuture<T> getFirst();

	ValueFuture<U> getSecond();

	<R> ValueFuture<R> then(Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> followup);

	<R> ValueFuture<R> then(Executor executor, BiFutureTransformer<T, U, R> followup);

	VoidFuture then(Executor executor, BiFutureConsumer<T, U> followup);
}
