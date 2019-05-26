package com.tbohne.async.impl;

import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.FutureResult;
import com.tbohne.async.TaskCallbacks.ConsumerTask;
import com.tbohne.async.TaskCallbacks.TransformerTask;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;

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
