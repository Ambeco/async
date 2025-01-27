package com.mpd.concurrent.futures.atomic;

import static android.util.Log.DEBUG;
import static android.util.Log.VERBOSE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.mpd.concurrent.executors.MoreExecutors.directExecutor;
import static com.mpd.concurrent.futures.Future.MAY_INTERRUPT;
import static com.mpd.test.matchers.WithCauseMatcher.withCause;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.mockito.Mockito.mock;

import android.annotation.SuppressLint;
import android.os.Build.VERSION_CODES;
import android.util.Log;
import androidx.annotation.RequiresApi;
import androidx.test.filters.SdkSuppress;
import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.Future.AsyncCheckedException;
import com.mpd.concurrent.futures.Future.FutureNotCompleteException;
import com.mpd.concurrent.futures.Future.FutureSucceededTwiceException;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.atomic.AbstractFuture.SetExceptionCalledAfterCompleteException;
import com.mpd.concurrent.futures.atomic.AbstractFuture.SetResultCalledAfterFailureException;
import com.mpd.concurrent.futures.atomic.AbstractFuture.SetResultCalledAfterSuccessException;
import com.mpd.concurrent.futures.atomic.AbstractFuture.SetResultCalledTwiceException;
import com.mpd.test.MockedCall;
import com.mpd.test.matchers.MockedCallMatcher;
import com.mpd.test.rules.AsyncContextRule;
import com.mpd.test.rules.ErrorCollector;
import com.mpd.test.rules.UncaughtExceptionRule;
import com.tbohne.android.flogger.backend.AndroidBackend;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.junit.rules.TimeoutRule;
import org.robolectric.shadows.ShadowLog;

/**
 * @noinspection deprecation
 */
@RunWith(RobolectricTestRunner.class) public class AbstractFutureTest {

	@Rule(order = 0) public UncaughtExceptionRule uncaughtExceptionRule = new UncaughtExceptionRule();
	@Rule(order = 1) public ErrorCollector collector = new ErrorCollector();
	@Rule(order = 50) public TestRule timeoutRule = new DisableOnDebug(TimeoutRule.seconds(10));
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
			fut.cancel(MAY_INTERRUPT); // cancel and interrupt anything in progress
			for (int i = 0; i < 10 && fut.getListener() instanceof AbstractFuture; i++) {
				fut = (AbstractFuture<?>) fut.getListener();  // traverse down chain up to a depth of 10 to find the end
			}
			if (fut.getListener() instanceof EndListener) { // if it's an end listener, then we don't need to do anything
				Log.d("atomic", fut + " already has an EndListener, so is \"safe\" to leak");
			} else if (fut.getListener() instanceof AbstractFuture.ListenerAlreadyDispatched) {
				Log.d("atomic", fut + " already dispatched to listener. Hopefully the listener was ended?");
			} else if (fut.getListener() == null) { // at the end of the chain, then swallow exceptions and end.
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
		long systemNanoTime = System.nanoTime();

		fut = new PublicAbstractFuture<>(3, SECONDS);

		// Since time actually passes during execution, and we gave a relative time, then the scheduled time won't be
		// *exactly* 3 seconds offset. but as long as it's within 1 second, then that means the internal logic isn't off by
		// a factor of 10 or anything.
		collector.checkSucceeds(fut::getSystemNanoTimeProtected, greaterThan(systemNanoTime + SECONDS.toNanos(3)));
		collector.checkSucceeds(fut::getSystemNanoTimeProtected, lessThan(systemNanoTime + SECONDS.toNanos(4)));
		checkFutureIsScheduled();
	}

