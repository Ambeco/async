package com.tbohne.async.impl;

import com.tbohne.async.BiValueFuture;
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
		ValueFutureStep<T> step = new ValueFutureStep<>(executor, followup);
		step.setPrerequisites(this);
		return step;
	}

	public VoidFuture then(Executor executor, FutureListener followup) {
		VoidFutureStep step = new VoidFutureStep(executor, new FutureProducer<Void>() {
			@Override
			public Void onSuccess() {
				followup.onSuccess(VoidFutureStep.this);
				return null;
			}

			@Override
			public Void onFailure(RuntimeException t) {
				followup.onFailure(VoidFutureStep.this, t);
				return null;
			}
		});
		step.setPrerequisites(this);
		return step;
	}

	@Override
	public VoidFuture andAfter(VoidFuture other) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(this, other, FutureStep.PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	@Override
	public <U> ValueFuture<U> andAfter(ValueFuture<U> other) {
		ValueFutureStep<U> step = new ValueFutureStep<>(getDirectExecutor(), new FutureProducer<U>(){
			@Override
			public U onSuccess() {
				return other.getNow();
			}

			@Override
			public U onFailure(RuntimeException t) {
				throw t;
			}
		});
		step.setPrerequisites(this, other, FutureStep.PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}

	@Override
	public <T,U> BiValueFuture<T,U> andAfter(BiValueFuture<T,U> other) {
		BiValueFutureStep<T,U> step = new BiValueFutureStep<>(other.getFirst(), other.getSecond());
		step.setPrerequisites(this, other, FutureStep.PrereqStrategy.ALL_PREREQS_SUCCEED);
		return step;
	}
}
