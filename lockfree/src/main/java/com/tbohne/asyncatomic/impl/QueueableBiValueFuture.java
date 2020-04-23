package com.tbohne.asyncatomic.impl;

import com.tbohne.asyncatomic.BiValueFuture;
import com.tbohne.asyncatomic.Combine;
import com.tbohne.asyncatomic.Executor;
import com.tbohne.asyncatomic.FutureResult;
import com.tbohne.asyncatomic.PrereqStrategy;
import com.tbohne.asyncatomic.TaskCallbacks.BiConsumerTask;
import com.tbohne.asyncatomic.TaskCallbacks.BiTransformerTask;
import com.tbohne.asyncatomic.ValueFuture;
import com.tbohne.asyncatomic.VoidFuture;

import java.util.function.BiFunction;

import static com.tbohne.asyncatomic.DirectExecutor.getDirectExecutor;
import static com.tbohne.asyncatomic.impl.FutureProducers.NO_OP_VOID_CALLBACK;

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