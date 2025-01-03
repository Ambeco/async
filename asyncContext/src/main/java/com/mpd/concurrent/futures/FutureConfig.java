package com.mpd.concurrent.futures;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import com.google.common.flogger.FluentLogger;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.executors.impl.JavaAsMpdExecutor;
import com.mpd.concurrent.executors.impl.LooperAsMpdExecutor;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.CancellationException;

public interface FutureConfig {
	// default executor for future transforms
	default Executor getDefaultExecutor() {
		return new JavaAsMpdExecutor(AsyncTask.THREAD_POOL_EXECUTOR, Runtime.getRuntime().availableProcessors());
	}

	// Many executors won't implement scheduling directly, and will simply delegate to this executor for the
	// delay, and after that completes, immediately run the task on themselves.
	default Executor getDelegateScheduledExecutor() {
		return new LooperAsMpdExecutor(Looper.getMainLooper());
	}

	// what to do when an unhandled exception occurs. Note that CancellationException should probably be ignored.
	void onUnhandledException(Throwable exception);


	class DefaultFutureConfig implements FutureConfig {
		static final FluentLogger log = FluentLogger.forEnclosingClass();

		private static UncaughtExceptionHandler getBestDelegate(Thread currentThread) {
			UncaughtExceptionHandler currentThreadHandler = currentThread.getUncaughtExceptionHandler();
			if (currentThreadHandler != null) {
				return currentThreadHandler;
			}
			UncaughtExceptionHandler defaultThreadHandler = Thread.getDefaultUncaughtExceptionHandler();
			if (defaultThreadHandler != null) {
				return defaultThreadHandler;
			}
			UncaughtExceptionHandler mainThreadHandler = Looper.getMainLooper().getThread().getUncaughtExceptionHandler();
			if (mainThreadHandler != null) {
				return mainThreadHandler;
			}
			// Throw the exception in the main thread and let the OS handle the app crash.
			// Theoretically the main thread might be blocked, preventing the crash, but in practice, the OS will ANR/crash
			// the process in that case anyway, making that situation moot.
			return (thread, throwable) -> {
				log.atSevere().withCause(throwable).log("Unhandled Exception in thread %s", thread);
				Handler mainHandler = new Handler(Looper.getMainLooper());
				mainHandler.post(() -> {
					if (throwable instanceof RuntimeException) {
						throw (RuntimeException) throwable;
					} else {
						throw new RuntimeException(throwable);
					}
				});
			};
		}

		@Override public void onUnhandledException(Throwable exception) {
			// it's very normal to not catch and handle Cancellation, so don't do anything dramatic for that
			if (exception instanceof CancellationException) {
				log.atFine().withCause(exception).log("Unhandled CancellationException");
				return;
			}
			Thread currentThread = Thread.currentThread();
			getBestDelegate(currentThread).uncaughtException(currentThread, exception);
		}
	}
}
