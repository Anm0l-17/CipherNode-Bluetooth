package org.briarproject.briar.android.call;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.briarproject.briar.android.BriarApplication;
import org.briarproject.briar.api.android.AndroidNotificationManager;

import javax.annotation.Nullable;
import javax.inject.Inject;

public class CallService extends Service {

	@Inject
	AndroidNotificationManager notificationManager;

	@Override
	public void onCreate() {
		super.onCreate();
		((BriarApplication) getApplication()).getApplicationComponent().inject(this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Notification notification = notificationManager.getForegroundNotification();
		startForeground(12345, notification);
		return START_NOT_STICKY;
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
