package com.mpd.concurrent.futures.atomic;

import static android.util.Log.DEBUG;
import static android.util.Log.VERBOSE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.mpd.concurrent.executors.MoreExecutors.directExecutor;
import static com.mpd.test.matchers.WithCauseMatcher.withCause;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
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

		checkFutureIsPending();
	}

	@Test public void constructor_scheduled_stateIsScheduled() {
		fut = new PublicAbstractFuture<>(3, SECONDS);

		checkFutureIsScheduled();
	}

	@Test public void constructor_immediateSuccess_stateIsSuccessful() {
		String value = "constructor_immediateSuccess_stateIsSuccessful";
		fut = new PublicAbstractFuture<>(value);

		checkFutureIsSuccessful(value);
	}

	@Test public void constructor_immediateUnchecked_stateIsFailed() {
		ArithmeticException expectedException = new ArithmeticException("constructor_immediateUnchecked_stateIsFailed");
		fut = new PublicAbstractFuture<>(expectedException);

		checkFutureFailedUnchecked(expectedException, "constructor_immediateUnchecked_stateIsFailed");
	}

	@Test public void constructor_immediateChecked_stateIsFailed() {
		IOException expectedException = new IOException("constructor_immediateChecked_stateIsFailed");
		fut = new PublicAbstractFuture<>(expectedException);

		//java.util.concurrent.Future state
		checkFutureFailedChecked(expectedException, "constructor_immediateChecked_stateIsFailed");
	}

	@Test public void constructor_immediateCancelled_stateIsCancelled() {
		CancellationException expectedException = new CancellationException(
				"constructor_immediateCancelled_stateIsCancelled");
		fut = new PublicAbstractFuture<>(expectedException);

		//java.util.concurrent.Future state
		checkFutureCancelled(expectedException, "constructor_immediateCancelled_stateIsCancelled");
	}

	@Test public void setResultWithSuccessValue_whenPending_isSuccess() {
		fut = new PublicAbstractFuture<>();

		String value = "setResultWithSuccessValue_whenPending_isSuccess";
		fut.setResult(value);

		checkFutureIsSuccessful(value);
	}

	@Test public void setResultWithSuccessValue_afterAlreadySucceeded_crashes() {
		String value = "setResultWithSuccessValue_afterAlreadySucceeded_crashes";
		fut = new PublicAbstractFuture<>(value);

		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(Matchers.instanceOf(FutureSucceededTwiceException.class));
		fut.setResult(value);

		checkFutureIsSuccessful(value);
	}

	@Test public void setResultWithSuccessValue_afterAlreadyUnchecked_isNoOp() {
		ArithmeticException expectedException = new ArithmeticException(
				"setResultWithSuccessValue_afterAlreadyUnchecked_isNoOp");
		fut = new PublicAbstractFuture<>(expectedException);

		String value = "setResultWithSuccessValue_afterAlreadyUnchecked_isNoOp";
		fut.setResult(value);

		checkFutureFailedUnchecked(
				expectedException,
				"java.lang.ArithmeticException: setResultWithSuccessValue_afterAlreadyUnchecked_isNoOp");
	}

	@Test public void setResultWithSuccessValue_afterAlreadyChecked_isNoOp() {
		IOException expectedException = new IOException("setResultWithSuccessValue_afterAlreadyChecked_isNoOp");
		fut = new PublicAbstractFuture<>(expectedException);

		String value = "setResultWithSuccessValue_afterAlreadyChecked_isNoOp";
		fut.setResult(value);

		//java.util.concurrent.Future state
		checkFutureFailedChecked(expectedException, "setResultWithSuccessValue_afterAlreadyChecked_isNoOp");
	}

	@Test public void setResultWithFuture_whenPending_resultIsPending_setsAsync() {
		fut = new PublicAbstractFuture<>();

		PublicAbstractFuture<String> async = new PublicAbstractFuture<>();
		fut.setResult(async);

		checkFutureIsAsync(async);
	}

	@Test public void setResultWithFuture_whenPending_resultIsSucceeded_isSucceeded() {
		fut = new PublicAbstractFuture<>();

		String value = "setResultWithFuture_whenPending_resultIsComplete_setsAsync";
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>(value);
		fut.setResult(async);

		checkFutureIsSuccessful(value);
	}

	@Test public void setResultWithFuture_whenPending_resultIsUnchecked_isFailed() {
		fut = new PublicAbstractFuture<>();

		ArithmeticException expectedException = new ArithmeticException(
				"setResultWithFuture_whenPending_resultIsUnchecked_isFailed");
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>(expectedException);
		fut.setResult(async);

		//java.util.concurrent.Future state
		checkFutureFailedUnchecked(expectedException, "setResultWithFuture_whenPending_resultIsUnchecked_isFailed");
	}

	@Test public void setResultWithFuture_whenPending_resultIsChecked_isFailed() {
		fut = new PublicAbstractFuture<>();

		IOException expectedException = new IOException("setResultWithFuture_whenPending_resultIsChecked_isFailed");
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>(expectedException);
		fut.setResult(async);

		checkFutureFailedChecked(
				expectedException,
				"setResultWithFuture_whenPending_resultIsChecked_isFailed");
	}

	@Test public void setResultWithFuture_whenPending_resultIsCancelled_isCancelled() {
		fut = new PublicAbstractFuture<>();

		CancellationException expectedException = new CancellationException(
				"setResultWithFuture_whenPending_resultIsCancelled_isCancelled");
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>(expectedException);
		fut.setResult(async);

		checkFutureCancelled(expectedException, "setResultWithFuture_whenPending_resultIsCancelled_isCancelled");
	}

	@Test public void setResultWithFuture_whenAsync_resultIsSucceeded_isSucceeded() {
		fut = new PublicAbstractFuture<>();
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>();
		fut.setResult(async);

		String value = "setResultWithFuture_whenAsync_resultIsSucceeded_isSucceeded";
		async.setResult(value);

		checkFutureIsSuccessful(value);
	}

	@Test public void setResultWithFuture_whenAsync_resultIsUnchecked_isFailed() {
		fut = new PublicAbstractFuture<>();
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>();
		fut.setResult(async);

		ArithmeticException expectedException = new ArithmeticException(
				"setResultWithFuture_whenAsync_resultIsUnchecked_isFailed");
		async.setException(expectedException);

		//java.util.concurrent.Future state
		checkFutureFailedUnchecked(
				expectedException,
				"java.lang.ArithmeticException: setResultWithFuture_whenAsync_resultIsUnchecked_isFailed");
	}

	@Test public void setResultWithFuture_whenAsync_resultIsChecked_isFailed() {
		fut = new PublicAbstractFuture<>();
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>();
		fut.setResult(async);

		IOException expectedException = new IOException("setResultWithFuture_whenAsync_resultIsChecked_isFailed");
		async.setException(expectedException);

		checkFutureFailedChecked(expectedException, "setResultWithFuture_whenAsync_resultIsChecked_isFailed");
	}

	@Test public void setResultWithFuture_whenAsync_resultIsCancelled_isCancelled() {
		fut = new PublicAbstractFuture<>();
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>();
		fut.setResult(async);

		CancellationException expectedException = new CancellationException(
				"setResultWithFuture_whenAsync_resultIsCancelled_isCancelled");
		async.setException(expectedException);

		checkFutureCancelled(expectedException, "setResultWithFuture_whenAsync_resultIsCancelled_isCancelled");
	}

	@Test public void setException_noInterrupt_unchecked_whenPending_isFailed() {
		ArithmeticException expectedException = new ArithmeticException(
				"setException_noInterrupt_unchecked_whenPending_isFailed");
		fut = new PublicAbstractFuture<>(expectedException);

		checkFutureFailedUnchecked(expectedException, "setException_noInterrupt_unchecked_whenPending_isFailed");
	}

	@Test public void setException_noInterrupt_checked_whenPending_isFailed() {
		IOException expectedException = new IOException("setException_noInterrupt_checked_whenPending_isFailed");
		fut = new PublicAbstractFuture<>(expectedException);

		//java.util.concurrent.Future state
		checkFutureFailedChecked(expectedException, "setException_noInterrupt_checked_whenPending_isFailed");
	}

	@Test public void setException_noInterrupt_cancelled_whenPending_isFailed() {
		CancellationException expectedException = new CancellationException(
				"setException_noInterrupt_cancelled_whenPending_isFailed");
		fut = new PublicAbstractFuture<>(expectedException);

		//java.util.concurrent.Future state
		checkFutureCancelled(expectedException, "setException_noInterrupt_cancelled_whenPending_isFailed");
	}

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
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) //PublicAbstractFuture@",
				"\n  at AbstractFutureTest$$Lambda$",
				".apply(AbstractFutureTest:0) //FutureFunction<AbstractFutureTest$$Lambda$",
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) //PublicAbstractFuture@",
				"\n  at AbstractFutureTest$$Lambda$",
				".apply(AbstractFutureTest:0) //FutureFunction<AbstractFutureTest$$Lambda$"));
	}

	private void checkFutureIsPending() {
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

	private void checkFutureIsScheduled() {
		checkNotNull(fut);
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

	private void checkFutureIsAsync(PublicAbstractFuture<String> async) {
		checkNotNull(fut);
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
		collector.checkSucceeds(fut::toString,
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

	private void checkFutureIsSuccessful(String result) {
		checkNotNull(fut);
		//java.util.concurrent.Future state
		collector.checkSucceeds(fut::exceptionNow, nullValue());
		collector.checkSucceeds(() -> fut.get(1, SECONDS), sameInstance(result));
		collector.checkSucceeds(fut::get, sameInstance(result));
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkSucceeds(fut::resultNow, sameInstance(result));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(true));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ success=",
				result,
				"]"));
		collector.checkSucceeds(fut::toString, stringContainsInOrder("PublicAbstractFuture@", "[ success=", result, "]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getScheduledTimeNanosProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, sameInstance(result));
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	private void checkFutureFailedUnchecked(ArithmeticException expectedException, String testName) {
		checkNotNull(fut);
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
				"[ failure=java.lang.ArithmeticException: ",
				testName,
				"]"));
		collector.checkSucceeds(
				fut::toString,
				stringContainsInOrder("PublicAbstractFuture@", "[ failure=java.lang.ArithmeticException: ", testName, "]"));
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

	private void checkFutureFailedChecked(IOException expectedException, String testName) {
		checkNotNull(fut);
		//java.util.concurrent.Future state
		collector.checkSucceeds(fut::exceptionNow, sameInstance(expectedException));
		collector.checkThrows(AsyncCheckedException.class, fut::get, withCause(sameInstance(expectedException)));
		collector.checkThrows(AsyncCheckedException.class,
				() -> fut.get(1, SECONDS),
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
				" //PublicAbstractFuture@", "java.io.IOException: ",
				testName));
		collector.checkSucceeds(
				fut::toString,
				stringContainsInOrder("PublicAbstractFuture@", "[ failure=java.io.IOException: ", testName, "]"));
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

	private void checkFutureCancelled(CancellationException expectedException, String testName) {
		checkNotNull(fut);
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
				"[ cancelled=java.util.concurrent.CancellationException: ",
				testName,
				"]"));
		collector.checkSucceeds(
				fut::toString,
				stringContainsInOrder("PublicAbstractFuture@",
						"[ cancelled=java.util.concurrent.CancellationException: ",
						testName,
						"]"));
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