	@SdkSuppress(minSdkVersion = VERSION_CODES.O) @Test public void constructor_instant_stateIsScheduled() {
		long systemNanoTime = System.nanoTime();
		Instant scheduledInstant = Instant.now().plus(60, ChronoUnit.SECONDS); // schedule for 60 seconds from now

		fut = new PublicAbstractFuture<>(scheduledInstant);

		// Future uses System.nanoTime internally, so even Instant requires a clock shift and will be slightly off. But as
		// long as it's within a a few seconds, then that means the internal logic isn't off by a factor of 10 or anything.
		long futureSystemNanoTime = fut.getSystemNanoTimeProtected();
		collector.checkThat(futureSystemNanoTime, greaterThan(systemNanoTime + SECONDS.toNanos(50)));
		collector.checkThat(futureSystemNanoTime, lessThan(systemNanoTime + SECONDS.toNanos(70)));
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

		checkFutureFailedUnchecked(expectedException, "setResultWithSuccessValue_afterAlreadyUnchecked_isNoOp");
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

		checkFutureFailedChecked(expectedException, "setResultWithFuture_whenPending_resultIsChecked_isFailed");
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
		checkFutureFailedUnchecked(expectedException, "setResultWithFuture_whenAsync_resultIsUnchecked_isFailed");
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

	@Test public void setResultWithFuture_whenAsync_crashes() {
		String result = "setResultWithFuture_whenAsync_crashes";
		fut = new PublicAbstractFuture<>();
		PublicAbstractFuture<String> async1 = new PublicAbstractFuture<>();
		fut.setResult(async1);
		ArithmeticException asyncException2 = new ArithmeticException("setResultWithFuture_whenAsync_crashes");
		PublicAbstractFuture<String> async2 = new PublicAbstractFuture<>(asyncException2);

		Matcher<Throwable> matcher = Matchers.allOf(Matchers.instanceOf(SetResultCalledTwiceException.class),
				withCause(sameInstance(asyncException2)));
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(matcher);
		fut.setResult(async2);

		checkFutureFailedUnchecked(result, SetResultCalledTwiceException.class, matcher);
		async1.cancel(/*interrupt=*/true);
	}

	@Test public void setResultWithFuture_whenSucceeded_crashes() {
		String result = "setResultWithFuture_whenSucceeded_crashes";
		fut = new PublicAbstractFuture<>(result);
		ArithmeticException asyncException = new ArithmeticException("setResultWithFuture_whenSucceeded_crashes");
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>(asyncException);

		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(Matchers.allOf(Matchers.instanceOf(
				SetResultCalledAfterSuccessException.class), withCause(sameInstance(asyncException))));
		fut.setResult(async);

		checkFutureIsSuccessful(result);
	}

	@Test public void setResultWithFuture_whenFailed_asyncAlsoFailed_crashes() {
		ArithmeticException firstException = new ArithmeticException(
				"setResultWithFuture_whenFailed_asyncAlsoFailed_crashes");
		fut = new PublicAbstractFuture<>(firstException);
		ArithmeticException asyncException = new ArithmeticException(
				"setResultWithFuture_whenFailed_asyncAlsoFailed_crashes_2");
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>(asyncException);

		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(Matchers.allOf(Matchers.instanceOf(
				SetResultCalledAfterFailureException.class), withCause(sameInstance(asyncException))));
		fut.setResult(async);

		checkFutureFailedUnchecked(firstException, "setResultWithFuture_whenFailed_asyncAlsoFailed_crashes");
	}

	@Test public void setResultWithFuture_whenFailed_asyncSucceeded_noOps() {
		ArithmeticException firstException = new ArithmeticException("setResultWithFuture_whenFailed_asyncSucceeded_noOps");
		fut = new PublicAbstractFuture<>(firstException);
		String result2 = "setResultWithFuture_whenFailed_asyncSucceeded_noOps_2";
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>(result2);

		fut.setResult(async);

		checkFutureFailedUnchecked(firstException, "setResultWithFuture_whenFailed_asyncSucceeded_noOps");
	}

	@Test public void setException_unchecked_whenPending_isFailed() {
		ArithmeticException expectedException = new ArithmeticException("setException_unchecked_whenPending_isFailed");
		fut = new PublicAbstractFuture<>();

		fut.setException(expectedException);

		checkFutureFailedUnchecked(expectedException, "setException_unchecked_whenPending_isFailed");
	}

	@Test public void setException_checked_whenPending_isFailed() {
		IOException expectedException = new IOException("setException_checked_whenPending_isFailed");
		fut = new PublicAbstractFuture<>();

		fut.setException(expectedException);

		checkFutureFailedChecked(expectedException, "setException_checked_whenPending_isFailed");
	}

	@Test public void setException_cancelled_whenPending_isFailed() {
		CancellationException expectedException = new CancellationException("setException_cancelled_whenPending_isFailed");
		fut = new PublicAbstractFuture<>();

		fut.setException(expectedException);

		checkFutureCancelled(expectedException, "setException_cancelled_whenPending_isFailed");
	}

	@Test public void setException_unchecked_whenAsync_isFailed() {
		ArithmeticException expectedException = new ArithmeticException("setException_unchecked_whenPending_isFailed");
		fut = new PublicAbstractFuture<>();
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>();
		fut.setResult(async);

		fut.setException(expectedException);

		checkFutureFailedUnchecked(expectedException, "setException_unchecked_whenPending_isFailed");
		async.cancel(MAY_INTERRUPT);
	}

	@Test public void setException_checked_whenAsync_isFailed() {
		IOException expectedException = new IOException("setException_checked_whenPending_isFailed");
		fut = new PublicAbstractFuture<>();
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>();
		fut.setResult(async);

		fut.setException(expectedException);

		checkFutureFailedChecked(expectedException, "setException_checked_whenPending_isFailed");
		async.cancel(MAY_INTERRUPT);
	}

	@Test public void setException_cancelled_whenAsync_isFailed() {
		CancellationException expectedException = new CancellationException("setException_cancelled_whenPending_isFailed");
		fut = new PublicAbstractFuture<>();
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>();
		fut.setResult(async);

		fut.setException(expectedException);

		checkFutureCancelled(expectedException, "setException_cancelled_whenPending_isFailed");
		async.cancel(MAY_INTERRUPT);
	}

	@Test public void setException_unchecked_whenSucceeded_crashes() {
		fut = new PublicAbstractFuture<>("setException_unchecked_whenSucceeded_crashes");

		ArithmeticException expectedException = new ArithmeticException("setException_unchecked_whenSucceeded_crashes");
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(Matchers.allOf(Matchers.instanceOf(
				SetExceptionCalledAfterCompleteException.class), withCause(sameInstance(expectedException))));
		fut.setException(expectedException);

		checkFutureIsSuccessful("setException_unchecked_whenSucceeded_crashes");
	}

	@Test public void setException_checked_whenSucceeded_crashes() {
		fut = new PublicAbstractFuture<>("setException_checked_whenSucceeded_crashes");

		IOException expectedException = new IOException("setException_checked_whenSucceeded_crashes");
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(Matchers.allOf(Matchers.instanceOf(
				SetExceptionCalledAfterCompleteException.class), withCause(sameInstance(expectedException))));
		fut.setException(expectedException);

		checkFutureIsSuccessful("setException_checked_whenSucceeded_crashes");
	}

	@Test public void setException_cancelled_whenSucceeded_NoOps() {
		fut = new PublicAbstractFuture<>("setException_cancelled_whenSucceeded_NoOps");

		CancellationException expectedException = new CancellationException("setException_cancelled_whenSucceeded_NoOps_2");
		fut.setException(expectedException);

		checkFutureIsSuccessful("setException_cancelled_whenSucceeded_NoOps");
	}

	@Test public void setException_unchecked_whenFailed_crashes() {
		ArithmeticException e = new ArithmeticException("setException_unchecked_whenFailed_crashes");
		fut = new PublicAbstractFuture<>(e);

		ArithmeticException secondException = new ArithmeticException("setException_unchecked_whenFailed_crashes_2");
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(Matchers.allOf(Matchers.instanceOf(
				SetExceptionCalledAfterCompleteException.class), withCause(sameInstance(secondException))));
		fut.setException(secondException);

		checkFutureFailedUnchecked(e, "setException_unchecked_whenFailed_crashes");
	}

	@Test public void setException_checked_whenFailed_crashes() {
		ArithmeticException e = new ArithmeticException("setException_checked_whenFailed_crashes");
		fut = new PublicAbstractFuture<>(e);

		IOException secondException = new IOException("setException_checked_whenFailed_crashes_2");
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(Matchers.allOf(Matchers.instanceOf(
				SetExceptionCalledAfterCompleteException.class), withCause(sameInstance(secondException))));
		fut.setException(secondException);

		checkFutureFailedUnchecked(e, "setException_checked_whenFailed_crashes");
	}

	@Test public void setException_cancelled_whenFailed_NoOps() {
		ArithmeticException e = new ArithmeticException("setException_cancelled_whenFailed_NoOps");
		fut = new PublicAbstractFuture<>(e);

		CancellationException secondException = new CancellationException("setException_cancelled_whenFailed_NoOps_2");
		fut.setException(secondException);

		checkFutureFailedUnchecked(e, "setException_cancelled_whenFailed_NoOps");
	}

	@Test public void setException_withInterrupt_whenPending_callsInterruptTask() {
		ArithmeticException expectedException = new ArithmeticException(
				"setException_withInterrupt_whenPending_callsInterruptTask");
		fut = new PublicAbstractFuture<>();

		fut.setException(expectedException, MAY_INTERRUPT);

		collector.checkThat(fut.mockedCalls,
				contains(new MockedCallMatcher<>(fut,
						PublicAbstractFuture.interruptTaskMethod,
						any(Void.class),
						sameInstance(expectedException))));
	}

	@Test public void setException_withCancellation_andInterrupt_whenAsync_interruptsAsync() {
		CancellationException expectedException = new CancellationException(
				"setException_withCancellation_andInterrupt_whenAsync_interruptsAsync");
		fut = new PublicAbstractFuture<>();
		PublicAbstractFuture<String> failedListener = new PublicAbstractFuture<>();
		failedListener.end();
		failedListener.setResult(fut);

		failedListener.setException(expectedException, MAY_INTERRUPT);

		collector.checkThat(fut.mockedCalls,
				hasItem(new MockedCallMatcher<>(fut,
						PublicAbstractFuture.onCancelledMethod,
						any(Void.class),
						sameInstance(expectedException),
						equalTo(true))));
		collector.checkThat(fut.mockedCalls,
				hasItem(new MockedCallMatcher<>(fut,
						PublicAbstractFuture.interruptTaskMethod,
						any(Void.class),
						sameInstance(expectedException))));
		//java.lang.Object
		collector.checkSucceeds(fut::toString, stringContainsInOrder(
				"PublicAbstractFuture@",
				"[ cancelled=java.util.concurrent.CancellationException: setException_withCancellation_andInterrupt_whenAsync_interruptsAsync"));
		//java.util.concurrent.Future
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
		collector.checkThrows(UnsupportedOperationException.class, fut::getSystemNanoTime);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"[ cancelled=java.util.concurrent.CancellationException: setException_withCancellation_andInterrupt_whenAsync_interruptsAsync]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getSystemNanoTimeProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getInterrupt, sameInstance(expectedException));
		collector.checkSucceeds(fut::getListener, instanceOf(AbstractFuture.ListenerAlreadyDispatched.class));
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void setException_withUnchecked_andInterrupt_whenAsync_interruptsAsync() {
		ArithmeticException expectedException = new ArithmeticException(
				"setException_withUnchecked_andInterrupt_whenAsync_interruptsAsync");
		fut = new PublicAbstractFuture<>();
		PublicAbstractFuture<String> failedListener = new PublicAbstractFuture<>();
		failedListener.catching(ArithmeticException.class, e -> null).end();
		failedListener.setResult(fut);

		failedListener.setException(expectedException, MAY_INTERRUPT);

		collector.checkThat(fut.mockedCalls,
				hasItem(new MockedCallMatcher<>(fut,
						PublicAbstractFuture.interruptTaskMethod,
						any(Void.class),
						sameInstance(expectedException))));
		//java.lang.Object
		collector.checkSucceeds(fut::toString, stringContainsInOrder(
				"PublicAbstractFuture@",
				"[ failure=java.lang.ArithmeticException: setException_withUnchecked_andInterrupt_whenAsync_interruptsAsync]"));
		//java.util.concurrent.Future
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
		collector.checkThrows(UnsupportedOperationException.class, fut::getSystemNanoTime);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"[ failure=java.lang.ArithmeticException: setException_withUnchecked_andInterrupt_whenAsync_interruptsAsync]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getSystemNanoTimeProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getInterrupt, sameInstance(expectedException));
		collector.checkSucceeds(fut::getListener, instanceOf(AbstractFuture.ListenerAlreadyDispatched.class));
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	@Test public void cancel_whenPending_isCancelled() {
		fut = new PublicAbstractFuture<>();

		CancellationException cancellation = new CancellationException("cancel_whenPending_isCancelled");
		fut.cancel(cancellation, MAY_INTERRUPT);

		collector.checkThat(fut.mockedCalls, hasItem(new MockedCallMatcher<>(fut,
				PublicAbstractFuture.onCancelledMethod,
				any(Void.class),
				sameInstance(cancellation),
				equalTo(MAY_INTERRUPT))));
		checkFutureCancelled(cancellation, "cancel_whenPending_isCancelled", sameInstance(cancellation));
	}

	@Test public void cancel_whenAsync_isCancelled() {
		fut = new PublicAbstractFuture<>();
		PublicAbstractFuture<String> async = new PublicAbstractFuture<>();
		fut.setResult(async);

		CancellationException cancellation = new CancellationException("cancel_whenAsync_isCancelled");
		fut.cancel(cancellation, MAY_INTERRUPT);

		collector.checkThat(fut.mockedCalls, hasItem(new MockedCallMatcher<>(fut,
				PublicAbstractFuture.onCancelledMethod,
				any(Void.class),
				sameInstance(cancellation),
				equalTo(MAY_INTERRUPT))));
		checkFutureCancelled(cancellation, "cancel_whenAsync_isCancelled", sameInstance(cancellation));
	}

	@Test public void cancel_whenSucceeded_staysSucceeded() {
		fut = new PublicAbstractFuture<>("cancel_whenSucceeded_staysSucceeded");

		CancellationException cancellation = new CancellationException("cancel_whenSucceeded_staysSucceeded_2");
		fut.cancel(cancellation, MAY_INTERRUPT);

		collector.checkThat(fut.mockedCalls, Matchers.not(hasItem(new MockedCallMatcher<>(fut,
				PublicAbstractFuture.onCancelledMethod,
				any(Void.class),
				sameInstance(cancellation),
				equalTo(MAY_INTERRUPT)))));
		checkFutureIsSuccessful("cancel_whenSucceeded_staysSucceeded");
	}

	@Test public void cancel_whenFailed_staysFailed() {
		ArithmeticException expectedException = new ArithmeticException("cancel_whenFailed_staysFailed");
		fut = new PublicAbstractFuture<>(expectedException);

		CancellationException cancellation = new CancellationException("cancel_whenFailed_staysFailed_2");
		fut.cancel(cancellation, MAY_INTERRUPT);

		collector.checkThat(fut.mockedCalls, Matchers.not(hasItem(new MockedCallMatcher<>(fut,
				PublicAbstractFuture.onCancelledMethod,
				any(Void.class),
				sameInstance(cancellation),
				equalTo(MAY_INTERRUPT)))));
		checkFutureFailedUnchecked(expectedException, "cancel_whenFailed_staysFailed");
	}

	@Test public void onFutureFailed_whenAlreadyFailed_isCalled() {
		ArithmeticException expectedException = new ArithmeticException("onFutureFailed_whenAlreadyFailed_isCalled");
		fut = new PublicAbstractFuture<>(expectedException);
		FutureListener<String> mockListener = mock(FutureListener.class);

		fut.setListener(mockListener);

		Mockito.verify(mockListener).onFutureFailed(fut, expectedException, false);
	}

	@Test public void onFutureFailed_whenBecomeFailed_isCalled() {
		fut = new PublicAbstractFuture<>();
		FutureListener<String> mockListener = mock(FutureListener.class);
		fut.setListener(mockListener);

		ArithmeticException expectedException = new ArithmeticException("onFutureFailed_whenBecomeFailed_isCalled");
		fut.setException(expectedException);

		Mockito.verify(mockListener).onFutureFailed(fut, expectedException, false);
	}

	@Test public void onFutureFailed_whenPending_notCalled() {
		fut = new PublicAbstractFuture<>();
		FutureListener<String> mockListener = mock(FutureListener.class);

		fut.setListener(mockListener);

		Mockito.verify(mockListener, Mockito.never()).onFutureSucceeded(Mockito.any(Future.class), Mockito.anyString());
		Mockito.verify(mockListener, Mockito.never()).onFutureFailed(Mockito.any(Future.class),
				Mockito.any(Throwable.class),
				Mockito.anyBoolean());
	}

	@Test public void onFutureFailed_whenAsync_notCalled() {
		fut = new PublicAbstractFuture<>();
		FutureListener<String> mockListener = mock(FutureListener.class);
		fut.setListener(mockListener);

		PublicAbstractFuture<String> async = new PublicAbstractFuture<>();
		fut.setResult(async);

		Mockito.verify(mockListener, Mockito.never()).onFutureSucceeded(Mockito.any(Future.class), Mockito.anyString());
		Mockito.verify(mockListener, Mockito.never()).onFutureFailed(Mockito.any(Future.class),
				Mockito.any(Throwable.class),
				Mockito.anyBoolean());
	}

	@SdkSuppress(minSdkVersion = VERSION_CODES.O) @Test public void compareTo() {
		Instant now = Instant.now();
		fut = new PublicAbstractFuture<>(now);
		PublicAbstractFuture<String> later = new PublicAbstractFuture<>(now.plus(60, ChronoUnit.SECONDS));
		//PublicAbstractFuture<String> equal = new PublicAbstractFuture<>(now);
		PublicAbstractFuture<String> unset = new PublicAbstractFuture<>();

		collector.checkSucceeds(() -> fut.compareTo(later), equalTo(-1));
		collector.checkSucceeds(() -> later.compareTo(fut), equalTo(1));
		// Equals tests fail due to shift from Instant to System.nanoTime
		//collector.checkSucceeds(() -> fut.compareTo(equal), equalTo(0));
		//collector.checkSucceeds(() -> equal.compareTo(fut), equalTo(0));
		collector.checkSucceeds(() -> fut.compareTo(unset), equalTo(1));
		collector.checkSucceeds(() -> unset.compareTo(fut), equalTo(-1));

		later.cancel(MAY_INTERRUPT);
		//equal.cancel(MAY_INTERRUPT);
		unset.cancel(MAY_INTERRUPT);
	}

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
		//java.lang.Object
		collector.checkSucceeds(fut::toString, stringContainsInOrder("PublicAbstractFuture@", "[]"));
		//java.util.concurrent.Future
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
		collector.checkThrows(UnsupportedOperationException.class, fut::getSystemNanoTime);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getSystemNanoTimeProtected, equalTo(Long.MIN_VALUE));
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
		//java.lang.Object
		collector.checkSucceeds(
				fut::toString,
				stringContainsInOrder("PublicAbstractFuture@", "[ scheduledSystemNanoTime=", "]"));
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
		collector.checkSucceeds(fut::getSystemNanoTime, greaterThan(0L));
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@", "[ scheduledSystemNanoTime=",
				"]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getSystemNanoTimeProtected, greaterThan(0L));
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
		//java.lang.Object
		collector.checkSucceeds(fut::toString,
				stringContainsInOrder("PublicAbstractFuture@", "[ setAsync" + "=PublicAbstractFuture@", "]"));
		//java.util.concurrent.Future
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
		collector.checkThrows(UnsupportedOperationException.class, fut::getSystemNanoTime);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ setAsync=PublicAbstractFuture@",
				"]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, sameInstance(async));
		collector.checkSucceeds(fut::getSystemNanoTimeProtected, equalTo(Long.MIN_VALUE));
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
		//java.lang.Object
		collector.checkSucceeds(fut::toString, stringContainsInOrder("PublicAbstractFuture@", "[ success=", result, "]"));
		//java.util.concurrent.Future
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
		collector.checkThrows(UnsupportedOperationException.class, fut::getSystemNanoTime);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ success=",
				result,
				"]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getSystemNanoTimeProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, sameInstance(result));
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}


	private <E extends Throwable> void checkFutureFailedUnchecked(
			String testName, Class<E> clazz, Matcher<Throwable> failure)
	{
		checkFutureFailedUnchecked(testName, clazz, failure, nullValue());
	}

	private <E extends Throwable> void checkFutureFailedUnchecked(
			String testName, Class<E> clazz, Matcher<Throwable> failure, Matcher<? super Throwable> interrupted)
	{
		checkNotNull(fut);
		//java.lang.Object
		collector.checkSucceeds(fut::toString,
				stringContainsInOrder("PublicAbstractFuture@", "[ failure=", clazz.getName(), ": ", testName, "]"));
		//java.util.concurrent.Future
		collector.checkSucceeds(fut::exceptionNow, failure);
		collector.checkThrows(Throwable.class, fut::get, failure);
		collector.checkThrows(Throwable.class, () -> fut.get(1, SECONDS), failure);
		collector.checkSucceeds(fut::isCancelled, equalTo(false));
		collector.checkSucceeds(fut::isDone, equalTo(true));
		collector.checkThrows(Throwable.class, fut::resultNow, failure);
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkSucceeds(fut::isSuccessful, equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getSystemNanoTime);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"[ failure=",
				clazz.getName(),
				": ",
				testName,
				"]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getSystemNanoTimeProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, failure);
		collector.checkSucceeds(fut::getWrappedExceptionProtected, failure);
		collector.checkSucceeds(fut::getInterrupt, interrupted);
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	private <E extends Throwable> void checkFutureFailedUnchecked(E expectedException, String testName) {
		checkFutureFailedUnchecked(testName, expectedException.getClass(), sameInstance(expectedException));
	}

	private void checkFutureFailedChecked(IOException expectedException, String testName) {
		checkNotNull(fut);
		//java.lang.Object
		collector.checkSucceeds(fut::toString,
				stringContainsInOrder("PublicAbstractFuture@", "[ failure=java.io.IOException: ", testName, "]"));
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
		collector.checkThrows(UnsupportedOperationException.class, fut::getSystemNanoTime);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"java.io.IOException: ",
				testName));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getSystemNanoTimeProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, withCause(sameInstance(expectedException)));
		collector.checkSucceeds(fut::getInterrupt, nullValue());
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	private void checkFutureCancelled(CancellationException expectedException, String testName) {
		checkFutureCancelled(expectedException, testName, nullValue());
	}

	private void checkFutureCancelled(
			CancellationException expectedException, String testName, Matcher<? super Throwable> interrupted)
	{
		checkNotNull(fut);
		//java.lang.Object
		collector.checkSucceeds(fut::toString,
				stringContainsInOrder("PublicAbstractFuture@",
						"[ cancelled=java.util.concurrent.CancellationException: ",
						testName,
						"]"));
		//java.util.concurrent.Future
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
		collector.checkThrows(UnsupportedOperationException.class, fut::getSystemNanoTime);
		collector.checkSucceeds(() -> fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"[ cancelled=java.util.concurrent.CancellationException: ",
				testName,
				"]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkSucceeds(fut::getSetAsync, nullValue());
		collector.checkSucceeds(fut::getSystemNanoTimeProtected, equalTo(Long.MIN_VALUE));
		collector.checkSucceeds(fut::getResultProtected, nullValue());
		collector.checkSucceeds(fut::getExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getWrappedExceptionProtected, sameInstance(expectedException));
		collector.checkSucceeds(fut::getInterrupt, interrupted);
		collector.checkSucceeds(fut::getListener, nullValue());
		collector.checkSucceeds(fut::sourceClass, equalTo(PublicAbstractFuture.class));
		collector.checkSucceeds(fut::sourceMethodName, nullValue());
	}

	/**
	 * AbstractFuture where all methods are public, so we can mess with them in test
	 */
	private static class PublicAbstractFuture<O> extends AbstractFuture<O> {
		static final Method onCancelledMethod = getMethod("onCancelled", CancellationException.class, boolean.class);
		static final Method interruptTaskMethod = getMethod("interruptTask", Throwable.class);

		public final ArrayList<MockedCall<PublicAbstractFuture<O>>> mockedCalls = new ArrayList<>();

		public PublicAbstractFuture() {
			super();
		}

		@SuppressLint("UseSdkSuppress") @RequiresApi(VERSION_CODES.O) public PublicAbstractFuture(Instant time) {
			super(time);
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

		@Override public long getSystemNanoTimeProtected() {
			return super.getSystemNanoTimeProtected();
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

		private static Method getMethod(String name, Class<?>... parameterTypes) {
			try {
				return PublicAbstractFuture.class.getMethod(name, parameterTypes);
			} catch (NoSuchMethodException e) {
				throw new RuntimeException(e);
			}
		}

		@Override public void onCancelled(CancellationException exception, boolean mayInterruptIfRunning) {
			super.onCancelled(exception, mayInterruptIfRunning);
			mockedCalls.add(new MockedCall<>(this, onCancelledMethod, null, exception, mayInterruptIfRunning));
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

		@Override public void interruptTask(Throwable exception) {
			super.interruptTask(exception);
			mockedCalls.add(new MockedCall<>(this, interruptTaskMethod, null, exception));
		}
	}
}
