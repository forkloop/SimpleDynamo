package edu.buffalo.cse.cse486_586.simpledynamo;

import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
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
	static Map<Integer, SocketChannel> outSocket;
	static SortedMap<String, Integer> nodeMap;
	static Selector selector;
	static int myId;
	static String myIdHash;
	static int emulatorNum=5;
	
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

}
