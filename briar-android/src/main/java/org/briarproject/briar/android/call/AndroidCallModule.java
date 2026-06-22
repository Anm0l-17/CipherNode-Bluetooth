package org.briarproject.briar.android.call;

import android.app.Application;

import org.webrtc.PeerConnectionFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AndroidCallModule {

	public static class EagerSingletons {
		@Inject
		WebRtcManager webRtcManager;
	}

	@Provides
	@Singleton
	PeerConnectionFactory providePeerConnectionFactory(Application app) {
		PeerConnectionFactory.InitializationOptions initializationOptions =
				PeerConnectionFactory.InitializationOptions.builder(app)
						.createInitializationOptions();
		PeerConnectionFactory.initialize(initializationOptions);
		return PeerConnectionFactory.builder()
				.createPeerConnectionFactory();
	}
}
