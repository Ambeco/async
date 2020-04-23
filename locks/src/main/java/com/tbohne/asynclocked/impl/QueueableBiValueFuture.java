package com.tbohne.asynclocked.impl;

import com.tbohne.asynclocked.BiValueFuture;
import com.tbohne.asynclocked.Combine;
import com.tbohne.asynclocked.Executor;
import com.tbohne.asynclocked.FutureResult;
import com.tbohne.asynclocked.PrereqStrategy;
import com.tbohne.asynclocked.TaskCallbacks.BiConsumerTask;
import com.tbohne.asynclocked.TaskCallbacks.BiTransformerTask;
import com.tbohne.asynclocked.ValueFuture;
import com.tbohne.asynclocked.VoidFuture;

import java.util.function.BiFunction;

import static com.tbohne.asynclocked.DirectExecutor.getDirectExecutor;
import static com.tbohne.asynclocked.impl.FutureProducers.NO_OP_VOID_CALLBACK;

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