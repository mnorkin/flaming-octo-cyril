package net.vilkas.keturi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

public class RtspServer extends Thread {
	
	ServerSocket listenSocket;
	Socket RTSPsocket;
	private int state;
	private int RTSPport = 8086;
	private BufferedReader RTSPBufferedReader;
	private BufferedWriter RTSPBufferedWriter;
	private int RTSPSeqNb;
	private int client_port_1;
	private int client_port_2;
	private String CRLF = "\r\n";
	public int RTSP_ID = 12312;
	
	private static final String LOG_TAG = "WifiMedia Rtsp Server";
	
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	final static int SETUP = 3;
	final static int PLAY = 4;
	final static int PAUSE = 5;
	final static int TEARDOWN = 6;
	final static int OPTIONS = 7;
	final static int DESCRIBE = 8;
	RtpStreamer rtp_streamer;

	public RtspServer(LocalServer local_server) {
		
		rtp_streamer = new RtpStreamer( local_server );
		
	}
	
	public void run() {
		
		try {
			
			listenSocket = new ServerSocket(RTSPport);
			RTSPsocket = listenSocket.accept();
			listenSocket.close();
			
		} catch (IOException e) {
			Log.e(LOG_TAG, "listenSocket error " + e.getMessage());
		}

		// Initiate RTPstate
		state = INIT;

		try {
			RTSPBufferedReader = new BufferedReader(new InputStreamReader(
					RTSPsocket.getInputStream()));
			RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(
					RTSPsocket.getOutputStream()));
		} catch (IOException e) {
			Log.e(LOG_TAG, "reader error");
		}
		
		// Wait for the SETUP message from the client
		int request_type = 0;

