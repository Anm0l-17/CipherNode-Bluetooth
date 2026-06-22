package org.briarproject.briar.android;

import org.briarproject.briar.android.call.AndroidCallModule;

interface AndroidEagerSingletons {

	void inject(AppModule.EagerSingletons init);

	void inject(AndroidCallModule.EagerSingletons init);

	class Helper {

		static void injectEagerSingletons(AndroidEagerSingletons c) {
			c.inject(new AppModule.EagerSingletons());
			c.inject(new AndroidCallModule.EagerSingletons());
		}
	}
}
