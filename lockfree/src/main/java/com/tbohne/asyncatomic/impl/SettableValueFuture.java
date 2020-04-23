package com.tbohne.asyncatomic.impl;

import com.tbohne.asyncatomic.Combine;
import com.tbohne.asyncatomic.Executor;
import com.tbohne.asyncatomic.FutureResult;
import com.tbohne.asyncatomic.TaskCallbacks.ConsumerTask;
import com.tbohne.asyncatomic.TaskCallbacks.TransformerTask;
import com.tbohne.asyncatomic.ValueFuture;
import com.tbohne.asyncatomic.VoidFuture;

import java.util.function.Consumer;
import java.util.function.Function;

public class SettableValueFuture<R> extends SettableFuture<R> implements ValueFuture<R> {

	@Override
	public VoidFuture thenDo(Executor executor, ConsumerTask<R> followup) {
		return Combine.thenDo(this, executor, followup);
	}

	@Override
	public VoidFuture thenDo(Executor executor, Consumer<FutureResult<R>> followup) {
		return Combine.thenDo(this, executor, followup);
	}

	@Override
	public <R2> ValueFuture<R2> thenDo(Executor executor, TransformerTask<R, R2> followup) {
		return Combine.thenDo(this, executor, followup);
	}

	@Override
	public <R2> ValueFuture<R2> thenDo(Executor executor, Function<FutureResult<R>, R2> followup) {
		return Combine.thenDo(this, executor, followup);
	}
}
