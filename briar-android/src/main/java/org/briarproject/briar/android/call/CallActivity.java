package org.briarproject.briar.android.call;

import android.content.Intent;
import android.os.Bundle;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

import javax.annotation.Nullable;
import javax.inject.Inject;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CallActivity extends BriarActivity {

	public static final String CONTACT_ID = "briar.CONTACT_ID";
	public static final String VIDEO = "briar.VIDEO";
	public static final String IS_INCOMING = "briar.IS_INCOMING";

	@Inject
	WebRtcManager webRtcManager;

	private SurfaceViewRenderer localVideoView;
	private SurfaceViewRenderer remoteVideoView;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_call);

		Intent i = getIntent();
		int id = i.getIntExtra(CONTACT_ID, -1);
		if (id == -1) throw new IllegalStateException();
		ContactId contactId = new ContactId(id);
		boolean video = i.getBooleanExtra(VIDEO, true);
		boolean incoming = i.getBooleanExtra(IS_INCOMING, false);

		localVideoView = findViewById(R.id.local_video_view);
		remoteVideoView = findViewById(R.id.remote_video_view);

		localVideoView.init(webRtcManager.getEglContext(), null);
		localVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
		localVideoView.setMirror(true);

		remoteVideoView.init(webRtcManager.getEglContext(), null);
		remoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);

		if (incoming) {
			webRtcManager.acceptCall(contactId, localVideoView, remoteVideoView);
		} else {
			webRtcManager.setRemoteSink(remoteVideoView);
			webRtcManager.initLocalStream(video, localVideoView);
			webRtcManager.initiateCall(contactId);
		}

		findViewById(R.id.switch_camera_button).setOnClickListener(v -> {
			webRtcManager.switchCamera();
		});

		findViewById(R.id.hangup_button).setOnClickListener(v -> {
			webRtcManager.onHangupReceived(contactId);
			finish();
		});
	}

	@Override
	public void onUserLeaveHint() {
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
			enterPictureInPictureMode();
		}
	}

	@Override
	protected void onDestroy() {
		localVideoView.release();
		remoteVideoView.release();
		super.onDestroy();
	}
}
