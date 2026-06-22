package org.briarproject.briar.api.call;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface CallManager {

	ClientId CLIENT_ID = new ClientId("org.briarproject.briar.call");

	int MAJOR_VERSION = 0;

	void initiateCall(ContactId contactId, boolean video) throws DbException;

	void acceptCall(ContactId contactId) throws DbException;

	void endCall(ContactId contactId) throws DbException;

	void sendOffer(ContactId contactId, String sdp) throws DbException;

	void sendAnswer(ContactId contactId, String sdp) throws DbException;

	void sendIceCandidate(ContactId contactId, String label, int index,
			String candidate) throws DbException;

	void setCallListener(CallListener listener);

	interface CallListener {
		void onOfferReceived(ContactId contactId, String sdp);

		void onAnswerReceived(ContactId contactId, String sdp);

		void onIceCandidateReceived(ContactId contactId, String label, int index,
				String candidate);

		void onHangupReceived(ContactId contactId);
	}
}
