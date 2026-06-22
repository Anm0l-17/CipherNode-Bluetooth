package org.briarproject.briar.call;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.data.BdfDictionary;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.MetadataParser;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Metadata;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.sync.Group.Visibility;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.bramble.api.sync.validation.IncomingMessageHook;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.versioning.ClientVersioningManager.ClientVersioningHook;
import org.briarproject.briar.api.call.CallManager;
import org.briarproject.briar.api.messaging.MessagingManager;
import org.briarproject.nullsafety.NotNullByDefault;

import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static java.util.logging.Logger.getLogger;

@Immutable
@NotNullByDefault
class CallManagerImpl implements CallManager, IncomingMessageHook,
		ClientVersioningHook {

	private static final Logger LOG =
			getLogger(CallManagerImpl.class.getName());

	private static final int TYPE_OFFER = 1;
	private static final int TYPE_ANSWER = 2;
	private static final int TYPE_CANDIDATE = 3;
	private static final int TYPE_HANGUP = 4;

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	private final MetadataParser metadataParser;
	private final MessagingManager messagingManager;
	private final Clock clock;

	CallManagerImpl(DatabaseComponent db, ClientHelper clientHelper,
			MetadataParser metadataParser, MessagingManager messagingManager,
			Clock clock) {
		this.db = db;
		this.clientHelper = clientHelper;
		this.metadataParser = metadataParser;
		this.messagingManager = messagingManager;
		this.clock = clock;
	}

	@Override
	public void initiateCall(ContactId contactId, boolean video)
			throws DbException {
		LOG.info("Initiating call to " + contactId);
		// Logic to start WebRTC session will be added in Phase 3
	}

	@Override
	public void acceptCall(ContactId contactId) throws DbException {
		LOG.info("Accepting call from " + contactId);
	}

	@Override
	public void endCall(ContactId contactId) throws DbException {
		LOG.info("Ending call with " + contactId);
		sendSignalingMessage(contactId, BdfList.of(TYPE_HANGUP));
	}

	@Override
	public void sendOffer(ContactId contactId, String sdp) throws DbException {
		sendSignalingMessage(contactId, BdfList.of(TYPE_OFFER, sdp));
	}

	@Override
	public void sendAnswer(ContactId contactId, String sdp) throws DbException {
		sendSignalingMessage(contactId, BdfList.of(TYPE_ANSWER, sdp));
	}

	@Override
	public void sendIceCandidate(ContactId contactId, String label, int index,
			String candidate) throws DbException {
		sendSignalingMessage(contactId,
				BdfList.of(TYPE_CANDIDATE, label, index, candidate));
	}

	private void sendSignalingMessage(ContactId contactId, BdfList body)
			throws DbException {
		db.transaction(false, txn -> {
			try {
				GroupId groupId = messagingManager.getConversationId(txn,
						contactId);
				long timestamp = clock.currentTimeMillis();
				Message m = clientHelper.createMessage(groupId, timestamp,
						body);
				BdfDictionary meta = new BdfDictionary();
				meta.put("timestamp", timestamp);
				clientHelper.addLocalMessage(txn, m, meta, true, false);
			} catch (FormatException e) {
				throw new DbException(e);
			}
		});
	}

	@Override
	public DeliveryAction incomingMessage(Transaction txn, Message m,
			org.briarproject.bramble.api.db.Metadata meta) throws DbException {
		try {
			BdfList body = clientHelper.getMessageAsList(txn, m.getId());
			int type = body.getInt(0);
			LOG.info("Incoming signaling message type: " + type);
			// Process signaling data and dispatch to UI or WebRTC engine
		} catch (FormatException e) {
			LOG.warning("Malformed signaling message");
		}
		return DeliveryAction.ACCEPT_DO_NOT_SHARE;
	}

	@Override
	public void onClientVisibilityChanging(Transaction txn, Contact c,
			Visibility v) throws DbException {
		// Nothing to do
	}
}
