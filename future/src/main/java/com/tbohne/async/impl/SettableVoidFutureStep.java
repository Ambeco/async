package com.tbohne.async.impl;

import com.tbohne.async.BiValueFuture;
import com.tbohne.async.Combine;
import com.tbohne.async.Executor;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;

import java.util.Collections;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;
import static com.tbohne.async.impl.FutureStep.NO_OP_VOID_CALLBACK;

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
