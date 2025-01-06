package com.mpd.concurrent.executors;

import com.mpd.concurrent.executors.locked.DirectExecutor;

public class MoreExecutors {
	public static DirectExecutor directExecutor() {
		return DirectExecutor.instance;
	}
}
