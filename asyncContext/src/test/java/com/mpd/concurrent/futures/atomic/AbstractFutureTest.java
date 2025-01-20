package com.mpd.concurrent.futures.atomic;

import static android.util.Log.DEBUG;
import static android.util.Log.VERBOSE;
import static com.mpd.concurrent.executors.MoreExecutors.directExecutor;
import static com.mpd.test.matchers.WithCauseMatcher.withCause;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.stringContainsInOrder;

import android.util.Log;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.Future.AsyncCheckedException;
import com.mpd.concurrent.futures.Future.FutureNotCompleteException;
import com.mpd.concurrent.futures.Future.FutureSucceededTwiceException;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.test.rules.AsyncContextRule;
import com.mpd.test.rules.ErrorCollector;
import com.mpd.test.rules.UncaughtExceptionRule;
import com.tbohne.android.flogger.backend.AndroidBackend;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.junit.rules.TimeoutRule;
import org.robolectric.shadows.ShadowLog;

/**
 * @noinspection deprecation
 */
@RunWith(RobolectricTestRunner.class) public class AbstractFutureTest {
	@Rule(order = 0) public UncaughtExceptionRule uncaughtExceptionRule = new UncaughtExceptionRule();
	@Rule(order = 1) public ErrorCollector collector = new ErrorCollector();
	@Rule(order = 50) public TestRule timeoutRule = new DisableOnDebug(TimeoutRule.seconds(30));
	@Rule(order = 51) public AsyncContextRule asyncContextRule = new AsyncContextRule();

	@Nullable PublicAbstractFuture<String> fut;

	@Before public void enableDebugLogging() {
		AndroidBackend.setLogLevelOverride(DEBUG);
		ShadowLog.setLoggable("atomic", VERBOSE);
		ShadowLog.setLoggable("futures", VERBOSE);
	}

	public static void ensureFutureComplete(AbstractFuture<?> fut) {
		if (fut != null) {
			Log.d("atomic", "Cancelling " + fut);
			fut.cancel(Future.MAY_INTERRUPT); // cancel and interrupt anything in progress
			for (int i = 0; i < 10 && fut.getListener() instanceof AbstractFuture; i++) {
				fut = (AbstractFuture<?>) fut.getListener();  // traverse down chain up to a depth of 10 to find the end
			}
			if (fut.getListener() instanceof EndListener) { // if it's an end listener, then we don't need to do anything
				Log.d("atomic", fut + " already has an EndListener, so is \"safe\" to leak");
				return;
			}
			if (fut.getListener() == null) { // at the end of the chain, then swallow exceptions and end.
				Log.d("atomic", fut + " doesn't have an EndListener. Adding a catch-all, and #end()");
				fut.catching(Throwable.class, e -> null, directExecutor()).end();
			} else { // If there's an unknown  listener, then all we can do is pray :(
				Log.w("atomic", fut + " has an unknown listener, and we can't forcibly end the chain");
			}
		}
	}

	@After public void ensureFutureComplete() {
		ensureFutureComplete(fut);
		this.fut = null;
	}

