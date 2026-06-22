package org.briarproject.briar.android.call;

import android.content.Context;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.call.CallManager;
import org.briarproject.nullsafety.NotNullByDefault;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

import static java.util.logging.Logger.getLogger;

@Singleton
@NotNullByDefault
public class WebRtcManager implements CallManager.CallListener {

	private static final Logger LOG = getLogger(WebRtcManager.class.getName());

	private final Context context;
	private final PeerConnectionFactory factory;
	private final CallManager callManager;
	private final AndroidNotificationManager notificationManager;
	private final EglBase eglBase;

	@Nullable
	private PeerConnection peerConnection;
	@Nullable
	private VideoTrack localVideoTrack;
	@Nullable
	private AudioTrack localAudioTrack;

	@Nullable
	private CameraVideoCapturer videoCapturer;
	@Nullable
	private VideoSink remoteSink;

	private final Map<ContactId, String> pendingOffers = new ConcurrentHashMap<>();

	@Inject
	WebRtcManager(Context context, PeerConnectionFactory factory,
			CallManager callManager,
			AndroidNotificationManager notificationManager) {
		this.context = context;
		this.factory = factory;
		this.callManager = callManager;
		this.notificationManager = notificationManager;
		this.eglBase = EglBase.create();
		this.callManager.setCallListener(this);
	}

	public EglBase.Context getEglContext() {
		return eglBase.getEglBaseContext();
	}

	public void setRemoteSink(VideoSink sink) {
		this.remoteSink = sink;
	}

	public void switchCamera() {
		if (videoCapturer != null) {
			videoCapturer.switchCamera(null);
		}
	}

	public void initLocalStream(boolean video, VideoSink localSink) {
		MediaConstraints audioConstraints = new MediaConstraints();
		AudioSource audioSource = factory.createAudioSource(audioConstraints);
		localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource);

