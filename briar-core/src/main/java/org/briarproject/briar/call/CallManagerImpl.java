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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;

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

	private static final int LOG_TYPE_OUTGOING = 1;
	private static final int LOG_TYPE_INCOMING = 2;

	private final DatabaseComponent db;
	private final ClientHelper clientHelper;
	@SuppressWarnings("unused")
	private final MetadataParser metadataParser;
	private final MessagingManager messagingManager;
	private final Clock clock;

	private final Map<ContactId, Long> callStartTimes = new ConcurrentHashMap<>();
	private volatile CallListener callListener = new NoOpCallListener();

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
		callStartTimes.put(contactId, clock.currentTimeMillis());
	}

	@Override
	public void acceptCall(ContactId contactId) throws DbException {
		LOG.info("Accepting call from " + contactId);
		callStartTimes.put(contactId, clock.currentTimeMillis());
	}

	@Override
	public void endCall(ContactId contactId) throws DbException {
		LOG.info("Ending call with " + contactId);
		sendSignalingMessage(contactId, BdfList.of(TYPE_HANGUP));
		Long startTime = callStartTimes.remove(contactId);
		if (startTime != null) {
			saveCallLog(contactId, startTime, clock.currentTimeMillis(),
					LOG_TYPE_OUTGOING, true);
		}
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

	@Override
	public void setCallListener(CallListener listener) {
		this.callListener = listener;
	}

	private void saveCallLog(ContactId contactId, long startTime, long endTime,
			int type, boolean video) throws DbException {
		db.transaction(false, txn -> {
			try {
				// Use Metadata-based approach instead of raw SQL since it's safer within existing architecture
				GroupId groupId = messagingManager.getConversationId(txn, contactId);
				Message m = clientHelper.createMessageForStoringMetadata(groupId);
				BdfDictionary meta = new BdfDictionary();
				meta.put("call_log", true);
				meta.put("start_time", startTime);
				meta.put("end_time", endTime);
				meta.put("type", type);
				meta.put("video", video);
				clientHelper.addLocalMessage(txn, m, meta, false, false);
			} catch (Exception e) {
				throw new DbException(e);
			}
		});
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
			Metadata meta) throws DbException {
		try {
			BdfList body = clientHelper.getMessageAsList(txn, m.getId());
			int type = body.getInt(0);
			ContactId contactId = messagingManager.getContactId(m.getGroupId());
			switch (type) {
				case TYPE_OFFER:
					callListener.onOfferReceived(contactId, body.getString(1));
					break;
				case TYPE_ANSWER:
					callListener.onAnswerReceived(contactId, body.getString(1));
					break;
				case TYPE_CANDIDATE:
					callListener.onIceCandidateReceived(contactId,
							body.getString(1), body.getInt(2),
							body.getString(3));
					break;
				case TYPE_HANGUP:
					callListener.onHangupReceived(contactId);
					Long startTime = callStartTimes.remove(contactId);
					if (startTime != null) {
						saveCallLog(contactId, startTime,
								clock.currentTimeMillis(), LOG_TYPE_INCOMING,
								true);
					}
					break;
			}
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

	private static class NoOpCallListener implements CallListener {
		@Override
		public void onOfferReceived(ContactId contactId, String sdp) {
		}

		@Override
		public void onAnswerReceived(ContactId contactId, String sdp) {
		}

		@Override
		public void onIceCandidateReceived(ContactId contactId, String label,
				int index, String candidate) {
		}

		@Override
		public void onHangupReceived(ContactId contactId) {
		}
	}
}