	@Test public void constructor_default_stateIsPending() {
		fut = new PublicAbstractFuture<>();

		//java.util.concurrent.Future state
		collector.checkThrows(FutureNotCompleteException.class, fut::exceptionNow);
		//collector.checkThrows(FutureNotCompleteException.class, fut::get);
		//collector.checkThrows(TimeoutException.class, ()->fut.get(1, SECONDS));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(false));
		collector.checkThrows(FutureNotCompleteException.class, fut::resultNow);
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[]"));
		collector.checkSucceeds(fut::toString, stringContainsInOrder("PublicAbstractFuture@", "[]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, nullValue());
		collector.checkSucceeds(fut::getWrappedExceptionProtected, nullValue());
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void constructor_scheduled_stateIsPending() {
		fut = new PublicAbstractFuture<>(3, SECONDS);

		//java.util.concurrent.Future state
		collector.checkThrows(FutureNotCompleteException.class, fut::exceptionNow);
		//collector.checkThrows(FutureNotCompleteException.class, fut::get);
		//collector.checkThrows(TimeoutException.class, ()->fut.get(1, SECONDS));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(false));
		collector.checkThrows(FutureNotCompleteException.class, fut::resultNow);
		//java.util.concurrent.Delayed
		collector.checkSucceeds(() -> fut.getDelay(MILLISECONDS), greaterThan(0L));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkSucceeds(fut::getScheduledTimeNanos, greaterThan(0L));
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ scheduledNanos=",
				"]"));
		collector.checkSucceeds(fut::toString, stringContainsInOrder("PublicAbstractFuture@", "[ scheduledNanos=", "]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, greaterThan(0L));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, nullValue());
		collector.checkSucceeds(fut::getWrappedExceptionProtected, nullValue());
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void constructor_immediateSuccess_stateIsSuccessful() {
		String value = "constructor_immediateSuccess_stateIsSuccessful";
		fut = new PublicAbstractFuture<>(value);

		//java.util.concurrent.Future state
		collector.checkSucceeds(fut::exceptionNow, nullValue());
		collector.checkSucceeds(() -> fut.get(1, SECONDS), equalTo(value));
		collector.checkSucceeds(fut::get, equalTo(value));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkSucceeds(fut::resultNow, equalTo(value));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(true));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ success=constructor_immediateSuccess_stateIsSuccessful]"));
		collector.checkThat(
				fut.toString(),
				stringContainsInOrder("PublicAbstractFuture@", "[ success=constructor_immediateSuccess_stateIsSuccessful]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, sameInstance(value));
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void constructor_immediateUncheckedException_stateIsFailed() {
		ArithmeticException expectedException = new ArithmeticException(
				"constructor_immediateUncheckedException_stateIsFailed");
		fut = new PublicAbstractFuture<>(expectedException);

		//java.util.concurrent.Future state
		collector.checkSucceeds(fut::exceptionNow, sameInstance(expectedException));
		collector.checkThrows(ArithmeticException.class, fut::get, sameInstance(expectedException));
		collector.checkThrows(ArithmeticException.class, () -> fut.get(1, SECONDS), sameInstance(expectedException));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkThrows(ArithmeticException.class, fut::resultNow, sameInstance(expectedException));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"[ failure=java.lang.ArithmeticException: constructor_immediateUncheckedException_stateIsFailed]"));
		collector.checkSucceeds(fut::toString, stringContainsInOrder(
				"PublicAbstractFuture@",
				"[ failure=java.lang.ArithmeticException: constructor_immediateUncheckedException_stateIsFailed]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void constructor_immediateCheckedException_stateIsFailed() {
		IOException expectedException = new IOException("constructor_immediateCheckedException_stateIsFailed");
		fut = new PublicAbstractFuture<>(expectedException);

		//java.util.concurrent.Future state
		collector.checkSucceeds(fut::exceptionNow, sameInstance(expectedException));
		collector.checkThrows(AsyncCheckedException.class, fut::get, withCause(sameInstance(expectedException)));
		collector.checkThrows(AsyncCheckedException.class, () -> fut.get(1, SECONDS),
				withCause(sameInstance(expectedException)));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkThrows(AsyncCheckedException.class, fut::resultNow, withCause(sameInstance(expectedException)));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"[ failure=java.io.IOException: constructor_immediateCheckedException_stateIsFailed]"));
		collector.checkSucceeds(fut::toString, stringContainsInOrder(
				"PublicAbstractFuture@",
				"[ failure=java.io.IOException: constructor_immediateCheckedException_stateIsFailed]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, withCause(sameInstance(expectedException)));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void constructor_immediateCancelled_stateIsCancelled() {
		CancellationException expectedException = new CancellationException(
				"constructor_immediateCancelled_stateIsCancelled");
		fut = new PublicAbstractFuture<>(expectedException);

		//java.util.concurrent.Future state
		collector.checkSucceeds(fut::exceptionNow, sameInstance(expectedException));
		collector.checkThrows(CancellationException.class, fut::get, sameInstance(expectedException));
		collector.checkThrows(CancellationException.class, () -> fut.get(1, SECONDS), sameInstance(expectedException));
		collector.checkSucceeds(fut::isCancelled, equalTo(true));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkThrows(CancellationException.class, fut::resultNow, sameInstance(expectedException));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture",
				"(PublicAbstractFuture:0) //PublicAbstractFuture@",
				"[ cancelled=java.util.concurrent.CancellationException: constructor_immediateCancelled_stateIsCancelled]"));
		collector.checkSucceeds(fut::toString, stringContainsInOrder(
				"PublicAbstractFuture@",
				"[ cancelled=java.util.concurrent.CancellationException: constructor_immediateCancelled_stateIsCancelled]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void setResultWithSuccessValue_whenPending_isSuccess() {
		fut = new PublicAbstractFuture<>();

		String value = "setResultWithSuccessValue_whenPending_isSuccess";
		fut.setResult(value);

		//java.util.concurrent.Future state
		collector.checkSucceeds(fut::exceptionNow, nullValue());
		collector.checkSucceeds(() -> fut.get(1, SECONDS), sameInstance(value));
		collector.checkSucceeds(fut::get, sameInstance(value));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkSucceeds(fut::resultNow, equalTo(value));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(true));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ success=setResultWithSuccessValue_whenPending_isSuccess]"));
		collector.checkThat(
				fut.toString(),
				stringContainsInOrder("PublicAbstractFuture@", "[ success=setResultWithSuccessValue_whenPending_isSuccess]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, sameInstance(value));
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void setResultWithSuccessValue_afterAlreadySucceeded_crashes() {
		String value = "setResultWithSuccessValue_afterAlreadySucceeded_crashes";
		fut = new PublicAbstractFuture<>(value);

		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(Matchers.instanceOf(FutureSucceededTwiceException.class));
		fut.setResult(value);

		//java.util.concurrent.Future state
		collector.checkSucceeds(fut::exceptionNow, nullValue());
		collector.checkSucceeds(() -> fut.get(1, SECONDS), equalTo(value));
		collector.checkSucceeds(fut::get, equalTo(value));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkSucceeds(fut::resultNow, equalTo(value));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(true));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ success=setResultWithSuccessValue_afterAlreadySucceeded_crashes]"));
		collector.checkSucceeds(fut::toString, stringContainsInOrder(
				"PublicAbstractFuture@",
						"[ success=setResultWithSuccessValue_afterAlreadySucceeded_crashes]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, sameInstance(value));
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void setResultWithSuccessValue_afterAlreadyFailed_isNoOp() {
		ArithmeticException expectedException = new ArithmeticException(
				"setResultWithSuccessValue_afterAlreadyFailed_isNoOp_exception");
		fut = new PublicAbstractFuture<>(expectedException);

		String value = "setResultWithSuccessValue_afterAlreadyFailed_isNoOp_result";
		fut.setResult(value);

		//java.util.concurrent.Future state
		collector.checkSucceeds(fut::exceptionNow, sameInstance(expectedException));
		collector.checkThrows(ArithmeticException.class, fut::get, sameInstance(expectedException));
		collector.checkThrows(ArithmeticException.class, () -> fut.get(1, SECONDS), sameInstance(expectedException));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkThrows(ArithmeticException.class, fut::resultNow, sameInstance(expectedException));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"[ failure=java.lang.ArithmeticException: setResultWithSuccessValue_afterAlreadyFailed_isNoOp_exception]"));
		collector.checkSucceeds(fut::toString, stringContainsInOrder(
				"PublicAbstractFuture@",
				"[ failure=java.lang.ArithmeticException: setResultWithSuccessValue_afterAlreadyFailed_isNoOp_exception]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void setResultWithFuture_whenPending_resultIsPending_setsAsync() {
		fut = new PublicAbstractFuture<>();

		PublicAbstractFuture<String> async = new PublicAbstractFuture<>();
		fut.setResult(async);

		//java.util.concurrent.Future state
		collector.checkThrows(FutureNotCompleteException.class, fut::exceptionNow);
		//collector.checkThrows(FutureNotCompleteException.class, fut::get);
		//collector.checkThrows(TimeoutException.class, ()->fut.get(1, SECONDS));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(false));
		collector.checkThrows(FutureNotCompleteException.class, fut::resultNow);
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ setAsync=PublicAbstractFuture@",
				"]"));
		collector.checkSucceeds(
				fut::toString,
				stringContainsInOrder("PublicAbstractFuture@", "[ setAsync" + "=PublicAbstractFuture@", "]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, sameInstance(async));
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, nullValue());
		collector.checkSucceeds(fut::getWrappedExceptionProtected, nullValue());
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void setResultWithFuture_whenPending_resultIsSucceeded_isSucceeded() {
		fut = new PublicAbstractFuture<>();

		String value = "setResultWithFuture_whenPending_resultIsComplete_setsAsync";
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>(value);
		fut.setResult(async);

		//java.util.concurrent.Future state
		collector.checkThrows(FutureNotCompleteException.class, fut::exceptionNow);
		collector.checkSucceeds(() -> fut.get(1, SECONDS), sameInstance(value));
		collector.checkSucceeds(fut::get, sameInstance(value));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkSucceeds(fut::resultNow, sameInstance(value));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(true));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ success=setResultWithFuture_whenPending_resultIsComplete_setsAsync]"));
		collector.checkSucceeds(
				fut::toString,
				stringContainsInOrder("PublicAbstractFuture@",
						"[ success=setResultWithFuture_whenPending_resultIsComplete_setsAsync]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, sameInstance(async));
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(
				fut::getResultProtected,
				equalTo("setResultWithFuture_whenPending_resultIsComplete_setsAsync"));
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void setResultWithFuture_whenPending_resultIsFailed_isFailed() {
		fut = new PublicAbstractFuture<>();

		ArithmeticException expectedException = new ArithmeticException(
				"setResultWithFuture_whenPending_resultIsFailed_setsAsync");
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>(expectedException);
		fut.setResult(async);

		//java.util.concurrent.Future state
		collector.checkSucceeds(fut::exceptionNow, sameInstance(expectedException));
		collector.checkThrows(ArithmeticException.class, fut::get, sameInstance(expectedException));
		collector.checkThrows(ArithmeticException.class, () -> fut.get(1, SECONDS), sameInstance(expectedException));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkThrows(ArithmeticException.class, fut::resultNow, sameInstance(expectedException));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"[ failure=java.lang.ArithmeticException: setResultWithFuture_whenPending_resultIsFailed_setsAsync]"));
		collector.checkSucceeds(fut::toString, stringContainsInOrder(
				"PublicAbstractFuture@",
				"[ failure=java.lang.ArithmeticException: setResultWithFuture_whenPending_resultIsFailed_setsAsync]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void setResultWithFuture_whenPending_resultIsCancelled_isCancelled() {
		fut = new PublicAbstractFuture<>();

		CancellationException expectedException = new CancellationException(
				"setResultWithFuture_whenPending_resultIsCancelled_isCancelled");
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>(expectedException);
		fut.setResult(async);

		//java.util.concurrent.Future state
		collector.checkSucceeds(fut::exceptionNow, sameInstance(expectedException));
		collector.checkThrows(CancellationException.class, fut::get, sameInstance(expectedException));
		collector.checkThrows(CancellationException.class, () -> fut.get(1, SECONDS), sameInstance(expectedException));
		collector.checkSucceeds(fut::isCancelled, equalTo(true));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkThrows(CancellationException.class, fut::resultNow, sameInstance(expectedException));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"[ failure=java.lang.ArithmeticException: setResultWithFuture_whenPending_resultIsCancelled_isCancelled]"));
		collector.checkSucceeds(fut::toString, stringContainsInOrder(
				"PublicAbstractFuture@",
				"[ failure=java.lang.ArithmeticException: setResultWithFuture_whenPending_resultIsCancelled_isCancelled]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	// TODO: test setResult(Future)
	// TODO: test setResult(Future) then complete
	// TODO: test setException(Throwable)
	// TODO: test setException(Throwable, mayInterruptIfRunning)
	// TODO: test cancel
	// TODO: test onFutureSucceeded
	// TODO: test onFutureFailed
	// TODO: test getPendingString
	// TODO: test toString
	// TODO: test compareTo

	@Test public void toString_recursiveFuture_limitedDepth() {
		fut = new PublicAbstractFuture<>();
		Future<String> fut2 = fut.transform(s -> s);

		fut.setResult(fut2);

		collector.checkSucceeds(
				fut::toString,
				stringContainsInOrder("PublicAbstractFuture@",
						"[ setAsync=FutureFunction<AbstractFutureTest$$Lambda$",
						"/0x",
						">]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(sb.toString(), matchesPattern(Pattern.compile(".+", Pattern.DOTALL)));


		collector.checkThat(sb.toString(), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) //PublicAbstractFuture@",
				"\n  at AbstractFutureTest$$Lambda$",
				".apply(AbstractFutureTest:0) //FutureFunction<AbstractFutureTest$$Lambda$",
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) //PublicAbstractFuture@",
				"\n  at AbstractFutureTest$$Lambda$",
				".apply(AbstractFutureTest:0) //FutureFunction<AbstractFutureTest$$Lambda$"));
	}

	/**
	 * AbstractFuture where all methods are public, so we can mess with them in test
	 */
	private static class PublicAbstractFuture<O> extends AbstractFuture<O> {

		public PublicAbstractFuture() {
			super();
		}

		public PublicAbstractFuture(long delay, TimeUnit delayUnit) {
			super(delay, delayUnit);
		}

		public PublicAbstractFuture(@Nullable O value) {
			super(value);
		}

		public PublicAbstractFuture(Throwable exception) {
			super(exception);
		}

		@Override public Future<? extends O> getSetAsync() {
			return super.getSetAsync();
		}

		@Override public long getScheduledTimeNanosProtected() {
			return super.getScheduledTimeNanosProtected();
		}

		@Override public @Nullable O getResultProtected() {
			return super.getResultProtected();
		}

		@Override public @Nullable Throwable getExceptionProtected() {
			return super.getExceptionProtected();
		}

		@Override public @Nullable RuntimeException getWrappedExceptionProtected() {
			return super.getWrappedExceptionProtected();
		}

		@Override public @Nullable Throwable getInterrupt() {
			return super.getInterrupt();
		}

		@Override public void interruptTask(Throwable exception) {
			super.interruptTask(exception);
		}

		@Override public @Nullable FutureListener<? super O> getListener() {
			return super.getListener();
		}

		@Override public Class<?> sourceClass() {
			return super.sourceClass();
		}

		@Override public @Nullable String sourceMethodName() {
			return super.sourceMethodName();
		}
	}
}
