# Async
## Summary
A reimagining of futures and executors in Java, intended to work on Android.
VoidFutures and ValueFutures are separate types that easily chain together, there are no blocking 
members, and all callbacks are required to handle exceptions, and be explicit about where the work
is executed.

## Why
- Futures have no long-blocking members like .get()
- Exceptions must always be handled by callers.
- Cancelling a future cancels child futures, and childless parent futures, recursively.
- Can grab snapshots of callstacks of all threads working on your future.
- VoidFuture is a native type.
- futureA.andAfter(futureB).then(functionWithTwoArgs);

## Vocab
- Task: An operation that results in a future.
- Future.complete: the future has completed with a result or exception
- Future.success: the future has completed with a result and no exception

## History
Futures are neat.

When Java7 Added [futures](https://docs.oracle.com/javase/8/docs/api/index.html?java/util/concurrent/Future.html), 
they forgot to add any of the useful parts. There was no way to chain subsequent tasks, and no 
non-blocking way to retrieve the result. This is a complete abomination. 
The [ExecutorServices](https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html)
 produce Java7 Futures.

Guava produced [ListenableFuture](https://github.com/google/guava/wiki/ListenableFutureExplained), which allows listeners, which is a huge step, but they're a 
 awkward about handling exceptions, and you have to use [ListeningExecutorService](https://google.github.io/guava/releases/19.0/api/docs/com/google/common/util/concurrent/ListeningExecutorService.html)
 everywhere, which is slightly inconvenient.  Also, since async programming is hard, people just 
 keep calling the blocking .get() method, which half defeats the point of using features to start with.
 
 Then Java8 produced [CompletableFuture](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html),
 which split the future (used by the thing that sets the result) from the [CompletionStage](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletionStage.html),
 which is for scheduling subsequent tasks. This allows the callers to only use CompletionStages, 
 which prevent blocking threads, but also prevent cancelling. They also make it easy to schedule
 tasks in their special threads.
 
 In my real code, I would schedule async tasks across plural threads, then in production saw it was 
 sometimes slow. I have code that can report when a single async task was slow, but not when 
 it was split across unpredictable threads. So I wanted to be able to get a snapshot of all the
 threads' callstacks that were working on fulfilling a future. 

## Basics

 - interface **Future**:  
   The reference to a tasks of of a chain of async tasks.
   - Check if this step is finished, succeeded, or cancelled at any time.
   - Get the exception if the step failed.
   - Cancel the future, which recursively cancels prerequisite tasks, unless they have other child tasks.
   - Grab a snapshot of call stacks of all threads actively working on fulfilling this specific future.
   - Attach a listener for when the future finishes.
   - Mark a future as not cancelled itself by cancelled children.
   - As a return value from addListener, you know all exceptions are handled somewhere.
 - interface **RunnableFuture** extends Future, Runnable
   - Runnable that fulfills itself as a Future. Very useful when scheduling tasks because it means we can 
   simply queue the futures directly and cancel them when needed, with no overhead or maps or anything.
 - interface **VoidFuture** extends Future:  
   The reference to a step of a chain of async tasks with no return type.
   - Attach a SideEffectTask, or a ProducerTask.
 - interface **FutureResult<R>**:  
   The return value from a future.
   - Check if it's succeeded.
   - Get the exception if the step failed.
   - Get the result of the task.
 - interface **ValueFuture<R>** extends FutureResult<R>:  
   The reference to a step of a chain of async tasks with a return type.
   - Attach a ConsumerTask, a TransformerTask, or a SideEffectTask that explicitly ignores the result.
   - Attach a Consumer<FutureResult<R>> or Function<FutureResult<R>, R2>.
  - interface **BiValueFuture<T,U>** extends Future:   
    A holder of two ValueFutures, but doesn't actually _do_ anything.
    - Attach a BiConsumerTask or BiTransformerTask.
 - **Async**:  
   Static class with helper methods for starting async tasks.
   - Create a VoidFuture that triggers when a list of other Futures are all complete.
   - Create a ValueFuture<List<R>> from a List<ValueFuture<R>>.
   - A method for blocking the current thread until a future is complete.
     - This is strongly discouraged, but necessary for working with non-Future-based Apis.
 - **Combine**:  
   Static class with helper methods for combining tasks.
   - Methods for ignoring the result of a future.
   - Methods for chaining tasks after a future.
   - Methods for chaining tasks combining two futures complete or succeed.
   - Methods for chaining tasks after N futures complete or succeed.
   - Methods for chaining tasks after any of N futures completes.
 - **PrereqStrategy**:  
    List of prerequisite strategies when combining Futures:
     - ALL_PREREQS_COMPLETE: waits for all prerequisites to complete.
     - ALL_PREREQS_SUCCEED: waits for all prerequisites to succeed, or one to fail.
     - ANY_PREREQS_COMPLETE: waits for any prerequisites to succeed or fail.
     - ANY_PREREQS_SUCCEED: waits for any prerequisites to succeed or all to fail.
 
 ## Implementations
 - **SettableFuture<R>** implements Future  
   A step for manually triggering a Future. Discouraged, but necessary in some cases. Useful as a base class.
   - SetResult(R) and SetFailed(RuntimeException).
   - Has a set of FutureListener callbacks of child Futures.
   - Uncancellable by itself.
   - **SettableVoidFuture** implements VoidFuture, and **SettableValueFuture** implements ValueFuture.
 - **RunnableFutureTask<R>** extends SettableFuture<R> implements RunnableFuture
   - Cancellable Runnable that fulfills itself as a Future, then kicks off all the dependencies. 
 - **QueuableFutureTask<R>**
   Step that waits for prerequisites to complete before executing a FutureProducer as a runnable.
   - Has a Set of Future prerequisites
   - **QueuableVoidFuture** implements VoidFuture, **QueuableValueFuture** implements ValueFuture, and **QueuableBiValueFuture** implements BiValueFuture.
   
## Executors
- **Executor**  
  Interface for scheduling work
  - Can be passed a Runnable or Supplier, returns a Future. There are no non-Future submission methods.
- **DirectExecutor** implements Executor
  - Executes all work immediately, in the calling thread. 
  - Executor cannot be shut down, because that created lunacy.
  - See [com.google.common.util/concurrent.MoreExecutors#directExecutor](https://google.github.io/guava/releases/19.0/api/docs/com/google/common/util/concurrent/MoreExecutors.html#directExecutor())
- **SerializedDirectExecutor** implements Executor
  - Executes all work, in the calling thread, but one at a time. This prevents recursion in methods assumed to be serialized.
  - Executor cannot be shut down, because that created lunacy.
- **ThreadPoolExecutor** implements Executor
  - Executes runnables in a pool of threads.
  - See [java.util.concurrent.ThreadPoolExecutor](https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ThreadPoolExecutor.html)
  
## NOTES
- I try to be super careful to minimize the number of methods called in synchronized block. I think there's only one non-java.lang method call in a block right now, and it's a getter that is 'final'.
- Trying to make sure classes are designed to be inherited from, and generic. That's why ANY_PREREQS_SUCCEED exists, but is unused.
- Trying to keep BiValueFuture as a second-class citizen, as evidence the API is generic enough to handle custom future types.
  
## TODO
- When combining features, pass in PrereqStrategy.
- Decide on tons of overloads vs tons of parameters when combining.  
  Maybe tons of overloads in Combine methods, but overloads in futures for fluency?
