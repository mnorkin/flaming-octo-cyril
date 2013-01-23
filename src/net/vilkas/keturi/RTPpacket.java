package net.vilkas.keturi;

import android.util.Log;

public class RTPpacket {
	
	static final public String LOG_TAG = "WifiMedia Rtp Packet";
	
	// Size of the RTP header in bytes
	static int HEADER_SIZE = 12;
	
	// Fields that compose the RTP header
	public int Version;
	public int Padding;
	public int Extension;
	public int CC;
	public int Marker = 0;
	public int PayloadType;
	public int SequenceNumber;
	public int TimeStamp;
	public int Ssrc;
	
	// Bistream of the RTP header
	public byte[] header;
	
	// Size of the RTP payload
	public int payload_size;
	
	// Bistream of the RTP payload
	public byte[] payload;
	
	// Bitestream of RTP packet
	public byte[] packet;
	
	
	/**
	 * 
	 * @param PType
	 * @param Framenb
	 * @param Time
	 * @param data
	 * @param data_length
	 */
	public RTPpacket(int PType, int Framenb, int Time, byte[] data, int data_length) {
		// Fill the header
		Version = 2;
		Marker = 0;
		Padding = 0;
		Extension = 0;
		CC = 0;
		Ssrc = 0;
		
		// Fill changing header fields:
		SequenceNumber = Framenb;
		TimeStamp = Time;
		PayloadType = PType;
		
		// Build the header bite stream
		
		header = new byte[HEADER_SIZE];
		
		header[0] = (byte)( header[0] | Version << 6);
		header[0] = (byte)( header[0] | Padding << 5);
		header[0] = (byte)( header[0] | Extension << 4);
		header[0] = (byte)( header[0] | CC << 3);
		
		header[1] = (byte)( header[1] | Marker << 7);
		header[1] = (byte)( header[1] | PayloadType << 5);
		
		header[2] = (byte)( SequenceNumber >> 8);
		
		header[3] = (byte)( SequenceNumber & 0xFF);
		
		header[4] = (byte)( TimeStamp >> 24 );
		
		header[5] = (byte)( TimeStamp >> 16 );
		
		header[6] = (byte)( TimeStamp >> 8);
		
		header[7] = (byte)( TimeStamp & 0xFF);
		
		header[8] = (byte)( Ssrc >> 24 );
		
		header[9] = (byte)( Ssrc >> 16 );
		
		header[10] = (byte)( Ssrc >> 8 );
		
		header[11] = (byte)( Ssrc & 0xFF );
		
		// Fill the payload bite stream
		payload_size = data_length;
		payload = new byte[data_length];
		
//		System.out.println("Data length: " + data_length);
//		System.out.println("Data length 2: " + data.length);
		//this.payload = data;
		for (int i = 0; i < payload_size; i++) {
			payload[i] = data[i];
		}

//		System.out.println("Defined header: " + header);
//		System.out.println("Defined playload: " + payload);
	}
	
	/**
	 * 
	 * @param packet
	 * @param packet_size
	 */
	public RTPpacket(byte[] packet, int packet_size) {
		
		// Fill the geader
		Version = 2;
		Padding = 0;
		Extension = 0;
		CC = 0;
		Ssrc = 0;
		
		// Check if total packet size is lower than the header size
		if ( packet_size >= HEADER_SIZE ) {
			// Get header bitstream
			header = new byte[HEADER_SIZE];
			for (int i = 0; i < HEADER_SIZE; i++) {
				header[i] = packet[i];
			}
			
			// Get the payload bitstream
			payload_size = packet_size - HEADER_SIZE;
			payload = new byte[payload_size];
			for ( int i = HEADER_SIZE; i < packet_size; i++ ) {
				payload[i-HEADER_SIZE] = packet[i];
			}
			
			//interpret the changing fields of the header:
			PayloadType = header[1] & 127;
			SequenceNumber = unsigned_int(header[3]) + 256*unsigned_int(header[2]);
			TimeStamp = unsigned_int(header[7]) + 256*unsigned_int(header[6]) + 65536*unsigned_int(header[5]) + 16777216*unsigned_int(header[4]);
		}
	}
	
	public void mark_packet() {
		Marker = 1;
		header[1] = (byte)( header[1] | Marker << 7);
		Log.d(LOG_TAG, "Market package");
	}
	
	public void unmark_packet() {
		Marker = 0;
		header[1] = (byte)( header[1] | Marker << 7);
		Log.d(LOG_TAG, "Unmarket package");
	}
	/**
	 * 
	 * @param data
	 * @return
	 */
	public int getpayload(byte[] data) {
		// Why to do this loop?
		for ( int i = 0; i < payload_size; i++) {
			data[i] = payload[i];
		}
		return payload_size;
	}
	
	/**
	 * Function returns payload length
	 * @return
	 */
	public int getpayload_length() {
		return payload_size;
	}
	
	/**
	 * Function returns RTP packet length
	 * @return
	 */
	public int getlength() {
		return payload_size + HEADER_SIZE + 1;
	}
	
	/**
	 * 
	 * @param packet
	 * @return
	 */
	public byte[] getpacket() {
		
		packet = new byte[this.getlength()];
		
		// Construct the packet
		for ( int i = 0; i < HEADER_SIZE; i++ ) {
			packet[i] = header[i];
		}
		
//		System.out.println("Received payload: " + this.payload);
		
		for ( int i = 0; i < payload_size; i++ ) {
			packet[i+HEADER_SIZE] = payload[i];
		}
		
		return packet;
	}
	
	public int gettimestamp() {
		return TimeStamp;
	}
	
	public int getsequencenumber() {
		return SequenceNumber;
	}
	
	public int getpayloadtype() {
		return PayloadType;
	}
	
	static int unsigned_int(int nb) {
		if (nb >= 0) {
	      return(nb);
		} else {
	      return(256+nb);
		}
	}

}
// RTP PACKAGE
//0                   1                   2                   3
//0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|V=2|P|X|  CC   |M|     PT      |       sequence number         | RTP
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|                           timestamp                           | Header
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|           synchronization source (SSRC) identifier            |
//+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
//|            contributing source (CSRC) identifiers             |
//|                             ....                              |
//+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+=+
//|                                                               | RTP
//|       MPEG-4 Visual stream (byte aligned)                     | Pay-
//|                                                               | load
//|                               +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//|                               :...OPTIONAL RTP padding        |
//+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
