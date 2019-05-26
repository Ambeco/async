package com.tbohne.async.impl;

import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.Future;
import com.tbohne.async.PrereqStrategy;
import com.tbohne.async.TaskCallbacks.ProducerTask;
import com.tbohne.async.TaskCallbacks.SideEffectTask;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;

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