		if (video) {
			videoCapturer = createVideoCapturer();
			if (videoCapturer != null) {
				SurfaceTextureHelper helper = SurfaceTextureHelper.create(
						"CaptureThread", eglBase.getEglBaseContext());
				VideoSource videoSource = factory.createVideoSource(
						videoCapturer.isScreencast());
				videoCapturer.initialize(helper, context,
						videoSource.getCapturerObserver());
				videoCapturer.startCapture(1280, 720, 30);
				localVideoTrack = factory.createVideoTrack("ARDAMSv0",
						videoSource);
				localVideoTrack.addSink(localSink);
			}
		}
	}

	@Nullable
	private CameraVideoCapturer createVideoCapturer() {
		CameraEnumerator enumerator;
		if (Camera2Enumerator.isSupported(context)) {
			enumerator = new Camera2Enumerator(context);
		} else {
			enumerator = new Camera1Enumerator(true);
		}

		String[] deviceNames = enumerator.getDeviceNames();
		for (String deviceName : deviceNames) {
			if (enumerator.isFrontFacing(deviceName)) {
				return enumerator.createCapturer(deviceName, null);
			}
		}
		for (String deviceName2 : deviceNames) {
			if (!enumerator.isFrontFacing(deviceName2)) {
				return enumerator.createCapturer(deviceName2, null);
			}
		}
		return null;
	}

	public void initiateCall(ContactId contactId) {
		PeerConnection pc = createPeerConnection(contactId);
		if (pc == null) return;
		peerConnection = pc;

		if (localAudioTrack != null) pc.addTrack(localAudioTrack);
		if (localVideoTrack != null) pc.addTrack(localVideoTrack);

		MediaConstraints constraints = new MediaConstraints();
		constraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"OfferToReceiveAudio", "true"));
		constraints.mandatory.add(new MediaConstraints.KeyValuePair(
				"OfferToReceiveVideo", "true"));

		pc.createOffer(new SdpObserverAdapter() {
			@Override
			public void onCreateSuccess(SessionDescription sdp) {
				pc.setLocalDescription(new SdpObserverAdapter(), sdp);
				try {
					callManager.sendOffer(contactId, sdp.description);
				} catch (Exception e) {
					LOG.warning("Failed to send offer");
				}
			}
		}, constraints);
	}

	@Nullable
	private PeerConnection createPeerConnection(ContactId contactId) {
		PeerConnection.RTCConfiguration rtcConfig =
				new PeerConnection.RTCConfiguration(new ArrayList<>());
		rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED;

		return factory.createPeerConnection(rtcConfig,
				new PeerConnection.Observer() {
					@Override
					public void onSignalingChange(
							PeerConnection.SignalingState signalingState) {
					}

					@Override
					public void onIceConnectionChange(
							PeerConnection.IceConnectionState iceConnectionState) {
					}

					@Override
					public void onIceConnectionReceivingChange(boolean b) {
					}

					@Override
					public void onIceGatheringChange(
							PeerConnection.IceGatheringState iceGatheringState) {
					}

					@Override
					public void onIceCandidate(IceCandidate candidate) {
						try {
							callManager.sendIceCandidate(contactId,
									candidate.sdpMid, candidate.sdpMLineIndex,
									candidate.sdp);
						} catch (Exception e) {
							LOG.warning("Failed to send ICE candidate");
						}
					}

					@Override
					public void onIceCandidatesRemoved(
							IceCandidate[] iceCandidates) {
					}

					@Override
					public void onAddStream(MediaStream mediaStream) {
						if (!mediaStream.videoTracks.isEmpty()) {
							VideoTrack remoteVideoTrack =
									mediaStream.videoTracks.get(0);
							if (remoteSink != null) {
								remoteVideoTrack.addSink(remoteSink);
							}
						}
					}

					@Override
					public void onRemoveStream(MediaStream mediaStream) {
					}

					@Override
					public void onDataChannel(DataChannel dataChannel) {
					}

					@Override
					public void onRenegotiationNeeded() {
					}

					@Override
					public void onAddTrack(RtpReceiver rtpReceiver,
							MediaStream[] mediaStreams) {
					}
				});
	}

	@Override
	public void onOfferReceived(ContactId contactId, String sdp) {
		pendingOffers.put(contactId, sdp);
		notificationManager.showIncomingCallNotification(contactId, true);
	}

	public void acceptCall(ContactId contactId, VideoSink localSink,
			VideoSink remoteSink) {
		String sdp = pendingOffers.remove(contactId);
		if (sdp == null) return;

		this.remoteSink = remoteSink;
		initLocalStream(true, localSink);

		PeerConnection pc = createPeerConnection(contactId);
		if (pc == null) return;
		peerConnection = pc;

		if (localAudioTrack != null) pc.addTrack(localAudioTrack);
		if (localVideoTrack != null) pc.addTrack(localVideoTrack);

		pc.setRemoteDescription(new SdpObserverAdapter(),
				new SessionDescription(SessionDescription.Type.OFFER, sdp));
		pc.createAnswer(new SdpObserverAdapter() {
			@Override
			public void onCreateSuccess(SessionDescription sdp) {
				pc.setLocalDescription(new SdpObserverAdapter(), sdp);
				try {
					callManager.sendAnswer(contactId, sdp.description);
				} catch (Exception e) {
					LOG.warning("Failed to send answer");
				}
			}
		}, new MediaConstraints());
	}

	@Override
	public void onAnswerReceived(ContactId contactId, String sdp) {
		if (peerConnection != null) {
			peerConnection.setRemoteDescription(new SdpObserverAdapter(),
					new SessionDescription(SessionDescription.Type.ANSWER,
							sdp));
		}
	}

	@Override
	public void onIceCandidateReceived(ContactId contactId, String label,
			int index, String candidate) {
		if (peerConnection != null) {
			peerConnection.addIceCandidate(new IceCandidate(label, index,
					candidate));
		}
	}

	@Override
	public void onHangupReceived(ContactId contactId) {
		if (peerConnection != null) {
			peerConnection.close();
			peerConnection = null;
		}
		notificationManager.clearIncomingCallNotification(contactId);
	}

	private static class SdpObserverAdapter implements SdpObserver {
		@Override
		public void onCreateSuccess(SessionDescription sessionDescription) {
		}

		@Override
		public void onSetSuccess() {
		}

		@Override
		public void onCreateFailure(String s) {
		}

		@Override
		public void onSetFailure(String s) {
		}
	}
}
