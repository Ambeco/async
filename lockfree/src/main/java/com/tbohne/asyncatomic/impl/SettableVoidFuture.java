package com.tbohne.asynclocked.impl;

import com.tbohne.asynclocked.Combine;
import com.tbohne.asynclocked.Executor;
import com.tbohne.asynclocked.TaskCallbacks.ProducerTask;
import com.tbohne.asynclocked.TaskCallbacks.SideEffectTask;
import com.tbohne.asynclocked.ValueFuture;
import com.tbohne.asynclocked.VoidFuture;

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
