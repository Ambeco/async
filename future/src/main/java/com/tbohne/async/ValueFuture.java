package com.tbohne.async;

import com.tbohne.async.TaskCallbacks.ConsumerTask;
import com.tbohne.async.TaskCallbacks.TransformerTask;

import java.util.function.Consumer;
import java.util.function.Function;

public interface ValueFuture<R> extends Future, FutureResult<R> {
	VoidFuture thenDo(Executor executor, ConsumerTask<R> followup);

	VoidFuture thenDo(Executor executor, Consumer<FutureResult<R>> followup);

	<R2> ValueFuture<R2> thenDo(Executor executor, TransformerTask<R, R2> followup);

	<R2> ValueFuture<R2> thenDo(Executor executor, Function<FutureResult<R>, R2> followup);
}
