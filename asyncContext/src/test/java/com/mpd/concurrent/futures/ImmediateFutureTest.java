package com.mpd.concurrent.futures;

import static android.util.Log.DEBUG;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;

import com.mpd.concurrent.futures.Future.AsyncCheckedException;
import com.mpd.test.AsyncContextRule;
import com.mpd.test.ErrorCollector;
import com.mpd.test.UncaughtExceptionRule;
import com.tbohne.android.flogger.backend.AndroidBackend;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class) public class ImmediateFutureTest {
	@Rule public ErrorCollector collector = new ErrorCollector();
	@Rule public AsyncContextRule asyncContextRule = new AsyncContextRule();
	@Rule public UncaughtExceptionRule uncaughtExceptionRule = new UncaughtExceptionRule();

	@Before public void enableDebugLogging() {
		AndroidBackend.setLogLevelOverride(DEBUG);
	}

	@Test public void forString_state_isSuccessful() throws Throwable {
		Future<String> fut = Futures.immediateFuture("test");
		fut.end();

		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), nullValue());
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
	}

	@Test public void forString_get_isSuccessful() throws Throwable {
		Future<String> fut = Futures.immediateFuture("test");
		fut.end();

		collector.checkThat(fut.resultNow(), equalTo("test"));
		collector.checkThat(fut.get(), equalTo("test"));
		collector.checkThat(fut.get(1, DAYS), equalTo("test"));
	}

	@Test public void forString_toString_correct() throws Throwable {
		Future<String> fut = Futures.immediateFuture("test");
		fut.end();

		collector.checkThat(fut.toString(), matchesPattern("ImmediateFuture@\\d{1,20}\\[ success=test]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture\\("
						+ "ImmediateFuture:0\\) //ImmediateFuture@\\d{1,20}\\[ success=test]$"));
	}

	@Test public void forString_cancel_isNoOp() throws Throwable {
		Future<String> fut = Futures.immediateFuture("test");
		fut.end();

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.exceptionNow(), nullValue());
	}

	@Test public void forString_setException_crashes() throws Throwable {
		Future<String> fut = Futures.immediateFuture("test");
		fut.end();

		ArithmeticException secondException = new ArithmeticException("test-exception-after-success");
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(secondException);
		fut.setException(secondException);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), nullValue());
	}

	@Test public void forUnchecked_state_isFailed() throws Throwable {
		ArithmeticException e = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(e);
		fut.catching(ArithmeticException.class, ex -> null).end();

		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
	}

	@Test public void forUnchecked_get_throws() throws Throwable {
		ArithmeticException expect = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);
		fut.catching(ArithmeticException.class, ex -> null).end();

		collector.checkThrows(ArithmeticException.class, fut::resultNow, sameInstance(expect));
		collector.checkThrows(ArithmeticException.class, fut::get, sameInstance(expect));
		collector.checkThrows(ArithmeticException.class, () -> fut.get(1, DAYS), sameInstance(expect));
	}

	@Test public void forUnchecked_toString_correct() throws Throwable {
		ArithmeticException expect = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);
		fut.catching(ArithmeticException.class, ex -> null).end();

		collector.checkThat(fut.toString(),
				matchesPattern("ImmediateFuture@\\d{1,20}\\[ failure=java.lang.ArithmeticException: test]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture\\("
						+ "ImmediateFuture:0\\) //ImmediateFuture@\\d{1,20}\\[ failure=java.lang.ArithmeticException: test]$"));
	}

	@Test public void forUnchecked_cancel_isNoOp() throws Throwable {
		ArithmeticException expect = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);
		fut.catching(ArithmeticException.class, ex -> null).end();

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forUnchecked_setException_crashes() throws Throwable {
		ArithmeticException expect = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);
		fut.catching(ArithmeticException.class, ex -> null).end();

		IOException secondException = new IOException("test-exception-after-unchecked");
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(secondException);
		fut.setException(secondException);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forChecked_state_isFailed() throws Throwable {
		IOException e = new IOException("test");
		Future<String> fut = Futures.immediateFailedFuture(e);
		fut.catching(IOException.class, ex -> null).end();

		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
	}

	@Test public void forChecked_get_throws() throws Throwable {
		IOException expect = new IOException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);
		fut.catching(IOException.class, ex -> null).end();

		AsyncCheckedException found = assertThrows(AsyncCheckedException.class, fut::resultNow);
		collector.checkThat(found.getCause(), sameInstance(expect));
		found = assertThrows(AsyncCheckedException.class, fut::get);
		collector.checkThat(found.getCause(), sameInstance(expect));
		found = assertThrows(AsyncCheckedException.class, () -> fut.get(1, DAYS));
		collector.checkThat(found.getCause(), sameInstance(expect));
	}

	@Test public void forChecked_toString_correct() throws Throwable {
		IOException expect = new IOException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);
		fut.catching(IOException.class, ex -> null).end();

		collector.checkThat(fut.toString(),
				matchesPattern("ImmediateFuture@\\d{1,20}\\[ failure=java.io.IOException: test]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture\\("
						+ "ImmediateFuture:0\\) //ImmediateFuture@\\d{1,20}\\[ failure=java.io.IOException: test]$"));
	}

	@Test public void forChecked_cancel_isNoOp() throws Throwable {
		IOException expect = new IOException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);
		fut.catching(IOException.class, ex -> null).end();

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forChecked_setException_crashes() throws Throwable {
		IOException expect = new IOException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);
		fut.catching(IOException.class, ex -> null).end();

		IOException secondException = new IOException("test-exception-after-checked");
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(secondException);
		fut.setException(secondException);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forCancellation_state_isFailed() throws Throwable {
		CancellationException e = new CancellationException("test");
		Future<String> fut = Futures.immediateFailedFuture(e);
		fut.end();

		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
	}

	@Test public void forCancellation_get_throws() throws Throwable {
		CancellationException expect = new CancellationException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);
		fut.end();

		CancellationException found = assertThrows(CancellationException.class, fut::resultNow);
		collector.checkThat(found, sameInstance(expect));
		found = assertThrows(CancellationException.class, fut::get);
		collector.checkThat(found, sameInstance(expect));
		found = assertThrows(CancellationException.class, () -> fut.get(1, DAYS));
		collector.checkThat(found, sameInstance(expect));
	}

	@Test public void forCancellation_toString_correct() throws Throwable {
		CancellationException expect = new CancellationException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);
		fut.end();

		collector.checkThat(
				fut.toString(),
				matchesPattern("ImmediateFuture@\\d{1,20}\\[ cancelled=java.util.concurrent.CancellationException: test]"));
		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture\\("
						+ "ImmediateFuture:0\\) //ImmediateFuture@\\d{1,20}\\[ cancelled=java.util.concurrent"
						+ ".CancellationException: test]$"));
	}

	@Test public void forCancellation_cancel_isNoOp() throws Throwable {
		CancellationException expect = new CancellationException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);
		fut.end();

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forCancellation_setException_crashes() throws Throwable {
		CancellationException expect = new CancellationException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);
		fut.end();

		IOException secondException = new IOException("test-exception-after-cancel");
		uncaughtExceptionRule.expectUncaughtExceptionInThisThread(secondException);
		fut.setException(secondException);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}
}