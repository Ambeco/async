package com.tbohne.async.impl;

import com.tbohne.async.BiValueFuture;
import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;

import java.util.Collections;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;


public class VoidFutureStep
		extends FutureStep<Void>
		implements VoidFuture {
	public VoidFutureStep(Executor executor, FutureProducer<Void> function) {
		super(executor, function);
	}

	@Override
	public <T> ValueFuture<T> then(Executor executor, FutureProducer<T> followup) {
		return Combine.afterComplete(this, executor, followup);
	}

	public VoidFuture then(Executor executor, FutureEffect followup) {
		return Combine.afterComplete(this, executor, followup);
	}
}
