package edu.buffalo.cse.cse486_586.simpledynamo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

public class SimpleDynamoApp extends Application {
	
	static final Uri TABLE_URI = Uri.parse("content://edu.buffalo.cse.cse486_586.simpledynamo.provider/msgs");
	static final int N=3;
	static final int R = 2;
	static final int W = 2;
	static Map<Integer, SocketChannel> recvSocket;
	static Map<Integer, SocketChannel> sendSocket;
	static SortedMap<String, Integer> nodeMap;
//	static Map<Integer, String> nodeHash;
	static Selector selector;
	static int myId;
	static String myIdHash;
	static int emulatorNum=3;
	static int testNum=5;
	static List<Integer> succId;
	
	static	final int INS_MSG = 1;
	static	final int INQ_MSG = 2;
	static	final int QUO_MSG = 3;
	static	final int ACK_MSG = 4;
	static	final int REP_MSG = 5;
	static	final int CON_MSG = 6;
	static	final int REC_MSG = 7;
	static 	final int JOIN_MSG = 8;
//	static enum MsgType { INS_MSG, INQ_MSG, QUO_MSG, ACK_MSG, REP_MSG, CON_MSG, REC_MSG };
	
	static String genHash(String input) throws NoSuchAlgorithmException {

		MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
		byte[] sha1Hash = sha1.digest(input.getBytes());
		Formatter formatter = new Formatter();
		
		for (byte b : sha1Hash) {
			formatter.format("%02x", b);
		}
		Log.i("log", "SHA for " + input + " is: " + formatter.toString());
		return formatter.toString();
		}

	static int checkRange( String s ) {
		
		/* keySet() is sorted */
		String[] key = nodeMap.keySet().toArray(new String[0]);
		for ( int x=0; x<nodeMap.size(); x++ ) {
			if ( s.compareTo(key[x])<=0 ) {
				return nodeMap.get(key[x]);
			}
		}
		return nodeMap.get(key[0]);
	}
	
	
	static int[] getSuccessor(int id) {
	
		/* succId updated by ListenService */
		int idx = succId.indexOf(id);
		int[] ret = {succId.get((idx+1)%succId.size()), succId.get((idx+2)%succId.size())};
		return ret;
	}
	
	
	static byte[] getMsgStream(Object msg) {
		
		byte[] bytes = null;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(msg);
			oos.flush();
			oos.close();
			bos.close();
			bytes = bos.toByteArray();			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return bytes;		
	}
	
}
