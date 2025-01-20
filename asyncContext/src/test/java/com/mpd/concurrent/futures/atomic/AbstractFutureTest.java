package com.mpd.concurrent.futures.atomic;

import static android.util.Log.DEBUG;
import static android.util.Log.VERBOSE;
import static com.mpd.test.WithCauseMatcher.withCause;
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
import com.mpd.test.AsyncContextRule;
import com.mpd.test.ErrorCollector;
import com.mpd.test.UncaughtExceptionRule;
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
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class) public class AbstractFutureTest {
	protected static final long NOT_SCHEDULED = Long.MIN_VALUE;

	@Rule public ErrorCollector collector = new ErrorCollector();
	@Rule public AsyncContextRule asyncContextRule = new AsyncContextRule();
	@Rule public UncaughtExceptionRule uncaughtExceptionRule = new UncaughtExceptionRule();

	@Nullable PublicAbstractFuture<String> fut;

	@Before public void enableDebugLogging() {
		AndroidBackend.setLogLevelOverride(DEBUG);
		ShadowLog.setLoggable("atomic", VERBOSE);
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
				fut.catching(Throwable.class, e -> null).end();
			} else { // If there's an unknown  listener, then all we can do is pray :(
				Log.w("atomic", fut + " has an unknown listener, and we can't forcibly end the chain");
			}
		}
	}

	@After public void ensureFutureComplete() {
		ensureFutureComplete(fut);
		this.fut = null;
	}

	@Test public void constructor_default_stateIsPending() throws Throwable {
		fut = new PublicAbstractFuture<>();

		//java.util.concurrent.Future state
		collector.checkThrows(FutureNotCompleteException.class, fut::exceptionNow);
		//collector.checkThrows(FutureNotCompleteException.class, fut::get);
		//collector.checkThrows(TimeoutException.class, ()->fut.get(1, SECONDS));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(false));
		collector.checkThrows(FutureNotCompleteException.class, fut::resultNow);
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[]"));
		collector.checkThat(fut.toString(), stringContainsInOrder("PublicAbstractFuture@", "[]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getScheduledTimeNanosProtected(), equalTo(Long.MIN_VALUE));
		collector.checkThat(fut.getResultProtected(), nullValue());
		collector.checkThat(fut.getExceptionProtected(), nullValue());
		collector.checkThat(fut.getWrappedExceptionProtected(), nullValue());
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), nullValue());
		collector.checkThat(fut.sourceClass(), equalTo(PublicAbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
	}

	@Test public void constructor_scheduled_stateIsPending() throws Throwable {
		fut = new PublicAbstractFuture<>(3, SECONDS);

		//java.util.concurrent.Future state
		collector.checkThrows(FutureNotCompleteException.class, fut::exceptionNow);
		//collector.checkThrows(FutureNotCompleteException.class, fut::get);
		//collector.checkThrows(TimeoutException.class, ()->fut.get(1, SECONDS));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(false));
		collector.checkThrows(FutureNotCompleteException.class, fut::resultNow);
		//java.util.concurrent.Delayed
		collector.checkThat(fut.getDelay(MILLISECONDS), greaterThan(0L));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.getScheduledTimeNanos(), greaterThan(0L));
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ scheduledNanos=",
				"]"));
		collector.checkThat(fut.toString(), stringContainsInOrder("PublicAbstractFuture@", "[ scheduledNanos=", "]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getScheduledTimeNanosProtected(), greaterThan(0L));
		collector.checkThat(fut.getResultProtected(), nullValue());
		collector.checkThat(fut.getExceptionProtected(), nullValue());
		collector.checkThat(fut.getWrappedExceptionProtected(), nullValue());
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), nullValue());
		collector.checkThat(fut.sourceClass(), equalTo(PublicAbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
	}

	@Test public void constructor_immediateSuccess_stateIsSuccessful() throws Throwable {
		String result = "test";
		fut = new PublicAbstractFuture<>(result);

		//java.util.concurrent.Future state
		collector.checkThat(fut.exceptionNow(), nullValue());
		collector.checkThat(fut.get(), equalTo(result));
		collector.checkThat(fut.get(1, SECONDS), equalTo(result));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.resultNow(), equalTo(result));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ success=test]"));
		collector.checkThat(fut.toString(), stringContainsInOrder("PublicAbstractFuture@", "[ success=test]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getScheduledTimeNanosProtected(), equalTo(Long.MIN_VALUE));
		collector.checkThat(fut.getResultProtected(), sameInstance(result));
		collector.checkThat(fut.getExceptionProtected(), sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkThat(fut.getWrappedExceptionProtected(), sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), nullValue());
		collector.checkThat(fut.sourceClass(), equalTo(PublicAbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
	}

	@Test public void constructor_immediateUncheckedException_stateIsFailed() throws Throwable {
		ArithmeticException expectedException = new ArithmeticException("test");
		fut = new PublicAbstractFuture<>(expectedException);

		//java.util.concurrent.Future state
		collector.checkThat(fut.exceptionNow(), sameInstance(expectedException));
		collector.checkThrows(ArithmeticException.class, fut::get, sameInstance(expectedException));
		collector.checkThrows(ArithmeticException.class, () -> fut.get(1, SECONDS), sameInstance(expectedException));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThrows(ArithmeticException.class, fut::resultNow, sameInstance(expectedException));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"[ failure=java.lang.ArithmeticException: test]"));
		collector.checkThat(fut.toString(),
				stringContainsInOrder("PublicAbstractFuture@", "[ failure=java.lang.ArithmeticException: test]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getScheduledTimeNanosProtected(), equalTo(Long.MIN_VALUE));
		collector.checkThat(fut.getResultProtected(), nullValue());
		collector.checkThat(fut.getExceptionProtected(), sameInstance(expectedException));
		collector.checkThat(fut.getWrappedExceptionProtected(), sameInstance(expectedException));
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), nullValue());
		collector.checkThat(fut.sourceClass(), equalTo(PublicAbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
	}

	@Test public void constructor_immediateCheckedException_stateIsFailed() throws Throwable {
		IOException expectedException = new IOException("test");
		fut = new PublicAbstractFuture<>(expectedException);

		//java.util.concurrent.Future state
		collector.checkThat(fut.exceptionNow(), sameInstance(expectedException));
		collector.checkThrows(AsyncCheckedException.class, fut::get, withCause(sameInstance(expectedException)));
		collector.checkThrows(AsyncCheckedException.class, () -> fut.get(1, SECONDS),
				withCause(sameInstance(expectedException)));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThrows(AsyncCheckedException.class, fut::resultNow, withCause(sameInstance(expectedException)));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"[ failure=java.io.IOException: test]"));
		collector.checkThat(fut.toString(),
				stringContainsInOrder("PublicAbstractFuture@", "[ failure=java.io.IOException: test]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getScheduledTimeNanosProtected(), equalTo(Long.MIN_VALUE));
		collector.checkThat(fut.getResultProtected(), nullValue());
		collector.checkThat(fut.getExceptionProtected(), sameInstance(expectedException));
		collector.checkThat(fut.getWrappedExceptionProtected(), withCause(sameInstance(expectedException)));
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), nullValue());
		collector.checkThat(fut.sourceClass(), equalTo(PublicAbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
	}

	@Test public void constructor_immediateCancelled_stateIsCancelled() throws Throwable {
		CancellationException expectedException = new CancellationException("test");
		fut = new PublicAbstractFuture<>(expectedException);

		//java.util.concurrent.Future state
		collector.checkThat(fut.exceptionNow(), sameInstance(expectedException));
		collector.checkThrows(CancellationException.class, fut::get, sameInstance(expectedException));
		collector.checkThrows(CancellationException.class, () -> fut.get(1, SECONDS), sameInstance(expectedException));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThrows(CancellationException.class, fut::resultNow, sameInstance(expectedException));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture",
				"(PublicAbstractFuture:0) //PublicAbstractFuture@",
				"[ cancelled=java.util.concurrent.CancellationException: test]"));
		collector.checkThat(fut.toString(),
				stringContainsInOrder("PublicAbstractFuture@",
						"[ cancelled=java.util.concurrent.CancellationException: test]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getScheduledTimeNanosProtected(), equalTo(Long.MIN_VALUE));
		collector.checkThat(fut.getResultProtected(), nullValue());
		collector.checkThat(fut.getExceptionProtected(), sameInstance(expectedException));
		collector.checkThat(fut.getWrappedExceptionProtected(), sameInstance(expectedException));
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), nullValue());
		collector.checkThat(fut.sourceClass(), equalTo(PublicAbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
	}

	@Test public void setResultWithSuccessValue_whenPending_isSuccess() throws Throwable {
		fut = new PublicAbstractFuture<>();

		String result = "test";
		fut.setResult(result);

		//java.util.concurrent.Future state
		collector.checkThat(fut.exceptionNow(), nullValue());
		collector.checkThat(fut.get(), equalTo(result));
		collector.checkThat(fut.get(1, SECONDS), equalTo(result));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.resultNow(), equalTo(result));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ success=test]"));
		collector.checkThat(fut.toString(), stringContainsInOrder("PublicAbstractFuture@", "[ success=test]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getScheduledTimeNanosProtected(), equalTo(Long.MIN_VALUE));
		collector.checkThat(fut.getResultProtected(), sameInstance(result));
		collector.checkThat(fut.getExceptionProtected(), sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkThat(fut.getWrappedExceptionProtected(), sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), nullValue());
		collector.checkThat(fut.sourceClass(), equalTo(PublicAbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
	}

	@Test public void setResultWithSuccessValue_afterAlreadySucceeded_crashes() throws Throwable {
		String result = "test";
		fut = new PublicAbstractFuture<>(result);

		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(Matchers.instanceOf(FutureSucceededTwiceException.class));
		fut.setResult(result);

		//java.util.concurrent.Future state
		collector.checkThat(fut.exceptionNow(), nullValue());
		collector.checkThat(fut.get(), equalTo(result));
		collector.checkThat(fut.get(1, SECONDS), equalTo(result));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.resultNow(), equalTo(result));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ success=test]"));
		collector.checkThat(fut.toString(), stringContainsInOrder("PublicAbstractFuture@", "[ success=test]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getScheduledTimeNanosProtected(), equalTo(Long.MIN_VALUE));
		collector.checkThat(fut.getResultProtected(), sameInstance(result));
		collector.checkThat(fut.getExceptionProtected(), sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkThat(fut.getWrappedExceptionProtected(), sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), nullValue());
		collector.checkThat(fut.sourceClass(), equalTo(PublicAbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
	}

	@Test public void setResultWithSuccessValue_afterAlreadyFailed_isNoOp() throws Throwable {
		ArithmeticException expectedException = new ArithmeticException("test");
		fut = new PublicAbstractFuture<>(expectedException);

		String result = "test";
		fut.setResult(result);

		//java.util.concurrent.Future state
		collector.checkThat(fut.exceptionNow(), sameInstance(expectedException));
		collector.checkThrows(ArithmeticException.class, fut::get, sameInstance(expectedException));
		collector.checkThrows(ArithmeticException.class, () -> fut.get(1, SECONDS), sameInstance(expectedException));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThrows(ArithmeticException.class, fut::resultNow, sameInstance(expectedException));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0)",
				" //PublicAbstractFuture@",
				"[ failure=java.lang.ArithmeticException: test]"));
		collector.checkThat(fut.toString(),
				stringContainsInOrder("PublicAbstractFuture@", "[ failure=java.lang.ArithmeticException: test]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getScheduledTimeNanosProtected(), equalTo(Long.MIN_VALUE));
		collector.checkThat(fut.getResultProtected(), nullValue());
		collector.checkThat(fut.getExceptionProtected(), sameInstance(expectedException));
		collector.checkThat(fut.getWrappedExceptionProtected(), sameInstance(expectedException));
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), nullValue());
		collector.checkThat(fut.sourceClass(), equalTo(PublicAbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
	}

	@Test public void setResultWithFuture_whenPending_resultIsPending_setsAsync() throws Throwable {
		fut = new PublicAbstractFuture<>();

		PublicAbstractFuture<String> async = new PublicAbstractFuture<>();
		fut.setResult(async);

		//java.util.concurrent.Future state
		collector.checkThrows(FutureNotCompleteException.class, fut::exceptionNow);
		//collector.checkThrows(FutureNotCompleteException.class, fut::get);
		//collector.checkThrows(TimeoutException.class, ()->fut.get(1, SECONDS));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(false));
		collector.checkThrows(FutureNotCompleteException.class, fut::resultNow);
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThrows(UnsupportedOperationException.class, fut::getScheduledTimeNanos);
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest.PublicAbstractFuture(PublicAbstractFuture:0) ",
				"//PublicAbstractFuture@",
				"[ setAsync=PublicAbstractFuture@",
				"]"));
		collector.checkThat(fut.toString(),
				stringContainsInOrder("PublicAbstractFuture@", "[ setAsync" + "=PublicAbstractFuture@", "]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), sameInstance(async));
		collector.checkThat(fut.getScheduledTimeNanosProtected(), equalTo(Long.MIN_VALUE));
		collector.checkThat(fut.getResultProtected(), nullValue());
		collector.checkThat(fut.getExceptionProtected(), nullValue());
		collector.checkThat(fut.getWrappedExceptionProtected(), nullValue());
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), nullValue());
		collector.checkThat(fut.sourceClass(), equalTo(PublicAbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
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

	@Test public void toString_recursiveFuture_limitedDepth() throws Throwable {
		fut = new PublicAbstractFuture<>();
		Future<String> fut2 = fut.transform(s -> s);

		fut.setResult(fut2);

		collector.checkThat(fut.toString(),
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

		public PublicAbstractFuture(@Nullable O result) {
			super(result);
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
