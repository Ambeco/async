package com.tbohne.async.impl;

import com.tbohne.async.BiValueFuture;
import com.tbohne.async.Executor;
import com.tbohne.async.FutureResult;
import com.tbohne.async.ValueFuture;
import com.tbohne.async.VoidFuture;

import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;
import static com.tbohne.async.impl.FutureStep.NO_OP_VOID_CALLBACK;

public class SettableValueFutureStep<R> extends SettableFutureStep<R> implements ValueFuture<R> {

	@Override
	public void setResult(R value) {
		super.setResult(value);
	}

	@Override
	public R getNow() {
		return super.getNow();
	}

	@Override
	public VoidFuture thenIgnore() {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(Collections.singletonList(this));
		return step;
	}

	@Override
	public VoidFuture thenIgnore(Executor executor, VoidFuture.FutureListener followup) {
		VoidFutureStep step = new VoidFutureStep(executor,  new VoidFuture.FutureProducer<Void>() {
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
		step.setPrerequisites(Collections.singletonList(this));
		return step;
	}

	@Override
	public VoidFuture thenIgnore(Executor executor, VoidFuture.FutureProducer<Void> followup) {
		VoidFutureStep step = new VoidFutureStep(executor, followup);
		step.setPrerequisites(Collections.singletonList(this));
		return step;
	}

	@Override
	public VoidFuture then(Executor executor, FutureValueConsumer<R> followup) {
		VoidFutureStep step = new VoidFutureStep(executor, new VoidFuture.FutureProducer<Void>() {
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
		step.setPrerequisites(Collections.singletonList(this));
		return step;
	}

	@Override
	public VoidFuture then(Executor executor, Consumer<FutureResult<R>> followup) {
		VoidFutureStep step = new VoidFutureStep(executor, new VoidFuture.FutureProducer<Void>() {
			@Override
			public Void onSuccess() {
				followup.accept(SettableValueFutureStep.this);
				return null;
			}

			@Override
			public Void onFailure(RuntimeException t) {
				followup.accept(SettableValueFutureStep.this);
				return null;
			}
		});
		step.setPrerequisites(Collections.singletonList(this));
		return step;
	}

	@Override
	public <R2> ValueFuture<R2> then(Executor executor, FutureValueTransformer<R, R2> followup) {
		ValueFutureStep<R2> step = new ValueFutureStep<>(executor, new VoidFuture.FutureProducer<R2>() {
			@Override
			public R2 onSuccess() {
				return followup.onSuccess(getNow());
			}

			@Override
			public R2 onFailure(RuntimeException t) {
				return followup.onFailure(t);
			}
		});
		step.setPrerequisites(Collections.singletonList(this));
		return step;
	}

	@Override
	public <R2> ValueFuture<R2> then(Executor executor, Function<FutureResult<R>, R2> followup) {
		ValueFutureStep<R2> step = new ValueFutureStep<>(executor, new VoidFuture.FutureProducer<R2>() {
			@Override
			public R2 onSuccess() {
				return followup.apply(SettableValueFutureStep.this);
			}

			@Override
			public R2 onFailure(RuntimeException t) {
				return followup.apply(SettableValueFutureStep.this);
			}
		});
		step.setPrerequisites(Collections.singletonList(this));
		return step;
	}

	@Override
	public ValueFuture<R> andAfter(VoidFuture other) {
		ValueFutureStep<R> step = new ValueFutureStep<>(getDirectExecutor(), new VoidFuture.FutureProducer<R>() {
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
