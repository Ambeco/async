package com.tbohne.async.impl;

import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.Future;
import com.tbohne.async.FutureResult;
import com.tbohne.async.PrereqStrategy;
import com.tbohne.async.TaskCallbacks.ConsumerTask;
import com.tbohne.async.TaskCallbacks.ProducerTask;
import com.tbohne.async.TaskCallbacks.TransformerTask;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class QueueableValueFuture<R> extends QueueableFutureTask<R> implements ValueFuture<R> {

	public QueueableValueFuture(PrereqStrategy prereqStrategy,
			Set<Future> prerequisites,
			Executor executor,
			ProducerTask<R> function) {
		super(prereqStrategy, prerequisites, executor, function);
	}

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
