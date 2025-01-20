package com.mpd.concurrent.futures;

import static android.util.Log.DEBUG;
import static android.util.Log.VERBOSE;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;

import com.mpd.concurrent.futures.Future.AsyncCheckedException;
import com.mpd.concurrent.futures.atomic.AbstractFuture;
import com.mpd.concurrent.futures.atomic.AbstractFutureTest;
import com.mpd.test.AsyncContextRule;
import com.mpd.test.ErrorCollector;
import com.mpd.test.UncaughtExceptionRule;
import com.tbohne.android.flogger.backend.AndroidBackend;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class) public class ImmediateFutureTest {
	@Rule public ErrorCollector collector = new ErrorCollector();
	@Rule public AsyncContextRule asyncContextRule = new AsyncContextRule();
	@Rule public UncaughtExceptionRule uncaughtExceptionRule = new UncaughtExceptionRule();

	@Nullable Future<String> fut;

	@Before public void enableDebugLogging() {
		AndroidBackend.setLogLevelOverride(DEBUG);
		ShadowLog.setLoggable("atomic", VERBOSE);
		ShadowLog.setLoggable("futures", VERBOSE);
	}

	@After public void ensureFutureComplete() {
		AbstractFutureTest.ensureFutureComplete((AbstractFuture<?>) fut);
		this.fut = null;
	}

	@Test public void forString_state_isSuccessful() throws Throwable {
		fut = Futures.immediateFuture("forString_state_isSuccessful");

		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), nullValue());
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
	}

	@Test public void forString_get_isSuccessful() throws Throwable {
		fut = Futures.immediateFuture("forString_get_isSuccessful");

		collector.checkThat(fut.resultNow(), equalTo("forString_get_isSuccessful"));
		collector.checkThat(fut.get(), equalTo("forString_get_isSuccessful"));
		collector.checkThat(fut.get(1, DAYS), equalTo("forString_get_isSuccessful"));
	}

	@Test public void forString_toString_correct() throws Throwable {
		fut = Futures.immediateFuture("forString_toString_correct");

		collector.checkThat(
				fut.toString(),
				matchesPattern("ImmediateFuture@\\d{1,20}\\[ success=forString_toString_correct]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture\\("
						+ "ImmediateFuture:0\\) //ImmediateFuture@\\d{1,20}\\[ success=forString_toString_correct]$"));
	}

	@Test public void forString_cancel_isNoOp() throws Throwable {
		fut = Futures.immediateFuture("forString_cancel_isNoOp");

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.exceptionNow(), nullValue());
	}

	@Test public void forString_setException_crashes() throws Throwable {
		fut = Futures.immediateFuture("forString_setException_crashes_imm");

		ArithmeticException secondException = new ArithmeticException("forString_setException_crashes_exc");
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(secondException);
		fut.setException(secondException);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), nullValue());
	}

	@Test public void forUnchecked_state_isFailed() throws Throwable {
		ArithmeticException e = new ArithmeticException("forUnchecked_state_isFailed");
		fut = Futures.immediateFailedFuture(e);

		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
	}

	@Test public void forUnchecked_get_throws() throws Throwable {
		ArithmeticException expect = new ArithmeticException("forUnchecked_get_throws");
		fut = Futures.immediateFailedFuture(expect);

		collector.checkThrows(ArithmeticException.class, fut::resultNow, sameInstance(expect));
		collector.checkThrows(ArithmeticException.class, fut::get, sameInstance(expect));
		collector.checkThrows(ArithmeticException.class, () -> fut.get(1, DAYS), sameInstance(expect));
	}

	@Test public void forUnchecked_toString_correct() throws Throwable {
		ArithmeticException expect = new ArithmeticException("forUnchecked_toString_correct");
		fut = Futures.immediateFailedFuture(expect);

		collector.checkThat(fut.toString(),
				matchesPattern(
						"ImmediateFuture@\\d{1,20}\\[ failure=java.lang.ArithmeticException: forUnchecked_toString_correct]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture\\("
						+ "ImmediateFuture:0\\) //ImmediateFuture@\\d{1,20}\\[ failure=java.lang.ArithmeticException: forUnchecked_toString_correct]$"));
	}

	@Test public void forUnchecked_cancel_isNoOp() throws Throwable {
		ArithmeticException expect = new ArithmeticException("forUnchecked_cancel_isNoOp");
		fut = Futures.immediateFailedFuture(expect);

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forUnchecked_setException_crashes() throws Throwable {
		ArithmeticException expect = new ArithmeticException("forUnchecked_setException_crashes_expect");
		fut = Futures.immediateFailedFuture(expect);

		IOException secondException = new IOException("forUnchecked_setException_crashes_second");
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(secondException);
		fut.setException(secondException);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forChecked_state_isFailed() throws Throwable {
		IOException e = new IOException("forChecked_state_isFailed");
		fut = Futures.immediateFailedFuture(e);

		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
	}

	@Test public void forChecked_get_throws() throws Throwable {
		IOException expect = new IOException("forChecked_get_throws");
		fut = Futures.immediateFailedFuture(expect);

		AsyncCheckedException found = assertThrows(AsyncCheckedException.class, fut::resultNow);
		collector.checkThat(found.getCause(), sameInstance(expect));
		found = assertThrows(AsyncCheckedException.class, fut::get);
		collector.checkThat(found.getCause(), sameInstance(expect));
		found = assertThrows(AsyncCheckedException.class, () -> fut.get(1, DAYS));
		collector.checkThat(found.getCause(), sameInstance(expect));
	}

	@Test public void forChecked_toString_correct() throws Throwable {
		IOException expect = new IOException("forChecked_toString_correct");
		fut = Futures.immediateFailedFuture(expect);

		collector.checkThat(fut.toString(),
				matchesPattern("ImmediateFuture@\\d{1,20}\\[ failure=java.io.IOException: forChecked_toString_correct]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture\\("
						+ "ImmediateFuture:0\\) //ImmediateFuture@\\d{1,20}\\[ failure=java.io.IOException: forChecked_toString_correct]$"));
	}

	@Test public void forChecked_cancel_isNoOp() throws Throwable {
		IOException expect = new IOException("forChecked_cancel_isNoOp");
		fut = Futures.immediateFailedFuture(expect);

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forChecked_setException_crashes() throws Throwable {
		IOException expect = new IOException("forChecked_setException_crashes_expect");
		fut = Futures.immediateFailedFuture(expect);

		IOException secondException = new IOException("forChecked_setException_crashes_second");
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(secondException);
		fut.setException(secondException);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forCancellation_state_isFailed() throws Throwable {
		CancellationException e = new CancellationException("forCancellation_state_isFailed");
		fut = Futures.immediateFailedFuture(e);

		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
	}

	@Test public void forCancellation_get_throws() throws Throwable {
		CancellationException expect = new CancellationException("forCancellation_get_throws");
		fut = Futures.immediateFailedFuture(expect);

		CancellationException found = assertThrows(CancellationException.class, fut::resultNow);
		collector.checkThat(found, sameInstance(expect));
		found = assertThrows(CancellationException.class, fut::get);
		collector.checkThat(found, sameInstance(expect));
		found = assertThrows(CancellationException.class, () -> fut.get(1, DAYS));
		collector.checkThat(found, sameInstance(expect));
	}

	@Test public void forCancellation_toString_correct() throws Throwable {
		CancellationException expect = new CancellationException("forCancellation_toString_correct");
		fut = Futures.immediateFailedFuture(expect);

		collector.checkThat(
				fut.toString(),
				matchesPattern(
						"ImmediateFuture@\\d{1,20}\\[ cancelled=java.util.concurrent.CancellationException: forCancellation_toString_correct]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture\\("
						+ "ImmediateFuture:0\\) //ImmediateFuture@\\d{1,20}\\[ cancelled=java.util.concurrent"
						+ ".CancellationException: forCancellation_toString_correct]$"));
	}

	@Test public void forCancellation_cancel_isNoOp() throws Throwable {
		CancellationException expect = new CancellationException("forCancellation_cancel_isNoOp");
		fut = Futures.immediateFailedFuture(expect);

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forCancellation_setException_crashes() throws Throwable {
		CancellationException expect = new CancellationException("forCancellation_setException_crashes_cancel");
		fut = Futures.immediateFailedFuture(expect);

		IOException secondException = new IOException("forCancellation_setException_crashes_second");
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(secondException);
		fut.setException(secondException);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}
}