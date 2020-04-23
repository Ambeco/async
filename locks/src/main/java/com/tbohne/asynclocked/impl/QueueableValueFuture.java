package com.tbohne.asynclocked.impl;

import com.tbohne.asynclocked.Combine;
import com.tbohne.asynclocked.Executor;
import com.tbohne.asynclocked.Future;
import com.tbohne.asynclocked.FutureResult;
import com.tbohne.asynclocked.PrereqStrategy;
import com.tbohne.asynclocked.TaskCallbacks.ConsumerTask;
import com.tbohne.asynclocked.TaskCallbacks.ProducerTask;
import com.tbohne.asynclocked.TaskCallbacks.TransformerTask;
import com.tbohne.asynclocked.ValueFuture;
import com.tbohne.asynclocked.VoidFuture;

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
