# Async Context

Futures/Executors that:

- propagate an async context
- allocate much less
- slightly simplified api

But otherwise they very closely mirror Guava Futures

Current status: **Playground**, moving toward prototype.

## AsyncContext

- [`AsyncContext`](/asyncContext/src/main/java/com/mpd/concurrent/asyncContext/AsyncContext.java) is
  effectively a `ConcurrentHashMap` of classes to data of that type.
- Whenever the OS or a 3p library calls into your code, you should generally call
  [
  `AsyncContext#setNewRootContext(String name)`](/asyncContext/src/main/java/com/mpd/concurrent/asyncContext/AsyncContext.java#:~:text={setNewRootContext})
  to initialize a new AsyncContext.
- [`Executor`](/asyncContext/src/main/java/com/mpd/concurrent/executors/Executor.java) and
  [`Future`](/asyncContext/src/main/java/com/mpd/concurrent/futures/Future.java) preserve this
  AsyncContext as the control flow moves between actual threads,
  using [
  `AsyncContext.getCurrentExecutionContext`](/asyncContext/src/main/java/com/mpd/concurrent/asyncContext/AsyncContext.java#:~:text={getCurrentExecutionContext}),
  [
  `AsyncContext.resumeExecutionContext`](/asyncContext/src/main/java/com/mpd/concurrent/asyncContext/AsyncContext.java#:~:text={resumeExecutionContext}),
  and [
  `AsyncContext.pauseExecutionContext`](/asyncContext/src/main/java/com/mpd/concurrent/asyncContext/AsyncContext.java#:~:text={pauseExecutionContext}).
- You may also use [
  `AsyncContext.forkCurrentExecutionContext(String name)`](/asyncContext/src/main/java/com/mpd/concurrent/asyncContext/AsyncContext.java#:~:text={forkCurrentExecutionContext})
  or [
  `#clone(String name)`](/asyncContext/src/main/java/com/mpd/concurrent/asyncContext/AsyncContext.java#:~:text={clone})
  to fork a new AsyncContext for the
  current function, and this will automatically be propagated to any child functions branching
  directly from this one.
- Arbitrary data may be stored in this context, keyed by class.
  - By default, it stores [
    `AsyncContext$RootContextName`](/asyncContext/src/main/java/com/mpd/concurrent/asyncContext/AsyncContext.java#:~:text={RootContextName}),
    [
    `AsyncContext$ExecutionContextName`](/asyncContext/src/main/java/com/mpd/concurrent/asyncContext/AsyncContext.java#:~:text={ExecutionContextName}),
    both useful for debugging, and also [
    `Executor$RunnablePriority`](/asyncContext/src/main/java/com/mpd/concurrent/executors/Executor.java#:~:text={RunnablePriority}),
    which controls the priority of the Runnables associated with the current context.
- This can be used to track profiling data, debugging context, or other state, while processing a
  future-chain.

## Future

- [`Future<O>`](/asyncContext/src/main/java/com/mpd/concurrent/futures/Future.java) extends
  java.util.concurrent.ScheduledFuture<O>, so it is backwards compatible, making
  it easy to replace into existing codebases, mostly by simply changing the import. The default
  implementations use lock-free atomics for everything except actually completing a future.
- [
  `Future.futureConfig`](/asyncContext/src/main/java/com/mpd/concurrent/futures/Future.java#:~:text={futureConfig})
  is a public static global providing the default executor, and the uncaught
  exception handler.
- Added [
  `#resultNow`](/asyncContext/src/main/java/com/mpd/concurrent/futures/Future.java#:~:text={resultNow}),
  [
  `#exceptionNow`](/asyncContext/src/main/java/com/mpd/concurrent/futures/Future.java#:~:text={exceptionNow})
  methods from Java19.
- `#get` is deprecated. There's plenty of non-blocking APIs for virtually every other use-case.
- Each Future must have exactly 1 [
  `FutureListener`](/asyncContext/src/main/java/com/mpd/concurrent/futures/FutureListener.java)*,
  which is always executed in the same thread.
  - This assists with ensuring that all chains have an explicit end, which means unhandled
    exceptions can be detected, reported, and fixed more aggressively.
  - This also reduces complexity and allocations, resulting in better cpu performance and reduced
    data cache.
  - *TODO: Add a `FanoutListener` as an escape hatch. This is still safe as long as it's Immutable.
- [`#cancel`](/asyncContext/src/main/java/com/mpd/concurrent/futures/Future.java#:~:text={cancel})
  is less magical. It simply fails the future with a CancellationException, which is passed
  along identical to any other failure, making it much easier to track when and why a future-chain
  was cancelled. `#cancel(boolean)` now deprecated in favor of
  `#cance(CancellationException, boolean)`,
  giving even better debuggability.
- [
  `#getScheduledTimeNanos()`](/asyncContext/src/main/java/com/mpd/concurrent/futures/Future.java#:~:text={getScheduledTimeNanos})
  exists. I'm still shocked that ScheduledFuture did not have this.
- Future has fluent chaining APIs, which are a superset of Guava's FluentFuture, again, making it
  easy to replace into existing codebases, mostly by simply changing the import.
  - [
    `#transform`](/asyncContext/src/main/java/com/mpd/concurrent/futures/Future.java#:~:text={transform}),
    `#transformAsync`, `#catching`, `#catchingAsync`, `#withTimeout`.
  - Each of these also has an overload that doesn't take an executor, and simply uses the default
    executor. Since most transforms and submits should be lightweight CPU tasks, this is convenient.
  - Each of these makes only a single allocation (though the "runnable" _parameter_ will usually
    require a separate allocation at runtime)
- [`#end`](/asyncContext/src/main/java/com/mpd/concurrent/futures/Future.java#:~:text={end}) method
  explicitly ends a future chain, so that uncaught exceptions can be detected and handled.
- [
  `#addPendingString`](/asyncContext/src/main/java/com/mpd/concurrent/futures/Future.java#:~:text={addPendingString})
  (from Guava `AbstractFuture`) now public, and emits a callstack-like string
  describing the chain of incomplete parent futures to this chain.
- Useful [`Futures#X`](/asyncContext/src/main/java/com/mpd/concurrent/futures/Futures.java) methods
  exist just like the ones from Guava. Also [
  `ImmediateFuture`](/asyncContext/src/main/java/com/mpd/concurrent/futures/ImmediateFuture.java),
  Also [
  `RunnableFuture`](/asyncContext/src/main/java/com/mpd/concurrent/futures/RunnableFuture.java),
  and [
  `SettableFuture`](/asyncContext/src/main/java/com/mpd/concurrent/futures/SettableFuture.java).
  Again, for the most part, all you have to do is change the imports.
- [`AsyncFunction`](/asyncContext/src/main/java/com/mpd/concurrent/AsyncFunction.java),
  `AsyncCallable`,
  `AsyncConsumer`, `AsyncSupplier`, and `AsyncBiFunction` all exist, just like Guava.
  Again, for the most part, all you have to do is change the imports.

## Executor

- [`Executor`](/asyncContext/src/main/java/com/mpd/concurrent/executors/Executor.java) does NOT
  extend the `java.util.concurrent.ScheduledExecutorService` interface, because every method in that
  API is ridiculous in the context of futures.
- The primary methods are:
  - [
    `SubmittableFuture<O> execute(SubmittableFuture<O> task)`](/asyncContext/src/main/java/com/mpd/concurrent/executors/Executor.java#:~:text={execute})
  - [
    `Future<?> submit(Runnable task)`](/asyncContext/src/main/java/com/mpd/concurrent/executors/Executor.java#:~:text={submit})
  - [
    `<O> Future<O> submit(Runnable task, O result)`](/asyncContext/src/main/java/com/mpd/concurrent/executors/Executor.java#:~:text={submit})
  - [
    `Future<O> submit(Callable<O> task)`](/asyncContext/src/main/java/com/mpd/concurrent/executors/Executor.java#:~:text={submit})
  - [
    `Future<O> submitAsync(AsyncCallable<O> task)`](/asyncContext/src/main/java/com/mpd/concurrent/executors/Executor.java#:~:text={submitAsync})
  - Again, for code already using futures, you can mostly just change the imports.
  - Each method has an overload that takes a [
    `RunnablePriority`](/asyncContext/src/main/java/com/mpd/concurrent/executors/Executor.java#:~:text={RunnablePriority})
    allowing you to fork out a new [
    `AsyncContext`](/asyncContext/src/main/java/com/mpd/concurrent/asyncContext/AsyncContext.java)
    for this work that runs at a different priority.
- [
  `#asJavaExecutor`](/asyncContext/src/main/java/com/mpd/concurrent/executors/Executor.java#:~:text={asJavaExecutor})
  escape hatch which exposes the legacy `java.util.concurrent.ScheduledExecutorService` API, for
  easier integration.