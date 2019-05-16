package com.tbohne.async.impl;

import com.tbohne.async.BiValueFuture;
import com.tbohne.async.Executor;
import com.tbohne.async.FutureResult;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;
import com.tbohne.async.VoidFuture.FutureProducer;

import java.util.Collections;

import java.util.function.Consumer;
import java.util.function.Function;
import static com.tbohne.async.DirectExecutor.getDirectExecutor;

public class ValueFutureStep<R> extends FutureStep<R>
		implements ValueFuture<R> {

	public ValueFutureStep(Executor executor, FutureProducer<R> function) {
		super(executor, function);
	}

	@Override
	public R getNow() {
		return super.getNow();
	}

	@Override
	public VoidFuture thenIgnore() {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(Collections.singletonList(this), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	@Override
	public VoidFuture thenIgnore(Executor executor, VoidFuture.FutureListener followup) {
		VoidFutureStep step = new VoidFutureStep(executor,  new FutureProducer<Void>() {
			@Override
			public Void onSuccess() {
				followup.onSuccess();
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
	public VoidFuture thenIgnore(Executor executor, FutureProducer<Void> followup) {
		VoidFutureStep step = new VoidFutureStep(executor, followup);
		step.setPrerequisites(Collections.singletonList(this), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	@Override
	public VoidFuture then(Executor executor, FutureValueConsumer<R> followup) {
		VoidFutureStep step = new VoidFutureStep(executor, new FutureProducer<Void>() {
			@Override
			public Void onSuccess() {
				followup.onSuccess(getNow());
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
	public VoidFuture then(Executor executor, Consumer<FutureResult<R>> followup) {
		VoidFutureStep step = new VoidFutureStep(executor, new FutureProducer<Void>() {
			@Override
			public Void onSuccess() {
				followup.accept(ValueFutureStep.this);
				return null;
			}

			@Override
			public Void onFailure(RuntimeException t) {
				followup.accept(ValueFutureStep.this);
				return null;
			}
		});
		step.setPrerequisites(Collections.singletonList(this), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	@Override
	public <R2> ValueFuture<R2> then(Executor executor, FutureValueTransformer<R, R2> followup) {
		ValueFutureStep<R2> step = new ValueFutureStep<>(executor, new FutureProducer<R2>() {
			@Override
			public R2 onSuccess() {
				return followup.onSuccess(getNow());
			}

			@Override
			public R2 onFailure(RuntimeException t) {
				return followup.onFailure(t);
			}
		});
		step.setPrerequisites(Collections.singletonList(this), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	@Override
	public <R2> ValueFuture<R2> then(Executor executor, Function<FutureResult<R>, R2> followup) {
		ValueFutureStep<R2> step = new ValueFutureStep<>(executor, new FutureProducer<R2>() {
			@Override
			public R2 onSuccess() {
				return followup.apply(ValueFutureStep.this);
			}

			@Override
			public R2 onFailure(RuntimeException t) {
				return followup.apply(ValueFutureStep.this);
			}
		});
		step.setPrerequisites(Collections.singletonList(this), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	@Override
	public ValueFuture<R> andAfter(VoidFuture other) {
		ValueFutureStep<R> step = new ValueFutureStep<>(getDirectExecutor(), new FutureProducer<R>() {
			@Override
			public R onSuccess() {
				return getNow();
			}

			@Override
			public R onFailure(RuntimeException t) {
				throw t;
			}
		});
		step.setPrerequisites(this, other);
		other.then(getDirectExecutor(), step);
		return step;
	}

	@Override
	public <U> BiValueFuture<R,U> andAfter(ValueFuture<U> other) {
		BiValueFutureStep<R,U> step = new BiValueFutureStep<>(this, other);
		step.setPrerequisites(this, other);
		return step;
	}
}
