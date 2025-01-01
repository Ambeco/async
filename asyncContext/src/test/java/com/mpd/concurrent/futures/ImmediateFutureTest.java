package com.mpd.concurrent.futures;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.mpd.concurrent.futures.Future.AsyncCheckedException;
import com.mpd.test.ErrorCollector;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CancellationException;

public class ImmediateFutureTest {
	@Rule public ErrorCollector collector = new ErrorCollector();

	@Test public void forString_state_isSuccessful() {
		Future<String> fut = Futures.immediateFuture("test");
		fut.end();

		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), nullValue());
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
	}

	@Test public void forString_get_isSuccessful() {
		Future<String> fut = Futures.immediateFuture("test");
		fut.end();

		collector.checkThat(fut.resultNow(), equalTo("test"));
		collector.checkThat(fut.get(), equalTo("test"));
		collector.checkThat(fut.get(1, DAYS), equalTo("test"));
	}

	@Test public void forString_toString_correct() {
		Future<String> fut = Futures.immediateFuture("test");
		fut.end();

		collector.checkThat(fut.toString(), matchesPattern("ImmediateFuture@\\d{1,20}\\[success=test]"));
	}

	@Test public void forString_addPendingString_correct() {
		Future<String> fut = Futures.immediateFuture("test");
		fut.end();

		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture.run\\("
						+ "Unknown\\sSource\\) //ImmediateFuture@\\d{1,20}\\[success=test]$"));
	}

	@Test public void forString_cancel_isNoOp() {
		Future<String> fut = Futures.immediateFuture("test");
		fut.end();

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.exceptionNow(), nullValue());
	}

	@Test public void forString_setException_isNoOp() {
		Future<String> fut = Futures.immediateFuture("test");
		fut.end();

		fut.setException(new ArithmeticException("FAILURE"));

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), nullValue());
	}

	@Test public void forUnchecked_state_isFailed() {
		ArithmeticException e = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(e);
		fut.end();

		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
	}

	@Test public void forUnchecked_get_throws() {
		ArithmeticException expect = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		collector.checkThrows(ArithmeticException.class, fut::resultNow, sameInstance(expect));
		collector.checkThrows(ArithmeticException.class, fut::get, sameInstance(expect));
		collector.checkThrows(ArithmeticException.class, () -> fut.get(1, DAYS), sameInstance(expect));
	}

	@Test public void forUnchecked_toString_correct() {
		ArithmeticException expect = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		collector.checkThat(fut.toString(),
				matchesPattern("ImmediateFuture@\\d{1,20}\\[failure=java.lang.ArithmeticException: test]"));
	}

	@Test public void forUnchecked_addPendingString_correct() {
		ArithmeticException expect = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture.run\\("
						+ "Unknown\\sSource\\) //ImmediateFuture@\\d{1,20}\\[failure=java.lang.ArithmeticException: test]$"));
	}

	@Test public void forUnchecked_cancel_isNoOp() {
		ArithmeticException expect = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forUnchecked_setException_isNoOp() {
		ArithmeticException expect = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		fut.setException(new ArithmeticException("FAILURE"));

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forChecked_state_isFailed() {
		IOException e = new IOException("test");
		Future<String> fut = Futures.immediateFailedFuture(e);
		fut.end();

		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
	}

	@Test public void forChecked_get_throws() {
		IOException expect = new IOException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		AsyncCheckedException found = assertThrows(AsyncCheckedException.class, fut::resultNow);
		collector.checkThat(found.getCause(), sameInstance(expect));
		found = assertThrows(AsyncCheckedException.class, fut::get);
		collector.checkThat(found.getCause(), sameInstance(expect));
		found = assertThrows(AsyncCheckedException.class, () -> fut.get(1, DAYS));
		collector.checkThat(found.getCause(), sameInstance(expect));
	}

	@Test public void forChecked_toString_correct() {
		IOException expect = new IOException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		collector.checkThat(fut.toString(),
				matchesPattern("ImmediateFuture@\\d{1,20}\\[failure=java.io.IOException: test]"));
	}

	@Test public void forChecked_addPendingString_correct() {
		IOException expect = new IOException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture.run\\("
						+ "Unknown\\sSource\\) //ImmediateFuture@\\d{1,20}\\[failure=java.io.IOException: test]$"));
	}

	@Test public void forChecked_cancel_isNoOp() {
		IOException expect = new IOException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forChecked_setException_isNoOp() {
		IOException expect = new IOException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		fut.setException(new IOException("FAILURE"));

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forCancellation_state_isFailed() {
		CancellationException e = new CancellationException("test");
		Future<String> fut = Futures.immediateFailedFuture(e);
		fut.end();

		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
	}

	@Test public void forCancellation_get_throws() {
		CancellationException expect = new CancellationException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		CancellationException found = assertThrows(CancellationException.class, fut::resultNow);
		collector.checkThat(found, sameInstance(expect));
		found = assertThrows(CancellationException.class, fut::get);
		collector.checkThat(found, sameInstance(expect));
		found = assertThrows(CancellationException.class, () -> fut.get(1, DAYS));
		collector.checkThat(found, sameInstance(expect));
	}

	@Test public void forCancellation_toString_correct() {
		CancellationException expect = new CancellationException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		collector.checkThat(
				fut.toString(),
				matchesPattern("ImmediateFuture@\\d{1,20}\\[cancelled=java.util.concurrent.CancellationException: test]"));
	}

	@Test public void forCancellation_addPendingString_correct() {
		CancellationException expect = new CancellationException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		StringBuilder sb = new StringBuilder();
		fut.addPendingString(sb, 4);
		collector.checkThat(
				sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture.run\\("
						+ "Unknown\\sSource\\) //ImmediateFuture@\\d{1,20}\\[cancelled=java.util.concurrent.CancellationException: test]$"));
	}

	@Test public void forCancellation_cancel_isNoOp() {
		CancellationException expect = new CancellationException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}

	@Test public void forCancellation_setException_isNoOp() {
		CancellationException expect = new CancellationException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		fut.setException(new IOException("FAILURE"));

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.exceptionNow(), sameInstance(expect));
	}
}