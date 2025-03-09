package com.mpd.concurrent.futures;

import static android.util.Log.DEBUG;
import static android.util.Log.VERBOSE;
import static org.robolectric.Shadows.shadowOf;

import android.os.Looper;
import com.google.common.flogger.FluentLogger;
import com.mpd.concurrent.executors.Executor;
import com.mpd.concurrent.executors.Executor.AllExecutorsIdleListener;
import com.mpd.test.rules.AsyncContextRule;
import com.mpd.test.rules.ErrorCollector;
import com.mpd.test.rules.RegisterIdlingResourceRule;
import com.mpd.test.rules.UncaughtExceptionRule;
import com.tbohne.android.flogger.backend.AndroidBackend;
import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.robolectric.junit.rules.TimeoutRule;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLooper;

public class TestWithStandardRules {
	private static final FluentLogger log = FluentLogger.forEnclosingClass();

	// 0-19 handle things for all other rules, not specific to this codebase
	// UncaughtExceptionRule wants to catch exceptions from all other rules
	@Rule(order = 0) public UncaughtExceptionRule uncaughtExceptionRule = new UncaughtExceptionRule();

	// 20-39 handle things for all other rules, specific to this codebase
	// RegisterIdlingResourceRule needs to be lowish, so that other rules can wait for idle
	@Rule(order = 20) public RegisterIdlingResourceRule registerIdlingResourceRule = new RegisterIdlingResourceRule();
	// AsyncContextRule allows async work to track the context properly
	@Rule(order = 21) public AsyncContextRule asyncContextRule = new AsyncContextRule();

	// 40-79 may or may not be ordered relative to certain other rules, but don't have "global" dependencies
	// ErrorCollector allows the test to have multiple exceptions
	@Rule(order = 40) public ErrorCollector collector = new ErrorCollector();
	// TestRule prevents rules from taking forever if there's a deadlock. Disabled while debugger attached.
	@Rule(order = 41) public TestRule timeoutRule = new DisableOnDebug(TimeoutRule.seconds(30));

	// 80-99 wants to be "innermost"

	/**
	 * @return true if was already idle, or false if it had to wait
	 */
	private static void waitForExecutorsIdle(long startNanos) throws InterruptedException {
		CountDownLatch latch = new CountDownLatch(1);
		AllExecutorsIdleListener listener = latch::countDown;
		WeakReference<AllExecutorsIdleListener> callback = new WeakReference<>(listener);
		Executor.allExecutorsIdleListeners.add(callback);
		try {
			if (Executor.nonIdleExecutorCount.get() > 0) {
				long currentNanos = System.nanoTime();
				log.atFine().log(
						"waitForAllIdle has blocked for %dns, but an executor still has scheduled tasks. Waiting...",
						currentNanos - startNanos);
				latch.await();
			}
		} finally {
			Executor.allExecutorsIdleListeners.remove(callback);
		}
	}

	private static void waitForLoopersIdle(long startNanos) throws InterruptedException {
		for (Looper looper : ShadowLooper.getAllLoopers()) {
			shadowOf(looper).unPause();
		}
		try {
			for (Looper looper : ShadowLooper.getAllLoopers()) {
				ShadowLooper shadow = shadowOf(looper);
				if (!shadow.isIdle()) {
					long currentNanos = System.nanoTime();
					int tasks = shadow.getScheduler().size();
					log.atFine().log(
							"waitForAllIdle has blocked for %dns, but %s still has %d scheduled tasks. Waiting...",
							currentNanos - startNanos,
							shadow,
							tasks);
					shadow.runToEndOfTasks();
				}
			}
		} finally {
			for (Looper looper : ShadowLooper.getAllLoopers()) {
				shadowOf(looper).pause();
			}
		}
	}

	public static void waitForAllIdle() throws InterruptedException {
		long startNanos = System.nanoTime();
		boolean didWait = false;
		for (; ; ) {
			boolean allIdle = Executor.nonIdleExecutorCount.get() == 0;
			for (Looper looper : ShadowLooper.getAllLoopers()) {
				allIdle &= shadowOf(looper).isIdle();
			}
			if (allIdle) {
				if (didWait) {
					long currentNanos = System.nanoTime();
					log.atFine().log("waitForAllIdle blocked for %dns", currentNanos - startNanos);
				}
				return;
			}
			waitForLoopersIdle(startNanos);
			waitForExecutorsIdle(startNanos);
			didWait = true;
		}
	}

	@Before public void enableDebugLogging() {
		AndroidBackend.setLogLevelOverride(DEBUG);
		ShadowLog.setLoggable("atomic", VERBOSE);
		ShadowLog.setLoggable("futures", VERBOSE);
	}

	@After public void waitForAllIdleAfterTest() throws InterruptedException {
		waitForAllIdle();
	}
}
