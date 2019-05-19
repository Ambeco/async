package com.tbohne.async;

/**
 * Various listener types that can be attached to futures.
 * For listeners that produce values, there are also simple base classes that merely propagate
 * exceptions, for easier chaining.  These helpers deliberately not provided for non-returning
 * listeners, to affirm that all exceptions are explicitly handled.
 */
public class Listeners {
	private Listeners() {}

	public interface FutureListener {
		void onSuccess(Future future);

		void onFailure(Future future,
				RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public interface FutureEffect {
		void onSuccess();

		void onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public interface FutureProducer<R> {
		R onSuccess();

		R onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public interface FutureValueConsumer<T> {
		void onSuccess(T result);

		void onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public interface FutureValueTransformer<T, R> {
		R onSuccess(T result);

		R onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public interface BiFutureConsumer<T, U> {
		void onSuccess(T first, U second);

		void onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public interface BiFutureTransformer<T, U, R> {
		R onSuccess(T result, U second);

		R onFailure(RuntimeException t); //common implementation is merely to rethrow to children futures
	}

	public abstract static class SimpleFutureProducer<R> implements FutureProducer<R> {
		public R onFailure(RuntimeException t) {
			throw t;
		}
	}

	public abstract static class SimpleFutureTransformer<T, R>
			implements FutureValueTransformer<T, R> {
		public R onFailure(RuntimeException t) {
			throw t;
		}
	}

	public abstract static class SimpleBiFutureTransformer<T, U, R>
			implements BiFutureTransformer<T, U, R> {
		public R onFailure(RuntimeException t) {
			throw t;
		}
	}
}
