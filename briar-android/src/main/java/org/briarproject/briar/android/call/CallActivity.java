package org.briarproject.briar.android.call;

import android.os.Bundle;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.nullsafety.MethodsNotNullByDefault;
import org.briarproject.nullsafety.ParametersNotNullByDefault;
import org.webrtc.SurfaceViewRenderer;

import javax.annotation.Nullable;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class CallActivity extends BriarActivity {

	@SuppressWarnings("unused")
	private SurfaceViewRenderer localVideoView;
	@SuppressWarnings("unused")
	private SurfaceViewRenderer remoteVideoView;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_call);

		localVideoView = findViewById(R.id.local_video_view);
		remoteVideoView = findViewById(R.id.remote_video_view);

		findViewById(R.id.hangup_button).setOnClickListener(v -> finish());
	}
}
