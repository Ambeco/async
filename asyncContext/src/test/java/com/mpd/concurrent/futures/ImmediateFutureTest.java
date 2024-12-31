package com.mpd.concurrent.futures;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.matchesPattern;

import com.mpd.test.ErrorCollector;

import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ImmediateFutureTest {
	@Rule public ErrorCollector collector = new ErrorCollector();

	@Test public void forString_state_isSuccessful() {
		collector.checkThat(Futures.immediateFuture("test").isSuccessful(), equalTo(true));
		collector.checkThat(Futures.immediateFuture("test").isDone(), equalTo(true));
		collector.checkThat(Futures.immediateFuture("test").isCancelled(), equalTo(false));
		collector.checkThat(Futures.immediateFuture("test").getException(), nullValue());
		Futures.immediateFuture("test").end();
	}

	@Test public void forString_get_isSuccessful() {
		collector.checkThat(Futures.immediateFuture("test").getDone(), equalTo("test"));
		collector.checkThat(Futures.immediateFuture("test").get(), equalTo("test"));
		collector.checkThat(Futures.immediateFuture("test").get(1, TimeUnit.DAYS), equalTo("test"));
	}

	@Test public void forString_toString_correct() {
		collector.checkThat(Futures.immediateFuture("test").toString(),
				matchesPattern("ImmediateFuture@\\d{1,20}\\[success=test]"));
	}

	@Test public void forString_addPendingString_correct() {
		StringBuilder sb = new StringBuilder();
		Futures.immediateFuture("test").addPendingString(sb, 4);
		collector.checkThat(sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture.run\\("
						+ "Unknown\\sSource\\) //ImmediateFuture@\\d{1,20}\\[success=test]$"));
	}

	@Test public void forString_cancel_isNoOp() {
		Future<String> fut = Futures.immediateFuture("test");

		fut.cancel(Future.MAY_INTERRUPT);

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(Futures.immediateFuture("test").getException(), nullValue());
	}

	@Test public void forString_setException_isNoOp() {
		Future<String> fut = Futures.immediateFuture("test");

		fut.setException(new ArithmeticException("FAILURE"));

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(Futures.immediateFuture("test").getException(), nullValue());
	}

	@Test public void forUnchecked_state_isFailed() {
		ArithmeticException e = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(e);

		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.getException(), is(e));
		fut.end();
	}

	@Test public void forUnchecked_get_throws() {
		ArithmeticException expect = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		ArithmeticException found = collector.checkThrowsAndGet(ArithmeticException.class, fut::getDone);
		collector.checkThat(found, is(expect));
		found = collector.checkThrowsAndGet(ArithmeticException.class, fut::get);
		collector.checkThat(found, is(expect));
		found = collector.checkThrowsAndGet(ArithmeticException.class, () -> fut.get(1, TimeUnit.DAYS));
		collector.checkThat(found, is(expect));
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
		collector.checkThat(fut.getException(), is(expect));
	}

	@Test public void forUnchecked_setException_isNoOp() {
		ArithmeticException expect = new ArithmeticException("test");
		Future<String> fut = Futures.immediateFailedFuture(expect);

		fut.setException(new ArithmeticException("FAILURE"));

		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.getException(), is(expect));
	}
}