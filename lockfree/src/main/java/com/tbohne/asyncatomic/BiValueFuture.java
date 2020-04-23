package com.tbohne.asyncatomic;

import com.tbohne.asyncatomic.TaskCallbacks.BiConsumerTask;
import com.tbohne.asyncatomic.TaskCallbacks.BiTransformerTask;

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

	<R> ValueFuture<R> thenDo(Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> followup);

	<R> ValueFuture<R> thenDo(Executor executor, BiTransformerTask<T, U, R> followup);

	VoidFuture thenDo(Executor executor, BiConsumerTask<T, U> followup);
}
