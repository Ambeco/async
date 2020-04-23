package com.tbohne.asyncatomic.impl;

import com.tbohne.asyncatomic.Combine;
import com.tbohne.asyncatomic.Executor;
import com.tbohne.asyncatomic.TaskCallbacks.ProducerTask;
import com.tbohne.asyncatomic.TaskCallbacks.SideEffectTask;
import com.tbohne.asyncatomic.ValueFuture;
import com.tbohne.asyncatomic.VoidFuture;

public class SettableVoidFuture extends SettableFuture<Void> implements VoidFuture {

	public void setResult() {
		super.setResult(null);
	}

	@Override
	public VoidFuture thenDo(Executor executor, SideEffectTask followup) {
		return Combine.thenDo(this, executor, followup);
	}

	@Override
	public <T> ValueFuture<T> thenDo(Executor executor, ProducerTask<T> followup) {
		return Combine.thenDo(this, executor, followup);
	}
}
