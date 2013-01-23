package net.vilkas.keturi;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import android.os.SystemClock;
import android.util.Log;

public class RtpStreamer extends Thread {

	private int packet_size = 1400;
	private int rtp_header_length = 40;
	private int nalu_length = 0;
	private byte[] buf;
	private long oldtime;
	private long delay;

	private int MJPEG_TYPE = 26;
	private String LOG_TAG = "WifiMedia Rtp Streamer";
	private int frame_counter;

	private DatagramPacket packet;
	private LocalServer local_server;
	private DatagramSocket socket;
	private int client_port = 0;
	private InetAddress client_address = null;
	private int server_port = 7000;
	private InetAddress server_address = null;

	private InputStream fis = null;
	private int oldavailable;

	public RtpStreamer(LocalServer local_server) {
		this.local_server = local_server;

		buf = new byte[2056];
	}

	public void set_client_port(int client_port) {
		this.client_port = client_port;
	}

	public void set_client_address(InetAddress client_address) {
		this.client_address = client_address;
	}

	public void init() throws SocketException {
		socket = new DatagramSocket(server_port, server_address);

		try {
			fis = local_server.getInputStream();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	public static String bytesToHex(byte[] bytes) {
		final char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8',
				'9', 'A', 'B', 'C', 'D', 'E', 'F' };
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for (int j = 0; j < bytes.length; j++) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	public int get_server_port() {
		return server_port;
	}

	public void run() {
		
		Log.d(LOG_TAG, "Rtp Streamer running");
		oldtime = SystemClock.elapsedRealtime();

		int sent_length = 1;
		int len = 0;

		/* Skip MPEG header */
		/*
		 * Here we just skip the mpeg4 header
		 */
		try {

			// Skip all atoms preceding mdat atom
			while (true) {
				fis.read(buf, 0, 8);
				if (buf[4] == 'm' && buf[5] == 'd' && buf[6] == 'a' && buf[7] == 't') break;
				
				len = (buf[3] & 0xFF) + (buf[2] & 0xFF) * 256 + (buf[1] & 0xFF) * 65536;
				if (len <= 0) return;
				
				Log.e(LOG_TAG,"Atom skipped: "+bytesToHex(buf)+" size: "+len);
				
				fis.read(buf, 0, len - 8);
			}

			// Some phones do not set length correctly when stream is not
			// seekable, still we need to skip the header
//			if (len <= 0) {
//				while (true) {
//					while (fis.read() != 'm')
//						;
//					fis.read(buf, 0, 3);
//					if (buf[0] == 'd' && buf[1] == 'a' && buf[2] == 't')
//						break;
//				}
//			}
			
			len = 0;
			
		} catch (IOException e) {
			return;
		}

		while (!Thread.interrupted()) {

			frame_counter += 1;

			try {
				fis.read(buf, 0, 4);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			// Calculate the length of the NAL unit
			nalu_length = buf[3] & 0xFF | (buf[2] & 0xFF) << 8
					| (buf[1] & 0xFF) << 16 | (buf[0] & 0xFF) << 24;

			Log.d(LOG_TAG, bytesToHex(buf));

			Log.d(LOG_TAG, "Nalu length: " + nalu_length);

			if (nalu_length <= packet_size - rtp_header_length - 2
					&& nalu_length != 0) {
				/* Check if the frame can fit in one packet */

				try {
					fis.read(buf, 1, nalu_length);
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}

				long now = SystemClock.elapsedRealtime();

				if (now - oldtime < delay) {

					try {
						Thread.sleep(delay - (now - oldtime));
					} catch (InterruptedException e) {
						e.printStackTrace();
						this.stop();
					}

				}

				oldtime = SystemClock.elapsedRealtime();
				RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, frame_counter,
						(int) now, buf, buf.length);

				rtp_packet.mark_packet();

				packet = new DatagramPacket(rtp_packet.getpacket(),
						rtp_packet.getpacket().length);
				packet.setAddress(client_address);
				packet.setPort(client_port);

				try {

					socket.send(packet);

				} catch (IOException e) {

					Log.d(LOG_TAG, packet.toString());
					e.printStackTrace();
					this.stop();

				}

			} else if (nalu_length == 0) {

				Log.d(LOG_TAG, "Nalu length is zero");

				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

			} else {
				/* If not -- slit it to smaller packages */

				/* Set FU-A header */
				buf[1] = (byte) (buf[0] & 0x1F);
				/* Set FU indicator NRI */
				buf[1] += 0x80;

				/* Set FU-A indicator */
				buf[0] = (byte) ((buf[0] & 0x60) & 0xFF);
				/* Start bit */
				buf[0] += 28;

				while (sent_length < nalu_length) {

					len = fill(2, nalu_length - sent_length > packet_size
							- rtp_header_length ? packet_size
							- rtp_header_length : nalu_length - sent_length);

					if (len < 0) {
						return;
					}

					sent_length += len;

					long now = SystemClock.elapsedRealtime();

					RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE,
							frame_counter, (int) now, buf, buf.length);

					if (sent_length >= nalu_length) {
						/* End bit */
						buf[1] += 0x40;
						rtp_packet.mark_packet();

					} else {

						rtp_packet.unmark_packet();

					}

					packet = new DatagramPacket(rtp_packet.getpacket(),
							rtp_packet.getpacket().length);
					packet.setAddress(client_address);
					packet.setPort(client_port);

					try {
						socket.send(packet);
					} catch (IOException e) {
						e.printStackTrace();
						this.interrupt();
					}

					/* Switch start bit */
					buf[1] = (byte) (buf[1] & 0x7F);
				}
			}
			/* Exit for debug reasons */
			// return;
		}

	}

	private int fill(int offset, int length) {

		int sum = 0, len, available;

		while (sum < length) {
			try {
				available = fis.available();
				len = fis.read(buf, offset + sum, length - sum);
				// Log.e(SpydroidActivity.LOG_TAG,"Data read: "+fis.available()+","+len);

				if (oldavailable < available) {
					// We don't want fis.available to reach 0 because it
					// provokes choppy streaming (which is logical because it
					// causes fis.read to block the thread periodically).
					// So here, we increase the delay between two send calls to
					// induce more buffering (and the buffer is basically the
					// fis input stream)
					if (oldavailable < 10000) {
						delay++;
						// Log.e(SpydroidActivity.LOG_TAG,"Inc delay: "+delay);
					}
					// But we don't want to much buffering either:
					else if (oldavailable > 10000) {
						delay--;
						// Log.e(SpydroidActivity.LOG_TAG,"Dec delay: "+delay);
					}
				}
				oldavailable = available;
				if (len < 0) {
					Log.e(LOG_TAG, "Read error");
				} else
					sum += len;
			} catch (IOException e) {
				this.stop();
				return sum;
			}
		}

		return sum;

	}

}
