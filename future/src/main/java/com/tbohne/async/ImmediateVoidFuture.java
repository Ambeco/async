package com.tbohne.async;

import com.tbohne.async.impl.BiValueFutureStep;
import com.tbohne.async.impl.FutureStep;
import com.tbohne.async.impl.FutureStep.PrereqStrategy;
import com.tbohne.async.impl.ValueFutureStep;
import com.tbohne.async.impl.VoidFutureStep;

import java.util.Collections;
import java.util.List;

import static com.tbohne.async.DirectExecutor.getDirectExecutor;
import static com.tbohne.async.impl.FutureStep.NO_OP_VOID_CALLBACK;

public class ImmediateVoidFuture implements VoidFuture {
	private static final ImmediateVoidFuture instance = new ImmediateVoidFuture();

	public static VoidFuture getImmediateVoidFuture() {
		return instance;
	}

	private ImmediateVoidFuture() {
	}

	@Override
	public VoidFuture then(Executor executor, FutureListener followup) {
		return Async.start(executor, followup::onSuccess);
	}

	@Override
	public <T> ValueFuture<T> then(Executor executor, FutureProducer<T> followup) {
		return Async.start(executor, followup::onSuccess);
	}

	@Override
	public <T extends Future & FutureListener> T then(T followup) {
		followup.onSuccess();
		return followup;
	}

	@Override
	public VoidFuture andAfter(VoidFuture other) {
		VoidFutureStep step = new VoidFutureStep(getDirectExecutor(), NO_OP_VOID_CALLBACK);
		step.setPrerequisites(Collections.singletonList(other), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	@Override
	public <R> ValueFuture<R> andAfter(ValueFuture<R> other) {
		ValueFutureStep<R> step = new ValueFutureStep<>(getDirectExecutor(), new FutureProducer<R>() {
			@Override
			public R onSuccess() {
				return other.getNow();
			}

			@Override
			public R onFailure(RuntimeException t) {
				throw t;
			}
		});
		step.setPrerequisites(Collections.singletonList(other), PrereqStrategy.ALL_PREREQS_COMPLETE);
		return step;
	}

	@Override
	public <T, U> BiValueFuture<T, U> andAfter(BiValueFuture<T, U> other) {
		BiValueFutureStep<T,U> step = new BiValueFutureStep<>(other.getFirst(), other.getSecond());
		step.setPrerequisites(Collections.singletonList(other), PrereqStrategy.ALL_PREREQS_COMPLETE);
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
	public void callbackWasCancelled(FutureListener callback) {
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
		return this;
	}
}
