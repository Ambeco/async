package com.tbohne.android.flogger.backend;

import static android.util.Log.VERBOSE;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.typeCompatibleWith;

import android.util.Log;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.LogSiteStackTrace;
import com.google.common.flogger.StackSize;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class) public class AndroidBackendFactoryTest {

	@Before public void enableDebugLogging() {
		AndroidBackend.setLogLevelOverride(VERBOSE);
	}

	@Test public void log_writesToAndroidLog() throws Throwable {
		FluentLogger log = FluentLogger.forEnclosingClass();
		RuntimeException ex = new RuntimeException("errmsg");
		log.atInfo().withStackTrace(StackSize.SMALL).withCause(ex).atMostEvery(1, TimeUnit.SECONDS).log("logmsg");

		assertThat(ShadowLog.getLogs(), hasSize(1));
		ShadowLog.LogItem logItem = ShadowLog.getLogs().get(0);
		assertThat(logItem.type, equalTo(Log.INFO));
		assertThat(logItem.tag, equalTo("backend"));
		assertThat(logItem.msg, containsString("logmsg"));
		assertThat(logItem.msg, containsString("[CONTEXT ratelimit_period=\"1 SECONDS\" ]"));
		assertThat(logItem.throwable.getClass(), typeCompatibleWith(LogSiteStackTrace.class));
		assertThat(
				logItem.throwable.getStackTrace()[0].getClassName(),
				equalTo("com.tbohne.android.flogger.backend" + ".AndroidBackendFactoryTest"));
		assertThat(logItem.throwable.getStackTrace()[0].getMethodName(), equalTo("log_writesToAndroidLog"));
		assertThat(logItem.throwable.getStackTrace()[0].getFileName(), equalTo("AndroidBackendFactoryTest.java"));
		assertThat(logItem.throwable.getCause(), sameInstance(ex));
	}

	@Test public void create_withNull_fallsBack() throws Throwable {
		AndroidBackendFactory factory = new AndroidBackendFactory();

		AndroidBackend backend = factory.create(null);

		assertThat(backend.getLoggerName(), equalTo("NoTag"));
	}

	@Test public void create_withNoClass_fallsBack() throws Throwable {
		AndroidBackendFactory factory = new AndroidBackendFactory();

		AndroidBackend backend = factory.create("");

		assertThat(backend.getLoggerName(), equalTo("NoTag"));
	}

	@Test public void create_withNoPackage_fallsBack() throws Throwable {
		AndroidBackendFactory factory = new AndroidBackendFactory();

		AndroidBackend backend = factory.create("a");

		assertThat(backend.getLoggerName(), equalTo("NoTag"));
	}

	@Test public void create_withEmptyPackage_fallsBack() throws Throwable {
		AndroidBackendFactory factory = new AndroidBackendFactory();

		AndroidBackend backend = factory.create(".a");

		assertThat(backend.getLoggerName(), equalTo("NoTag"));
	}

	@Test public void create_withLongPackageName_truncates() throws Throwable {
		AndroidBackendFactory factory = new AndroidBackendFactory();

		AndroidBackend backend = factory.create("com.tbohne.thispackagenameiswaytoolongtheandroidlimitis23chars"
				+ ".AndroidBackendFactoryTest");

		assertThat(backend.getLoggerName(), equalTo("thispackagenameiswayto"));
	}

	@Test public void create_packageIsIgnored_parentPackageValid_usesParent() throws Throwable {
		AndroidBackendFactory factory = new AndroidBackendFactory();

		AndroidBackend backend = factory.create("com.tbohne.test.a");

		assertThat(backend.getLoggerName(), equalTo("tbohne"));
	}

	@Test public void create_packageIsIgnored_noParentPackage_fallsBack() throws Throwable {
		AndroidBackendFactory factory = new AndroidBackendFactory();

		AndroidBackend backend = factory.create("test.a");

		assertThat(backend.getLoggerName(), equalTo("NoTag"));
	}

	@Test public void create_packageIsIgnored_emptyParentPackage_fallsBack() throws Throwable {
		AndroidBackendFactory factory = new AndroidBackendFactory();

		AndroidBackend backend = factory.create(".test.a");

		assertThat(backend.getLoggerName(), equalTo("NoTag"));
	}

	@Test public void create_packageIsIgnored_longParentPackage_truncates() throws Throwable {
		AndroidBackendFactory factory = new AndroidBackendFactory();

		AndroidBackend backend = factory.create("thispackagenameiswaytoolongtheandroidlimitis23chars.test.a");

		assertThat(backend.getLoggerName(), equalTo("thispackagenameiswayto"));
	}

	@Test public void create_packageIsIgnored_parentAlsoIgnored_usesParentAnyway() throws Throwable {
		AndroidBackendFactory factory = new AndroidBackendFactory();

		AndroidBackend backend = factory.create("impl.test.a");

		assertThat(backend.getLoggerName(), equalTo("impl"));
	}


}
