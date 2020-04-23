package com.tbohne.asyncatomic.impl;

import com.tbohne.asyncatomic.Combine;
import com.tbohne.asyncatomic.Executor;
import com.tbohne.asyncatomic.Future;
import com.tbohne.asyncatomic.PrereqStrategy;
import com.tbohne.asyncatomic.TaskCallbacks.ProducerTask;
import com.tbohne.asyncatomic.TaskCallbacks.SideEffectTask;
import com.tbohne.asyncatomic.ValueFuture;
import com.tbohne.asyncatomic.VoidFuture;

import java.util.Set;


public class QueueableVoidFuture extends QueueableFutureTask<Void> implements VoidFuture {
	public QueueableVoidFuture(PrereqStrategy prereqStrategy,
			Set<Future> prerequisites,
			Executor executor,
			ProducerTask<Void> function) {
		super(prereqStrategy, prerequisites, executor, function);
	}

	@Override
	public <T> ValueFuture<T> thenDo(Executor executor, ProducerTask<T> followup) {
		return Combine.thenDo(this, executor, followup);
	}

	public VoidFuture thenDo(Executor executor, SideEffectTask followup) {
		return Combine.thenDo(this, executor, followup);
	}
}
