package com.mpd.test;

import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.sameInstance;

import androidx.annotation.NonNull;
import com.google.common.flogger.FluentLogger;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matcher;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;

public class UncaughtExceptionRule implements TestRule {
	private static final FluentLogger log = FluentLogger.forEnclosingClass();

	CopyOnWriteArraySet<ExpectedUncaughtException> expectedExceptions = new CopyOnWriteArraySet<>();
	CopyOnWriteArraySet<ExpectedUncaughtException> unmatchedExpectedExceptions = new CopyOnWriteArraySet<>();
	CopyOnWriteArrayList<Throwable> uncaughtExceptions = new CopyOnWriteArrayList<>();

	public void expectUncaughtException(Matcher<Thread> threadMatcher, Matcher<Throwable> throwableMatcher) {
		ExpectedUncaughtException expected = new ExpectedUncaughtException(threadMatcher, throwableMatcher);
		unmatchedExpectedExceptions.add(expected);
		expectedExceptions.add(expected);
	}

	public void expectUncaughtExceptionInAnyThread(Matcher<Throwable> throwableMatcher) {
		ExpectedUncaughtException expected = new ExpectedUncaughtException(any(Thread.class), throwableMatcher);
		unmatchedExpectedExceptions.add(expected);
		expectedExceptions.add(expected);
	}

	public void expectUncaughtExceptionInAnyThread(Throwable throwable) {
		ExpectedUncaughtException expected = new ExpectedUncaughtException(any(Thread.class), sameInstance(throwable));
		unmatchedExpectedExceptions.add(expected);
		expectedExceptions.add(expected);
	}

	public void expectUncaughtExceptionInThisThread(Matcher<Throwable> throwableMatcher) {
		ExpectedUncaughtException expected = new ExpectedUncaughtException(sameInstance(Thread.currentThread()),
				throwableMatcher);
		unmatchedExpectedExceptions.add(expected);
		expectedExceptions.add(expected);
	}

	public void expectUncaughtExceptionInThisThread(Throwable throwable) {
		ExpectedUncaughtException expected = new ExpectedUncaughtException(sameInstance(Thread.currentThread()),
				sameInstance(throwable));
		unmatchedExpectedExceptions.add(expected);
		expectedExceptions.add(expected);
	}

	private void reportUncaughtException(Thread thread, Throwable ex) {
		// we create the UncaughtException first to snag as much volatile thread state as possible, ASAP
		UncaughtException uncaught = new UncaughtException(thread, ex);
		for (ExpectedUncaughtException expected : expectedExceptions) {
			if (expected.matches(uncaught)) {
				unmatchedExpectedExceptions.remove(expected);
				log.atInfo().log("expected uncaught exception was detected: " + uncaught);
				return;
			}
		}
		log.atSevere().withCause(uncaught).log("uncaught exception did not match any expected exceptions: "
				+ expectedExceptions);
		uncaughtExceptions.add(uncaught);
	}

	@Override public Statement apply(Statement base, Description description) {
		return new Statement() {
			@Override public void evaluate() throws Throwable {
				// save any uncaught exceptions passed to the test thread's UncaughtExceptionHandler
				LoggingUncaughtExceptionHandler threadHandler = new LoggingUncaughtExceptionHandler(Thread.currentThread()
						.getUncaughtExceptionHandler());
				Thread.currentThread().setUncaughtExceptionHandler(threadHandler);
				// save any uncaught exceptions passed to the global default UncaughtExceptionHandler
				LoggingUncaughtExceptionHandler
						defaultHandler =
						new LoggingUncaughtExceptionHandler(Thread.getDefaultUncaughtExceptionHandler());
				Thread.setDefaultUncaughtExceptionHandler(defaultHandler);
				try {
					try {
						// run the test
						base.evaluate();
					} catch (RuntimeException e) {
						uncaughtExceptions.add(e);
					}
					// if any expected exceptions were not thrown, report them
					uncaughtExceptions.addAll(unmatchedExpectedExceptions);
					// if UncaughtExceptionHandlers were used, then report all exceptions in it.
					if (uncaughtExceptions.size() == 1) {
						throw uncaughtExceptions.get(0);
					} else if (!uncaughtExceptions.isEmpty()) {
						throw new MultipleFailureException(uncaughtExceptions);
					}
				} finally {
					// restore the old UncaughtExceptionHandlers
					Thread.setDefaultUncaughtExceptionHandler(defaultHandler.oldHandler);
					Thread.currentThread().setUncaughtExceptionHandler(threadHandler.oldHandler);
				}
			}
		};
	}

	public static class UncaughtException extends RuntimeException {
		public final Thread thread;

		public UncaughtException(Thread thread, Throwable exception) {
			super("Uncaught exception thrown in thread " + thread + ": " + exception, exception);
			// subtly, we NEED to access thread.toString here, to ensure we preserve the mutable state ASAP
			this.thread = thread;
		}
	}

	public static class ExpectedUncaughtException extends RuntimeException {
		public final Matcher<Thread> threadMatcher;
		public final Matcher<Throwable> throwableMatcher;

		public ExpectedUncaughtException(Matcher<Thread> threadMatcher, Matcher<Throwable> throwableMatcher) {
			super("expected thread matching " + threadMatcher + " to throw exception matching " + throwableMatcher);
			this.threadMatcher = threadMatcher;
			this.throwableMatcher = throwableMatcher;
		}

		public boolean matches(UncaughtException ex) {
			return threadMatcher.matches(ex.thread) && throwableMatcher.matches(ex.getCause());
		}
	}

	public class LoggingUncaughtExceptionHandler implements UncaughtExceptionHandler {
		public final @Nullable UncaughtExceptionHandler oldHandler;

		public LoggingUncaughtExceptionHandler(UncaughtExceptionHandler oldHandler) {
			this.oldHandler = oldHandler;
		}

		@Override public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
			reportUncaughtException(t, e);
			if (oldHandler != null) {
				oldHandler.uncaughtException(t, e);
			}
		}
	}
}

