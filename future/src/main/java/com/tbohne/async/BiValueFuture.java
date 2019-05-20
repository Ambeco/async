package com.tbohne.async;

import com.tbohne.async.TaskCallbacks.BiConsumerTask;
import com.tbohne.async.TaskCallbacks.BiTransformerTask;

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

	<R> ValueFuture<R> then(Executor executor, BiTransformerTask<T, U, R> followup);

	VoidFuture then(Executor executor, BiConsumerTask<T, U> followup);
}
