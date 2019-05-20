package com.tbohne.async.impl;

import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.TaskCallbacks;
import com.tbohne.async.TaskCallbacks.ProducerTask;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;


public class QueueableVoidFuture extends QueueableFutureTask<Void> implements VoidFuture {
	public QueueableVoidFuture(Executor executor, ProducerTask<Void> function) {
		super(executor, function);
	}

	@Override
	public <T> ValueFuture<T> then(Executor executor, ProducerTask<T> followup) {
		return Combine.afterComplete(this, executor, followup);
	}

	public VoidFuture then(Executor executor, TaskCallbacks.SideEffectTask followup) {
		return Combine.afterComplete(this, executor, followup);
	}
}
