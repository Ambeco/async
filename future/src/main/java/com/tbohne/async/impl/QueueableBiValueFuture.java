package com.tbohne.async.impl;

import com.tbohne.async.BiValueFuture;
import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.FutureResult;
import com.tbohne.async.PrereqStrategy;
import com.tbohne.async.TaskCallbacks.BiConsumerTask;
import com.tbohne.async.TaskCallbacks.BiTransformerTask;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;

import java.util.function.BiFunction;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;
import static com.tbohne.async.impl.FutureProducers.NO_OP_VOID_CALLBACK;

public class QueueableBiValueFuture<T, U> extends QueueableFutureTask<Void>
		implements BiValueFuture<T, U> {
	private final ValueFuture<T> first;
	private final ValueFuture<U> second;

	public QueueableBiValueFuture(ValueFuture<T> first, ValueFuture<U> second) {
		super(PrereqStrategy.ALL_PREREQS_SUCCEED,
				toSet(first, second),
				getDirectExecutor(),
				NO_OP_VOID_CALLBACK);
		this.first = first;
		this.second = second;
	}

	@Override
	public ValueFuture<T> getFirst() {
		return first;
	}

	@Override
	public ValueFuture<U> getSecond() {
		return second;
	}

	@Override
	public <R> ValueFuture<R> thenDo(Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> followup) {
		return Combine.thenDo(this, executor, followup);
	}

	@Override
	public <R> ValueFuture<R> thenDo(Executor executor, BiTransformerTask<T, U, R> followup) {
		return Combine.thenDo(this, executor, followup);
	}

	@Override
	public VoidFuture thenDo(Executor executor, BiConsumerTask<T, U> followup) {
		return Combine.thenDo(this, executor, followup);
	}
}