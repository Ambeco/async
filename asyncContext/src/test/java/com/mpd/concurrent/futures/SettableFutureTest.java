package com.mpd.concurrent.futures;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.internal.matchers.ThrowableCauseMatcher.hasCause;
import static java.util.concurrent.TimeUnit.DAYS;

import com.mpd.concurrent.futures.Future.AsyncCheckedException;
import com.mpd.concurrent.futures.Future.FutureNotCompleteException;
import com.mpd.test.ErrorCollector;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

public class SettableFutureTest {
	@Rule public ErrorCollector collector = new ErrorCollector();

	@Test public void construct_incomplete() {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.end();

		collector.checkThrows(FutureNotCompleteException.class, fut::resultNow);
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(false));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThrows(FutureNotCompleteException.class, fut::exceptionNow);
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
	}

	@Test public void setResult_isSuccessful() {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.end();
		fut.setResult("test");

		collector.checkThat(fut.resultNow(), equalTo("test"));
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), nullValue());
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
		collector.checkThat(fut.get(), equalTo("test"));
		collector.checkThat(fut.get(1, DAYS), equalTo("test"));
	}

	@Test public void setException_UncheckedException_isFailed() {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.end();
		ArithmeticException e = new ArithmeticException("test");
		fut.setException(e);

		collector.checkThrows(ArithmeticException.class, fut::resultNow);
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
		collector.checkThrows(ArithmeticException.class, fut::get);
		collector.checkThrows(ArithmeticException.class, () -> fut.get(1, DAYS));
	}

	@Test public void setException_checkedException_isFailed() {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.end();
		IOException e = new IOException("test");
		fut.setException(e);

		collector.checkThrows(AsyncCheckedException.class, fut::resultNow, hasCause(sameInstance(e)));
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
		collector.checkThrows(AsyncCheckedException.class, fut::get, hasCause(sameInstance(e)));
		collector.checkThrows(AsyncCheckedException.class, () -> fut.get(1, DAYS), hasCause(sameInstance(e)));
	}

	@Test public void setException_cancelledException_isFailed() {
		SettableFuture<String> fut = new SettableFuture<>();
		fut.end();
		CancellationException e = new CancellationException("test");
		fut.setException(e);

		collector.checkThrows(CancellationException.class, fut::resultNow);
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.exceptionNow(), sameInstance(e));
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(TimeUnit.MILLISECONDS));
		collector.checkThrows(CancellationException.class, fut::get);
		collector.checkThrows(CancellationException.class, () -> fut.get(1, DAYS));
	}
}