package com.tbohne.asynclocked.impl;

import com.tbohne.asynclocked.FutureResult;
import com.tbohne.asynclocked.TaskCallbacks.BiConsumerTask;
import com.tbohne.asynclocked.TaskCallbacks.BiTransformerTask;
import com.tbohne.asynclocked.TaskCallbacks.ConsumerTask;
import com.tbohne.asynclocked.TaskCallbacks.ProducerTask;
import com.tbohne.asynclocked.TaskCallbacks.SideEffectTask;
import com.tbohne.asynclocked.TaskCallbacks.SimpleProducerTask;
import com.tbohne.asynclocked.TaskCallbacks.TransformerTask;
import com.tbohne.asynclocked.ValueFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public class FutureProducers {
	public static final ProducerTask<Void> NO_OP_VOID_CALLBACK = new ProducerTask<Void>() {
		@Override
		public Void onSuccess() {
			return null;
		}

		@Override
		public Void onFailure(RuntimeException t) {
			throw t;
		}
	};

	private FutureProducers() {}

	public static class FutureEffectAsFutureProducer implements ProducerTask<Void> {
		private final SideEffectTask listener;

		public FutureEffectAsFutureProducer(SideEffectTask listener) {
			this.listener = listener;
		}

		@Override
		public Void onSuccess() {
			listener.onSuccess();
			return null;
		}

		@Override
		public Void onFailure(RuntimeException t) {
			listener.onFailure(t);
			return null;
		}
	}

	public static class ValueConsumerAsFutureProducer<T> implements ProducerTask<Void> {
		private final FutureResult<T> result;
		private final ConsumerTask<T> listener;

		public ValueConsumerAsFutureProducer(FutureResult<T> result, ConsumerTask<T> listener) {
			this.result = result;
			this.listener = listener;
		}

		@Override
		public Void onSuccess() {
			listener.onSuccess(result.getNow());
			return null;
		}

		@Override
		public Void onFailure(RuntimeException t) {
			listener.onFailure(t);
			return null;
		}
	}

	public static class ConsumerAsFutureProducer<T> implements ProducerTask<Void> {
		private final FutureResult<T> result;
		private final Consumer<FutureResult<T>> listener;

		public ConsumerAsFutureProducer(FutureResult<T> result,
				Consumer<FutureResult<T>> listener) {
			this.result = result;
			this.listener = listener;
		}

		@Override
		public Void onSuccess() {
			listener.accept(result);
			return null;
		}

		@Override
		public Void onFailure(RuntimeException t) {
			listener.accept(result);
			return null;
		}
	}

	public static class ValueTransformerAsFutureProducer<T, R> implements ProducerTask<R> {
		private final FutureResult<T> result;
		private final TransformerTask<T, R> listener;

		public ValueTransformerAsFutureProducer(FutureResult<T> result,
				TransformerTask<T, R> listener) {
			this.result = result;
			this.listener = listener;
		}

		@Override
		public R onSuccess() {
			return listener.onSuccess(result.getNow());
		}

		@Override
		public R onFailure(RuntimeException t) {
			return listener.onFailure(t);
		}
	}

	public static class FunctionAsFutureProducer<T, R> implements ProducerTask<R> {
		private final FutureResult<T> result;
		private final Function<FutureResult<T>, R> listener;

		public FunctionAsFutureProducer(FutureResult<T> result,
				Function<FutureResult<T>, R> listener) {
			this.result = result;
			this.listener = listener;
		}

		@Override
		public R onSuccess() {
			return listener.apply(result);
		}

		@Override
		public R onFailure(RuntimeException t) {
			return listener.apply(result);
		}
	}

	public static class BiFutureConsumerAsFutureProducer<T, U> implements ProducerTask<Void> {
		private final FutureResult<T> first;
		private final FutureResult<U> second;
		private final BiConsumerTask<T, U> listener;

		public BiFutureConsumerAsFutureProducer(FutureResult<T> first,
				FutureResult<U> second,
				BiConsumerTask<T, U> listener) {
			this.first = first;
			this.second = second;
			this.listener = listener;
		}

		@Override
		public Void onSuccess() {
			listener.onSuccess(first.getNow(), second.getNow());
			return null;
		}

		@Override
		public Void onFailure(RuntimeException t) {
			listener.onFailure(t);
			return null;
		}
	}

	public static class BiConsumerAsFutureProducer<T, U> implements ProducerTask<Void> {
		private final FutureResult<T> first;
		private final FutureResult<U> second;
		private final BiConsumer<FutureResult<T>, FutureResult<U>> listener;

		public BiConsumerAsFutureProducer(FutureResult<T> first,
				FutureResult<U> second,
				BiConsumer<FutureResult<T>, FutureResult<U>> listener) {
			this.first = first;
			this.second = second;
			this.listener = listener;
		}

		@Override
		public Void onSuccess() {
			listener.accept(first, second);
			return null;
		}

		@Override
		public Void onFailure(RuntimeException t) {
			listener.accept(first, second);
			return null;
		}
	}

	public static class BiFutureTransformerAsFutureProducer<T, U, R> implements ProducerTask<R> {
		private final FutureResult<T> first;
		private final FutureResult<U> second;
		private final BiTransformerTask<T, U, R> listener;

		public BiFutureTransformerAsFutureProducer(FutureResult<T> first,
				FutureResult<U> second,
				BiTransformerTask<T, U, R> listener) {
			this.first = first;
			this.second = second;
			this.listener = listener;
		}

		@Override
		public R onSuccess() {
			return listener.onSuccess(first.getNow(), second.getNow());
		}

		@Override
		public R onFailure(RuntimeException t) {
			return listener.onFailure(t);
		}
	}

	public static class BiFunctionAsFutureProducer<T, U, R> implements ProducerTask<R> {
		private final FutureResult<T> first;
		private final FutureResult<U> second;
		private final BiFunction<FutureResult<T>, FutureResult<U>, R> listener;

		public BiFunctionAsFutureProducer(FutureResult<T> first,
				FutureResult<U> second,
				BiFunction<FutureResult<T>, FutureResult<U>, R> listener) {
			this.first = first;
			this.second = second;
			this.listener = listener;
		}

		@Override
		public R onSuccess() {
			return listener.apply(first, second);
		}

		@Override
		public R onFailure(RuntimeException t) {
			return listener.apply(first, second);
		}
	}

	public static class ToListTask<R> extends SimpleProducerTask<List<R>> {
		Collection<ValueFuture<R>> futures;

		public ToListTask(Collection<ValueFuture<R>> futures) {
			this.futures = futures;
		}

		@Override
		public List<R> onSuccess() {
			List<R> results = new ArrayList<>(futures.size());
			for (ValueFuture<R> future : futures) {
				results.add(future.getNow());
			}
			return results;
		}
	}
}
