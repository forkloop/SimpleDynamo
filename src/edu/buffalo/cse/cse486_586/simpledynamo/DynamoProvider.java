package edu.buffalo.cse.cse486_586.simpledynamo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;


import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.util.Log;

public class DynamoProvider extends ContentProvider {

	static Boolean lock;
	private int id, pid;
	static boolean flag;
	static ReplyMsg rm;
	
	private static final String[] SCHEMA = {"provider_key", "provider_value"};
	private static final String DBNAME = "dynamo";
	private static final String MSG_TABLE_NAME = "fl";
	private static final String AUTHORITY = "edu.buffalo.cse.cse486_586.simpledynamo.provider";
	
	private DynamoOpenHelper dbHelper;
	private static Map<String, String> msgProjectionMap;
	private static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/msgs");
	private static final UriMatcher sUriMatcher;
	private SQLiteDatabase db;
	
	static Map<String, String> myData;
	static Map<Integer, Map<String, String>> peerData;
	static Map<String, String> myTmp;
	static Map<Integer, Map<String, String>> peerTmp;
	static Map<String, Integer> putQ;
	static Map<String, Integer> getQ;
	static Map<String, Integer> tputQ;
	static Map<String, Integer> tgetQ;
	
	private SocketChannel sc;
	
	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(AUTHORITY, "msgs", 1);			// all messages
		sUriMatcher.addURI(AUTHORITY, "msgs/#", 2);		// fetch specific message
		
		msgProjectionMap = new HashMap<String, String>();
		msgProjectionMap.put("provider_key", "provider_key");
		msgProjectionMap.put("provider_value", "provider_value");
	}

	
	@Override
	public boolean onCreate() {
		
		dbHelper = new DynamoOpenHelper(getContext(), DBNAME);
		TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
		id = Integer.parseInt(tel.getLine1Number().substring(tel.getLine1Number().length()-4));
		Log.i("log", "My id is "+id);
		try {
			SimpleDynamoApp.myIdHash = SimpleDynamoApp.genHash(""+id);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		SimpleDynamoApp.myId = id;
		SimpleDynamoApp.outSocket = new HashMap<Integer, SocketChannel>();
		SimpleDynamoApp.nodeMap = new TreeMap<String, Integer>();
		SimpleDynamoApp.nodeMap.put(SimpleDynamoApp.myIdHash, SimpleDynamoApp.myId);
		lock = new Boolean(true);

		myData = new HashMap<String, String>();
		peerData = new HashMap<Integer, Map<String, String>>();
		putQ = new HashMap<String, Integer>();
		getQ = new HashMap<String, Integer>();
		for ( int n=0; n<SimpleDynamoApp.N-1; n++ ){
			//XXX
		}
		Intent intent = new Intent(getContext().getApplicationContext(), ListenService.class);
		intent.putExtra("myPort", 10000);
		getContext().getApplicationContext().startService(intent);

		return true;
	}
	
	
	@Override
	public Uri insert(Uri uri, ContentValues values) {
	
		//XXX no URI check !!!
		String key = (String) values.get("provider_key");
		Log.i("log", "provider_key:" + key);
		String keyHash;
		try {
			keyHash = SimpleDynamoApp.genHash(key);
			pid = SimpleDynamoApp.checkRange(keyHash);
			if ( pid == id ) {
				myTmp.put(key, (String) values.getAsString("provider_value"));
				putQ.put(key, 1);
				//XXX need block
			}
			else {
				InsertMsg insMsg = new InsertMsg();
				insMsg.key = (String) values.get("provider_key");
				insMsg.value = (String) values.get("provider_value");
				insMsg.owner = pid;
				byte[] msgByte = SimpleDynamoApp.getMsgStream(insMsg);
				sc = SimpleDynamoApp.outSocket.get(pid);
				sc.write(ByteBuffer.wrap(msgByte));

				synchronized (lock) {
					lock.wait(100);
					if ( !flag ) {
						// XXX coordinator dead, send to its successor
					}
				}
			}
		} catch(NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		String key = selection;
		Log.i("log", "KEY: "+key);
		String keyHash;
		
		try {
			keyHash = SimpleDynamoApp.genHash(key);
			pid = SimpleDynamoApp.checkRange(keyHash);
			if ( pid == id ) {
				getQ.put(key, 1);
				MatrixCursor mc = new MatrixCursor(SCHEMA);
				String[] v = {key, myData.get(key)};
				mc.addRow(v);
				mc.setNotificationUri(getContext().getContentResolver(), uri);
				return mc;
			}
			else {
				InquiryMsg inqMsg = new InquiryMsg();
				inqMsg.key = key;
				inqMsg.owner = pid;
				inqMsg.sender = id;
				byte[] msgByte = SimpleDynamoApp.getMsgStream(inqMsg);
				sc = SimpleDynamoApp.outSocket.get(pid);
				sc.write(ByteBuffer.wrap(msgByte));
				
				synchronized (lock) {
						lock.wait(100);
						if ( rm != null ) {
							MatrixCursor mc = new MatrixCursor(SCHEMA);
							String[] v = {rm.key, rm.value};
							mc.addRow(v);
							mc.setNotificationUri(getContext().getContentResolver(), uri);
							return mc;
						}
						else {
							//XXX the coordinator is dead, ask its successor
						}
				}
			}
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	@Override
	public String getType(Uri uri) {
		//FIXME
		return null;
	}

	
	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// FIXME
		return 0;
	}

	
	@Override
	public int delete(Uri arg0, String arg1, String[] arg2) {
		// FIXME
		return 0;
	}
}
