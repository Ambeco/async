package com.mpd.concurrent.futures;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.matchesPattern;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

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
				matchesPattern("ImmediateFuture@\\d{10}\\[success=test]"));
	}

	@Test public void forString_addPendingString_correct() {
		StringBuilder sb = new StringBuilder();
		Futures.immediateFuture("test").addPendingString(sb, 4);
		collector.checkThat(sb.toString(),
				matchesPattern("^\n\\s\\sat com.mpd.concurrent.futures.ImmediateFuture.run\\("
						+ "Unknown\\sSource\\) //ImmediateFuture@\\d{8,10}\\[success=test]$"));
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
}