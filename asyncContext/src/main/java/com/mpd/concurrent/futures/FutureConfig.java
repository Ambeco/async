package com.mpd.concurrent.futures;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import com.google.common.flogger.FluentLogger;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.executors.locked.JavaAsMpdExecutor;
import com.mpd.concurrent.executors.locked.LooperAsMpdExecutor;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.CancellationException;

public interface FutureConfig {
	// default executor for future transforms
	Executor getDefaultExecutor();

	// Some executors won't implement scheduling directly, and will simply delegate to this executor for the
	// delay, and after that completes, immediately run the task on themselves.
	Executor getDelegateScheduledExecutor();

	// what to do when an unhandled exception occurs. Note that CancellationException should probably be ignored.
	void onUnhandledException(Throwable exception);


	class DefaultFutureConfig implements FutureConfig {
		static final FluentLogger log = FluentLogger.forEnclosingClass();
		static final JavaAsMpdExecutor defaultExecutor = new JavaAsMpdExecutor(
				AsyncTask.THREAD_POOL_EXECUTOR,
				Runtime.getRuntime().availableProcessors());
		static final LooperAsMpdExecutor defaultScheduledExecutor = new LooperAsMpdExecutor(Looper.getMainLooper());

		@Override public Executor getDefaultExecutor() {
			return defaultExecutor;
		}

		@Override public Executor getDelegateScheduledExecutor() {
			return defaultScheduledExecutor;
		}

		private static UncaughtExceptionHandler getBestDelegate(Thread currentThread) {
			UncaughtExceptionHandler currentThreadHandler = currentThread.getUncaughtExceptionHandler();
			if (currentThreadHandler != null) {
				log.atFine().log("Unhandled Exception handled by current thread handler %s", currentThreadHandler);
				return currentThreadHandler;
			}
			UncaughtExceptionHandler defaultThreadHandler = Thread.getDefaultUncaughtExceptionHandler();
			if (defaultThreadHandler != null) {
				log.atFine().log("Unhandled Exception handled by current thread handler %s", defaultThreadHandler);
				return defaultThreadHandler;
			}
			UncaughtExceptionHandler mainThreadHandler = Looper.getMainLooper().getThread().getUncaughtExceptionHandler();
			if (mainThreadHandler != null) {
				log.atFine().log("Unhandled Exception handled by current thread handler %s", mainThreadHandler);
				return mainThreadHandler;
			}
			// Throw the exception in the main thread and let the OS handle the app crash.
			// Theoretically the main thread might be blocked, preventing the crash, but in practice, the OS will ANR/crash
			// the process in that case anyway, making that situation moot.
			log.atFine().log("Unhandled Exception handled by crash-in-main-thread-fallback");
			return (thread, throwable) -> {
				log.atSevere().withCause(throwable).log("Unhandled Exception in thread %s", thread);
				RuntimeException wrapped = new RuntimeException("Unhandled Exception in thread " + thread, throwable);
				Handler mainHandler = new Handler(Looper.getMainLooper());
				mainHandler.post(() -> {
					log.atFine().log("Unhandled Exception handler crash-in-main-thread-fallback crashing now");
					throw wrapped;
				});
			};
		}

		@Override public void onUnhandledException(Throwable exception) {
			// it's very normal to not catch and handle Cancellation, so don't do anything dramatic for that
			if (exception instanceof CancellationException) {
				log.atFine().withCause(exception).log("Unhandled %s", exception);
				return;
			}
			Thread currentThread = Thread.currentThread();
			getBestDelegate(currentThread).uncaughtException(currentThread, exception);
		}
	}
}