		// Loop to handle RTSP requests
		while (true) {
			// Parse the request
			try {
				// Log.d(LOG_TAG, "Listening RTSP request");
				request_type = this.parse_RTSP_request();
				// Log.d(LOG_TAG, "DONE");
			} catch (NumberFormatException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			Log.d(LOG_TAG, "Request_type: " + request_type);

			if ((request_type == PLAY) && (state == READY)) {

				/*
				 * PLAY
				 */

				// Send back response
				this.send_RTSP_response();

				// Update the state
				state = PLAYING;

				/* Launch playback */
				rtp_streamer.start();

				Log.d(LOG_TAG, "State is PLAYING");

			} else if ((request_type == PAUSE) && (state == PLAYING)) {

				/*
				 * PAUSE
				 */

				// Send back response
				this.send_RTSP_response();

				// Stop the timer
				// launch_thread();

				// Update the state
				state = READY;

				Log.d(LOG_TAG, "State is READY");

			} else if (request_type == TEARDOWN) {

				/*
				 * TEARDOWN
				 */

				this.send_RTSP_response();

				// timer.cancel();

				try {
					RTSPsocket.close();
				} catch (IOException e) {
					Log.d(LOG_TAG, "RTSP socket close error");
				}

				rtp_streamer.interrupt();
				
			} else if (request_type == OPTIONS) {

				/*
				 * OPTIONS
				 */

				this.send_RTSP_options();

			} else if (request_type == DESCRIBE) {

				/*
				 * DESCRIBE
				 */
				this.send_RTSP_describe();

			} else if (request_type == SETUP) {

				/*
				 * SETUP
				 */

				state = READY;
				Log.d(LOG_TAG, "New RTSP state: READY");

				// Send response
				this.send_RTSP_setup();

				// Initialize VideoStreaming
				try {
					// video = new VideoStream(VideoFileName);
					Log.d(LOG_TAG, "Video stream OK");
				} catch (Exception e1) {
					Log.e(LOG_TAG, "Video stream error");
				}

				// Initialize RTP socket
				rtp_streamer.set_client_port( client_port_1 );
				rtp_streamer.set_client_address( RTSPsocket.getInetAddress() );
				
				try {
					rtp_streamer.init();
				} catch (SocketException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		}
		
	}
	
	private int parse_RTSP_request() throws NumberFormatException, IOException {

		int request_type = -1;

		boolean message_fully_received = false;

		// Read while message is not fully received
		while (!message_fully_received) {

			if (RTSPBufferedReader.ready()) {

				try {

					String RequestLine = RTSPBufferedReader.readLine();

					// Description message
					if (RequestLine.startsWith("DESCRIBE")) {
						request_type = DESCRIBE;
						// Video File Name

					}

					// Options message
					if (RequestLine.startsWith("OPTIONS")) {
						request_type = OPTIONS;
					}

					// Setup message
					if (RequestLine.startsWith("SETUP")) {
						request_type = SETUP;
					}

					// Play message
					if (RequestLine.startsWith("PLAY")) {
						request_type = PLAY;
					}

					// Teardown message
					if (RequestLine.startsWith("TEARDOWN")) {
						request_type = TEARDOWN;
					}

					// Sequece counter update
					if (RequestLine.startsWith("CSeq")) {
						Pattern p = Pattern.compile("CSeq: (\\d+)",
								Pattern.CASE_INSENSITIVE);
						Matcher m = p.matcher(RequestLine);
						if (m.find()) {
							RTSPSeqNb = Integer.parseInt(m.group(1));
						}
					}

					// Transport message
					if (RequestLine.startsWith("Transport: ")) {
						Pattern p = Pattern.compile(
								"client_port=(\\d+)-(\\d+)",
								Pattern.CASE_INSENSITIVE);
						Matcher m = p.matcher(RequestLine);

						if (m.find()) {
							Log.d(LOG_TAG, "client_port found");
							client_port_1 = Integer.parseInt(m.group(1));
							client_port_2 = Integer.parseInt(m.group(2));
						}
					}

					// Message finished
					if (RequestLine.length() == 0) {
						message_fully_received = true;
						Log.d(LOG_TAG, "Message received");
					}

					// DEBUG
					Log.d(LOG_TAG, "RequestLine: " + RequestLine);

				} catch (IOException e) {
					Log.e(LOG_TAG, "Request error " + e.getMessage());
				}
			}
		}

		return request_type;

	}

	private void send_RTSP_response() {

		try {
			RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
			RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);
			// Need to send empty message
			RTSPBufferedWriter.write("" + CRLF);
			RTSPBufferedWriter.flush();
			Log.d(LOG_TAG, "RTSP response sent");
		} catch (Exception e) {
			Log.e(LOG_TAG, "response error");
		}
	}

	private void send_RTSP_options() {
		try {
			RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
			RTSPBufferedWriter
					.write("Public: DESCRIBE, SETUP, TEARDOWN, PLAY, SET_PARAMETER, GET_PARAMETER"
							+ CRLF);
			// Need to send empty message
			RTSPBufferedWriter.write("" + CRLF);
			RTSPBufferedWriter.flush();

			Log.d(LOG_TAG, "RTSP OPTIONS SENT");
		} catch (Exception e) {
			Log.e(LOG_TAG, "response error");
		}
	}

	private void send_RTSP_setup() {
		
		try {
			RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
			RTSPBufferedWriter.write("Date: " + getstringtime() + CRLF);
			RTSPBufferedWriter.write("Session: " + RTSP_ID + CRLF);
			RTSPBufferedWriter.write("Transport: RTP/AVP/UDP;unicast;"
					+ "client_port=" + String.format("%d", client_port_1) + "-"
					+ String.format("%d", client_port_2) + ";" + "server_port="
					+ String.format("%d", rtp_streamer.get_server_port()) + "-"
					+ String.format("%d", rtp_streamer.get_server_port() + 1) + ";source="
					+ RTSPsocket.getLocalAddress().getHostAddress() + CRLF);
			// Need to send empty message
			RTSPBufferedWriter.write("" + CRLF);
			RTSPBufferedWriter.flush();

			Log.d(LOG_TAG, "RTSP SETUP SENT");
		} catch (Exception e) {
			Log.e(LOG_TAG, "response error");
		}
	}

	private void send_RTSP_describe() {

		String sdp_message = "v=0" + CRLF + "o=- " + gettimestamp() + " "
				+ gettimestamp() + " " + "IN " + "IP4 "
				+ RTSPsocket.getLocalAddress().getHostAddress() + " " + CRLF
				+ "s=WifiMedia" + CRLF
				+ "e=Maksim Norkin (maksim.norkin@ieee.org)" + CRLF
				+ "c=IN IP4 " + RTSPsocket.getLocalAddress().getHostAddress()
				+ "/127" + CRLF + "t=" + gettimestamp() + " " + "0" + CRLF
				+ "b=RR:0" + CRLF + "m=video " + rtp_streamer.get_server_port() + " RTP/AVP 96" + CRLF
				+ "a=rtpmap:96 H264/90000" + CRLF
				+ "a=fmtp:96 packetization-mode=1" + CRLF;
		// TODO: audio-description

		try {
			RTSPBufferedWriter.write("RTSP/1.0 200 OK" + CRLF);
			RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);
			RTSPBufferedWriter.write("Content-Base: rtsp://"
					+ RTSPsocket.getLocalAddress().getHostAddress() + "/"
					+ "" + CRLF);
			RTSPBufferedWriter.write("Content-Type: application/sdp" + CRLF);
			RTSPBufferedWriter.write("Content-Length: "
					+ (sdp_message.length() + 4) + CRLF);
			RTSPBufferedWriter.write("" + CRLF);
			RTSPBufferedWriter.write(sdp_message + CRLF);

			RTSPBufferedWriter.write("" + CRLF);
			RTSPBufferedWriter.flush();
			Log.d(LOG_TAG, "RTSP DESCRIBE SENT");
			// Log.d(LOG_TAG, "Content-Base: " +
			// RTSPsocket.getLocalAddress().getHostAddress() + "/" +
			// VideoFileName + CRLF);
		} catch (Exception e) {
			Log.e(LOG_TAG, "response error");
		}
	}

	private long gettimestamp() {
		Timestamp timestamp = new Timestamp(Calendar.getInstance().getTime().getTime());
		return timestamp.getTime();
	}
	
	private String getstringtime() {
		Timestamp timestamp = new Timestamp(Calendar.getInstance().getTime().getTime());
		return timestamp.toString();
	}

	public int getRtspId() {
		return RTSP_ID;
	}
}
