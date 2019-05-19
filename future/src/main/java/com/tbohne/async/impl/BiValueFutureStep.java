package com.tbohne.async.impl;

import com.tbohne.async.BiValueFuture;
import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.FutureResult;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;

import java.util.function.BiFunction;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;
import static com.tbohne.async.impl.FutureProducers.NO_OP_VOID_CALLBACK;

public class BiValueFutureStep<T, U> extends FutureStep<Void> implements BiValueFuture<T, U> {
	private final ValueFuture<T> first;
	private final ValueFuture<U> second;

	public BiValueFutureStep(ValueFuture<T> first, ValueFuture<U> second) {
		super(getDirectExecutor(), NO_OP_VOID_CALLBACK);
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
	public <R> ValueFuture<R> then(Executor executor,
			BiFunction<FutureResult<T>, FutureResult<U>, R> followup) {
		return Combine.afterComplete(this, executor, followup);
	}

	@Override
	public <R> ValueFuture<R> then(Executor executor, BiFutureTransformer<T, U, R> followup) {
		return Combine.afterComplete(this, executor, followup);
	}

	@Override
	public VoidFuture then(Executor executor, BiFutureConsumer<T, U> followup) {
		return Combine.afterComplete(this, executor, followup);
	}
}