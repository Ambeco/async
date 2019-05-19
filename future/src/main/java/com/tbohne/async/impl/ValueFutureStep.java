package com.tbohne.async.impl;

import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.FutureResult;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;
import com.tbohne.async.VoidFuture.FutureProducer;

import java.util.function.Consumer;
import java.util.function.Function;

public class ValueFutureStep<R> extends FutureStep<R> implements ValueFuture<R> {

	public ValueFutureStep(Executor executor, FutureProducer<R> function) {
		super(executor, function);
	}

	@Override
	public VoidFuture then(Executor executor, FutureValueConsumer<R> followup) {
		return Combine.afterComplete(this, executor, followup);
	}

	@Override
	public VoidFuture then(Executor executor, Consumer<FutureResult<R>> followup) {
		return Combine.afterComplete(this, executor, followup);
	}

	@Override
	public <R2> ValueFuture<R2> then(Executor executor, FutureValueTransformer<R, R2> followup) {
		return Combine.afterComplete(this, executor, followup);
	}

	@Override
	public <R2> ValueFuture<R2> then(Executor executor, Function<FutureResult<R>, R2> followup) {
		return Combine.afterComplete(this, executor, followup);
	}
}
