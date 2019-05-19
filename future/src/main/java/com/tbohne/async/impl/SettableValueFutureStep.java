package com.tbohne.async.impl;

import com.tbohne.async.BiValueFuture;
import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.FutureResult;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;

import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;
import static com.tbohne.async.impl.FutureStep.NO_OP_VOID_CALLBACK;

public class SettableValueFutureStep<R> extends SettableFutureStep<R> implements ValueFuture<R> {

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
