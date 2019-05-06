package com.tbohne.async;

import com.tbohne.async.VoidFuture.FutureProducer;
import com.tbohne.async.impl.BiValueFutureStep;
import com.tbohne.async.impl.ValueFutureStep;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;
import static com.tbohne.async.ImmediateVoidFuture.getImmediateVoidFuture;

public class ImmediateValueFuture<R>
		implements ValueFuture<R> {
	private final R result;

	public ImmediateValueFuture(R result) {
		this.result = result;
	}

	@Override
	public VoidFuture thenIgnore() {
		return getImmediateVoidFuture();
	}

	@Override
	public VoidFuture thenIgnore(Executor executor, VoidFuture.FutureListener followup) {
		return Async.start(executor, (Runnable) followup::onSuccess);
	}

	@Override
	public VoidFuture thenIgnore(Executor executor, FutureProducer<Void> followup) {
		return Async.start(executor, (Runnable) followup::onSuccess);
	}

	@Override
	public VoidFuture then(Executor executor, FutureValueConsumer<R> followup) {
		return Async.start(executor, ()->followup.onSuccess(result));
	}

	@Override
	public VoidFuture then(Executor executor, Consumer<FutureResult<R>> followup) {
		return Async.start(executor, ()->followup.accept(this));
	}

	@Override
	public <R2> ValueFuture<R2> then(Executor executor, FutureValueTransformer<R, R2> followup) {
		return Async.start(executor, ()->followup.onSuccess(result));
	}

	@Override
	public <R2> ValueFuture<R2> then(Executor executor, Function<FutureResult<R>, R2> followup) {
		return Async.start(executor, ()->followup.apply(this));
	}

	@Override
	public <T extends Future & VoidFuture.FutureListener> T then(T followup) {
		followup.onSuccess();
		return followup;
	}

	@Override
	public ValueFuture<R> andAfter(VoidFuture other) {
		ValueFutureStep<R> step = new ValueFutureStep<R>(getDirectExecutor(), new FutureProducer<R>() {
			@Override
			public R onSuccess() {
				return result;
			}

			@Override
			public R onFailure(RuntimeException t) {
				throw t;
			}
		});
		step.setPrerequisites(Collections.singletonList(other));
		return step;
	}

	@Override
	public <U> BiValueFuture<R, U> andAfter(ValueFuture<U> other) {
		BiValueFutureStep<R, U> step = new BiValueFutureStep<>(this, other);
		step.setPrerequisites(Collections.singletonList(other));
		return step;
	}

	@Override
	public boolean finished() {
		return true;
	}

	@Override
	public boolean succeeded() {
		return true;
	}

	public boolean failed() {
		return false;
	}

	@Override
	public R getNow() {
		return result;
	}

	@Override
	public RuntimeException getThrownException() {
		return null;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public boolean cancel() {
		return false;
	}

	@Override
	public void callbackWasCancelled(VoidFuture.FutureListener callback) {

	}

	@Override
	public void fillStackTraces(List<StackTraceElement[]> stacks) {

	}

	@Override
	public boolean isPrerequisite(Future future) {
		return false;
	}

	@Override
	public VoidFuture childrenCannotCancel() {
		return getImmediateVoidFuture();
	}
}
