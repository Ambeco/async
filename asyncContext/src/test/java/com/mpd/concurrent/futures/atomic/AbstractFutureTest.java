package com.mpd.concurrent.futures.atomic;

import static com.mpd.test.WithCauseMatcher.withCause;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.Matchers.typeCompatibleWith;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.mpd.concurrent.futures.Future;
import com.mpd.concurrent.futures.Future.AsyncCheckedException;
import com.mpd.concurrent.futures.FutureListener;
import com.mpd.concurrent.futures.SettableFuture;
import com.mpd.test.ErrorCollector;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class) public class AbstractFutureTest {
	@Rule public ErrorCollector collector = new ErrorCollector();

	@Test public void constructor_immediateSuccess_stateIsSuccessful() {
		String result = "test";
		PublicAbstractFuture<String> fut = mock(PublicAbstractFuture.class,
				withSettings().defaultAnswer(CALLS_REAL_METHODS).useConstructor(result));
		fut.end();

		//java.util.concurrent.Future state
		collector.checkThat(fut.exceptionNow(), nullValue());
		collector.checkThat(fut.get(), equalTo(result));
		collector.checkThat(fut.get(1, DAYS), equalTo(result));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThat(fut.resultNow(), equalTo(result));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(true));
		collector.checkThat(fut.getScheduledTimeNanos(), equalTo(-1L));
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				"(AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				":0) //AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				"@",
				"[ success=test]"));
		//collector.checkThat(fut.getPendingString(4),
		//		matchesPattern(Pattern.compile("^\n\\s\\scom.mpd.concurrent.futures.atomic"
		//				+ ".AbstractFutureTest\\$PublicAbstractFuture\\$MockitoMock\\$\\d{1,20}\\("
		//				+ "AbstractFutureTest\\$PublicAbstractFuture\\$MockitoMock\\$\\d{1,20}:0\\) "
		//				+ "//AbstractFutureTest\\$PublicAbstractFuture\\$MockitoMock\\$\\d{1,20}@\\d{1,20}\\[ "
		//				+ "success=test]$", Pattern.DOTALL)));
		collector.checkThat(
				fut.toString(),
				stringContainsInOrder("AbstractFutureTest$PublicAbstractFuture$MockitoMock$", "@", "[ success=test]"));
		//collector.checkThat(fut.toString(),
		//		matchesPattern(
		//				"com.mpd.concurrent.futures.atomic.AbstractFutureTest\\$PublicAbstractFuture\\$MockitoMock\\$\\d{1,"
		//		+ "20}@\\d{1,20}\\[ success=test]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getResultProtected(), sameInstance(result));
		collector.checkThat(fut.getExceptionProtected(), sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkThat(fut.getWrappedExceptionProtected(), sameInstance(AbstractFuture.SUCCESS_EXCEPTION));
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), notNullValue());
		collector.checkThat(fut.sourceClass(), typeCompatibleWith(AbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
	}

	@Test public void constructor_immediateUncheckedException_stateIsFailed() {
		ArithmeticException expectedException = new ArithmeticException("test");
		AbstractFuture<String> fut = mock(PublicAbstractFuture.class,
				withSettings().defaultAnswer(CALLS_REAL_METHODS).useConstructor(expectedException));
		fut.catching(ArithmeticException.class, e -> null).end();

		//java.util.concurrent.Future state
		collector.checkThat(fut.exceptionNow(), sameInstance(expectedException));
		collector.checkThrows(ArithmeticException.class, fut::get, sameInstance(expectedException));
		collector.checkThrows(ArithmeticException.class, () -> fut.get(1, DAYS), sameInstance(expectedException));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThrows(ArithmeticException.class, fut::resultNow, sameInstance(expectedException));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.getScheduledTimeNanos(), equalTo(-1L));
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				"(AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				":0) //AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				"@",
				"[ failure=java.lang.ArithmeticException: test]"));
		collector.checkThat(fut.toString(),
				stringContainsInOrder("AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
						"@",
						"[ failure=java.lang.ArithmeticException: test]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getResultProtected(), nullValue());
		collector.checkThat(fut.getExceptionProtected(), sameInstance(expectedException));
		collector.checkThat(fut.getWrappedExceptionProtected(), sameInstance(expectedException));
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), notNullValue());
		collector.checkThat(fut.sourceClass(), typeCompatibleWith(AbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
	}

	@Test public void constructor_immediateCheckedException_stateIsFailed() {
		IOException expectedException = new IOException("test");
		AbstractFuture<String> fut = mock(PublicAbstractFuture.class,
				withSettings().defaultAnswer(CALLS_REAL_METHODS).useConstructor(expectedException));
		fut.catching(IOException.class, e -> null).end();

		//java.util.concurrent.Future state
		collector.checkThat(fut.exceptionNow(), sameInstance(expectedException));
		collector.checkThrows(AsyncCheckedException.class, fut::get, withCause(sameInstance(expectedException)));
		collector.checkThrows(AsyncCheckedException.class,
				() -> fut.get(1, DAYS),
				withCause(sameInstance(expectedException)));
		collector.checkThat(fut.isCancelled(), equalTo(false));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThrows(AsyncCheckedException.class, fut::resultNow, withCause(sameInstance(expectedException)));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.getScheduledTimeNanos(), equalTo(-1L));
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				"(AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				":0) //AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				"@",
				"[ failure=java.io.IOException: test]"));
		collector.checkThat(fut.toString(),
				stringContainsInOrder("AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
						"@",
						"[ failure=java.io.IOException: test]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getResultProtected(), nullValue());
		collector.checkThat(fut.getExceptionProtected(), sameInstance(expectedException));
		collector.checkThat(fut.getWrappedExceptionProtected(), withCause(sameInstance(expectedException)));
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), notNullValue());
		collector.checkThat(fut.sourceMethodName(), nullValue());
		collector.checkThat(fut.getResultProtected(), nullValue());
	}

	@Test public void constructor_immediateCancelled_stateIsCancelled() {
		CancellationException expectedException = new CancellationException("test");
		AbstractFuture<String> fut = mock(PublicAbstractFuture.class,
				withSettings().defaultAnswer(CALLS_REAL_METHODS).useConstructor(expectedException));
		fut.end();

		//java.util.concurrent.Future state
		collector.checkThat(fut.exceptionNow(), sameInstance(expectedException));
		collector.checkThrows(CancellationException.class, fut::get, sameInstance(expectedException));
		collector.checkThrows(CancellationException.class, () -> fut.get(1, DAYS), sameInstance(expectedException));
		collector.checkThat(fut.isCancelled(), equalTo(true));
		collector.checkThat(fut.isDone(), equalTo(true));
		collector.checkThrows(CancellationException.class, fut::resultNow, sameInstance(expectedException));
		//java.util.concurrent.Delayed
		collector.checkThrows(UnsupportedOperationException.class, () -> fut.getDelay(MILLISECONDS));
		//com.mpd.concurrent.futures.Future
		collector.checkThat(fut.isSuccessful(), equalTo(false));
		collector.checkThat(fut.getScheduledTimeNanos(), equalTo(-1L));
		collector.checkThat(fut.getPendingString(4), stringContainsInOrder(
				"\n  at com.mpd.concurrent.futures.atomic.AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				"(AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				":0) //AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				"@",
				"[ cancelled=java.util.concurrent.CancellationException: test]"));
		collector.checkThat(fut.toString(), stringContainsInOrder("AbstractFutureTest$PublicAbstractFuture$MockitoMock$",
				"@",
				"[ cancelled=java.util.concurrent.CancellationException: test]"));
		//com.mpd.concurrent.futures.impl.AbstractFuture
		collector.checkThat(fut.getSetAsync(), nullValue());
		collector.checkThat(fut.getResultProtected(), nullValue());
		collector.checkThat(fut.getExceptionProtected(), sameInstance(expectedException));
		collector.checkThat(fut.getWrappedExceptionProtected(), sameInstance(expectedException));
		collector.checkThat(fut.getInterrupt(), nullValue());
		collector.checkThat(fut.getListener(), notNullValue());
		collector.checkThat(fut.sourceClass(), typeCompatibleWith(AbstractFuture.class));
		collector.checkThat(fut.sourceMethodName(), nullValue());
	}

	@Test public void toString_recursiveFuture_limitedDepth() {
		SettableFuture<String> fut1 = new SettableFuture<>();
		Future<String> fut2 = fut1.transform(s -> s);

		fut1.setResult(fut2);

		collector.checkThat(fut1.toString(),
				matchesPattern("SettableFuture@\\d{1,20}\\[ "
						+ "setAsync=FutureFunction<AbstractFutureTest\\$\\$Lambda\\$\\d{1,3}/0x\\p{XDigit}{8,16}>]"));
		StringBuilder sb = new StringBuilder();
		fut1.addPendingString(sb, 4);
		collector.checkThat(sb.toString(), matchesPattern(Pattern.compile(".+", Pattern.DOTALL)));


		collector.checkThat(sb.toString(), matchesPattern(Pattern.compile(
				"^\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\(SettableFuture:0\\) //[^\\n]+"
						+ "\n\\s\\sat AbstractFutureTest\\$\\$Lambda\\$\\d{1,3}/0x\\p{XDigit}{8,16}.apply\\(AbstractFutureTest:0\\) //[^\\n]+"
						+ "\n\\s\\sat com.mpd.concurrent.futures.SettableFuture\\(SettableFuture:0\\) //[^\\n]+"
						+ "\n\\s\\sat AbstractFutureTest\\$\\$Lambda\\$\\d{1,3}/0x\\p{XDigit}{8,16}.apply\\(AbstractFutureTest:0\\) //[^\\n]+",
				Pattern.DOTALL)));
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
