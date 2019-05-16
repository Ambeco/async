package com.tbohne.async.impl;

import com.tbohne.async.BiValueFuture;
import com.tbohne.async.Executor;
import com.tbohne.async.FutureResult;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;

import java.util.Collections;

import java.util.function.BiFunction;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;

public class BiValueFutureStep<T,U> extends FutureStep<Void> implements BiValueFuture<T,U> {
	private final ValueFuture<T> first;
	private final ValueFuture<U> second;

	public BiValueFutureStep(ValueFuture<T> first, ValueFuture<U> second) {
		super(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		this.first = first;
		this.second = second;
	}

	@Override
	public ValueFuture<T> getFirst() {
		return first;
	}

	@Override
	public ValueFuture<U> getSecond() {
		return second;
	}

	@Override
	public <R> ValueFuture<R> then(Executor executor, BiFunction<FutureResult<T>, FutureResult<U>, R> followup) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor, new VoidFuture.FutureProducer<R>() {
			@Override
			public R onSuccess() {
				return followup.apply(first, second);
			}

			@Override
			public R onFailure(RuntimeException t) {
				return followup.apply(first, second);
			}
		});
		step.setPrerequisites(Collections.singletonList(this), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	@Override
	public <R> ValueFuture<R> then(Executor executor, BiFutureTransformer<T, U, R> followup) {
		ValueFutureStep<R> step = new ValueFutureStep<>(executor, new VoidFuture.FutureProducer<R>() {
			@Override
			public R onSuccess() {
				return followup.onSuccess(first.getNow(), second.getNow());
			}

			@Override
			public R onFailure(RuntimeException t) {
				return followup.onFailure(t);
			}
		});
		step.setPrerequisites(Collections.singletonList(this), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	@Override
	public VoidFuture then(Executor executor, BiFutureConsumer<T, U> followup) {
		VoidFutureStep step = new VoidFutureStep(executor, new VoidFuture.FutureProducer<Void>() {
			@Override
			public Void onSuccess() {
				followup.onSuccess(first.getNow(), second.getNow());
				return null;
			}

			@Override
			public Void onFailure(RuntimeException t) {
				followup.onFailure(t);
				return null;
			}
		});
		step.setPrerequisites(Collections.singletonList(this), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	@Override
	public BiValueFuture<T, U> andAfter(VoidFuture other) {
		BiValueFutureStep<T,U> step = new BiValueFutureStep<>(first, second);
		step.setPrerequisites(this, other);
		return step;
	}
}