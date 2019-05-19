package com.tbohne.async.impl;

import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.Listeners;
import com.tbohne.async.Listeners.FutureEffect;
import com.tbohne.async.Listeners.FutureProducer;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;

public class SettableVoidFutureStep extends SettableFutureStep<Void> implements VoidFuture {

	public void setResult() {
		super.setResult(null);
	}

	@Override
	public VoidFuture then(Executor executor, FutureEffect followup) {
		return Combine.afterComplete(this, executor, followup);
	}

	@Override
	public <T> ValueFuture<T> then(Executor executor, FutureProducer<T> followup) {
		return Combine.afterComplete(this, executor, followup);
	}
}
