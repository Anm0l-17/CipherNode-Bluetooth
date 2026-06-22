package org.briarproject.briar.call;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.MetadataEncoder;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.sync.validation.ValidationManager;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager;
import org.briarproject.briar.api.call.CallManager;
import org.briarproject.briar.api.messaging.MessagingManager;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.briar.api.call.CallManager.CLIENT_ID;
import static org.briarproject.briar.api.call.CallManager.MAJOR_VERSION;

@Module
public class CallModule {

	public static class EagerSingletons {
		@Inject
		CallManager callManager;
		@Inject
		CallValidator callValidator;
	}

	@Provides
	@Singleton
	CallValidator provideCallValidator(ValidationManager validationManager,
			ClientHelper clientHelper, MetadataEncoder metadataEncoder,
			Clock clock) {
		CallValidator validator = new CallValidator(clientHelper,
				metadataEncoder, clock);
		validationManager.registerMessageValidator(CLIENT_ID, MAJOR_VERSION,
				validator);
		return validator;
	}

	@Provides
	@Singleton
	CallManager provideCallManager(ValidationManager validationManager,
			ClientVersioningManager clientVersioningManager,
			DatabaseComponent db, ClientHelper clientHelper,
			MetadataParser metadataParser, MessagingManager messagingManager,
			Clock clock) {
		CallManagerImpl callManager = new CallManagerImpl(db, clientHelper,
				metadataParser, messagingManager, clock);
		validationManager.registerIncomingMessageHook(CLIENT_ID, MAJOR_VERSION,
				callManager);
		clientVersioningManager.registerClient(CLIENT_ID, MAJOR_VERSION, 0,
				callManager);
		return callManager;
	}
}
