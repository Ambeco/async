package com.tbohne.async.impl;

import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.TaskCallbacks.ProducerTask;
import com.tbohne.async.TaskCallbacks.SideEffectTask;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;

public class SettableVoidFuture extends SettableFuture<Void> implements VoidFuture {

	public void setResult() {
		super.setResult(null);
	}

	@Override
	public VoidFuture then(Executor executor, SideEffectTask followup) {
		return Combine.afterComplete(this, executor, followup);
	}

	@Override
	public <T> ValueFuture<T> then(Executor executor, ProducerTask<T> followup) {
		return Combine.afterComplete(this, executor, followup);
	}
}
