package com.tbohne.asynclocked.impl;

import com.tbohne.asynclocked.Combine;
import com.tbohne.asynclocked.Executor;
import com.tbohne.asynclocked.Future;
import com.tbohne.asynclocked.PrereqStrategy;
import com.tbohne.asynclocked.TaskCallbacks.ProducerTask;
import com.tbohne.asynclocked.TaskCallbacks.SideEffectTask;
import com.tbohne.asynclocked.ValueFuture;
import com.tbohne.asynclocked.VoidFuture;

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
