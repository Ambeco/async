package com.tbohne.async;

import com.tbohne.async.Listeners.FutureValueConsumer;
import com.tbohne.async.Listeners.FutureValueTransformer;

import java.util.function.Consumer;
import java.util.function.Function;

public interface ValueFuture<R> extends Future, FutureResult<R> {
	VoidFuture then(Executor executor, FutureValueConsumer<R> followup);

	VoidFuture then(Executor executor, Consumer<FutureResult<R>> followup);

	<R2> ValueFuture<R2> then(Executor executor, FutureValueTransformer<R, R2> followup);

	<R2> ValueFuture<R2> then(Executor executor, Function<FutureResult<R>, R2> followup);
}
