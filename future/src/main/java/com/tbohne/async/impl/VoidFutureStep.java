package com.tbohne.async.impl;

import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.Listeners;
import com.tbohne.async.Listeners.FutureProducer;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;


public class VoidFutureStep extends FutureStep<Void> implements VoidFuture {
	public VoidFutureStep(Executor executor, FutureProducer<Void> function) {
		super(executor, function);
	}

	@Override
	public <T> ValueFuture<T> then(Executor executor, FutureProducer<T> followup) {
		return Combine.afterComplete(this, executor, followup);
	}

	public VoidFuture then(Executor executor, Listeners.FutureEffect followup) {
		return Combine.afterComplete(this, executor, followup);
	}
}
