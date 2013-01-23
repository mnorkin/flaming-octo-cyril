package net.vilkas.keturi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.CameraProfile;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

@SuppressWarnings("unused")
public class Vilkas04Activity extends Activity implements SurfaceHolder.Callback {

	static final public String LOG_TAG = "WifiMedia";
	
	RtspServer rtsp_server;
	LocalServer lacal_server;
	MediaRecorder recorder;
	Camera mCamera;
	SurfaceView mSurfaceView;
	SurfaceHolder mSurfaceHolder;
	
	int session_id = 6238;
	
	public Vilkas04Activity() {
		
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.main);
		
		recorder = new MediaRecorder();
		
		lacal_server = new LocalServer( session_id );
		
		rtsp_server = new RtspServer( lacal_server );

		mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
		mSurfaceHolder = mSurfaceView.getHolder();
		mSurfaceHolder.addCallback(this);
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		
		if ( mCamera == null ) {
//			Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
//		    int cameraCount = Camera.getNumberOfCameras();
//		    for ( int camIdx = 0; camIdx < cameraCount; camIdx++ ) {
//		        Camera.getCameraInfo( camIdx, cameraInfo );
//		        if ( cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT  ) {
//		            try {
//		            	mCamera = Camera.open( camIdx );
//		            } catch (RuntimeException e) {
//		                Log.e(LOG_TAG, "Camera failed to open: " + e.getLocalizedMessage());
//		            }
//		        }
//		    }
			mCamera = Camera.open();
			
//			Camera.Parameters p = mCamera.getParameters();
//	        p.set("camera-id", );
//	        mCamera.setParameters(p);
	        
			mCamera.unlock();
		}

		Log.d(LOG_TAG, "setCamera");
		recorder.setCamera(mCamera);
		Log.d(LOG_TAG, "setAudioSource");
		recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
		Log.d(LOG_TAG, "setVideoSource");
		recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		Log.d(LOG_TAG, "CamcorderProfile");
		CamcorderProfile cp = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
		Log.d(LOG_TAG, "setProfile");
		recorder.setProfile(cp);
		Log.d(LOG_TAG, "setOutputFile");
		recorder.setOutputFile(lacal_server.getSenderFileDescriptor());
		
		rtsp_server.start();
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		if ( mCamera == null ) {
			mCamera = Camera.open();
			mCamera.unlock();
		}
		
		if ( recorder == null ) {
			Log.d(LOG_TAG, "setCamera");
			recorder.setCamera(mCamera);
			Log.d(LOG_TAG, "setAudioSource");
			recorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
			Log.d(LOG_TAG, "setVideoSource");
			recorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
			recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
			Log.d(LOG_TAG, "CamcorderProfile");
			CamcorderProfile cp = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
			Log.d(LOG_TAG, "setProfile");
			recorder.setProfile(cp);
			Log.d(LOG_TAG, "setOutputFile");
			recorder.setOutputFile(lacal_server.getSenderFileDescriptor());
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
//		release();
	}

	private void release() {
		if (recorder != null) {
			recorder.reset();
			if (mCamera != null) {
				mCamera.release();
				mCamera = null;
			}
			recorder.release();
			recorder = null;
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		/* Set preview */
		Log.d(LOG_TAG, "setPreviewDisplay");
		recorder.setPreviewDisplay(mSurfaceHolder.getSurface());
		try {
			Log.d(LOG_TAG, "prepare");
			recorder.prepare();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		/* Start preview */
		Log.d(LOG_TAG, "start");
		recorder.start();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		// TODO:
//		release();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		release();
	}

	/**
	 * Timer
	 * 
	 * @author Maksim Norkin
	 * 
	 */
//	private void launch_thread() {
//
//		new Thread() {
//
//
//			@Override
//			public void run() {
//				// update current imagenb
//
//				//
//				// image_length = buf.length;
//				// //int image_length = 120;
//				// // Builds an RTPpacket object containing the frame
//				// RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb++,
//				// imagenb*FRAME_PERIOD, buf, buf.length);
//				// Log.d(LOG_TAG, "RTPpacket description OK");
//				//
//				// // Get to total length of the full rtp packet to send
//				// int packet_length = rtp_packet.getlength();
//				// Log.d(LOG_TAG, "RTPpacket length: " + packet_length);
//				//
//				// // Retrieve the packate bitstream and store it in an array of
//				// bytes
//				// byte[] packet_bits = new byte[packet_length];
//				// packet_bits = rtp_packet.getpacket();
//				// Log.d(LOG_TAG, "RTPpacket getpacket OK");
//				//
//				// // Send a packet as a DatagramPacket over the UDP socket
//				// senddp = new DatagramPacket(packet_bits, packet_length);
//				// senddp.setAddress(ClientIPAddr);
//				// senddp.setPort(RTP_dest_port);
//				//
//				// Log.d(LOG_TAG, "DatagramPacket OK");
//				// try {
//				// RTPSocket.send(senddp);
//				// } catch (IOException e) {
//				// Log.d(LOG_TAG, senddp.toString());
//				// Log.e(LOG_TAG, "RTPacket send error: " + e.toString());
//				// e.printStackTrace();
//				// }
//				// Log.d(LOG_TAG, "RTPpacket send OK");
//			}
//		}.start();
//	}
}

// Session description protocol
// v= (protocol version)
// o= (owner/creator and session identifier).
// o = <username> <session_id> <version> <network type> <address type> <address>
// s= (session name)
// i=* (session information)
// u=* (URI of description)
// e=* (email address)
// p=* (phone number)
// c=* (connection information - not required if included in all media)
// b=* (bandwidth information)
// One or more time descriptions (see below)
// z=* (time zone adjustments)
// k=* (encryption key)
// a=* (zero or more session attribute lines)
// Zero or more media descriptions (see below)
//
// Time description
// t= (time the session is active)
// r=* (zero or more repeat times)
//
// Media description
// m= (media name and transport address)
// i=* (media title)
// c=* (connection information - optional if included at session-level)
// b=* (bandwidth information)
// k=* (encryption key)
// a=* (zero or more media attribute lines)