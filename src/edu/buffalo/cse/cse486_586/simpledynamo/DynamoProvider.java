package edu.buffalo.cse.cse486_586.simpledynamo;

import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.TreeMap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.util.Log;

public class DynamoProvider extends ContentProvider {

	static Boolean lock;
	private int id;
	
	private static final String[] SCHEMA = {"provider_key", "provider_value"};
	private static final String DBNAME = "dynamo";
	private static final String MSG_TABLE_NAME = "fl";
	private static final String AUTHORITY = "edu.buffalo.cse.cse486_586.simpledynamo.provider";
	
	private DynamoOpenHelper dbHelper;
	private static HashMap<String, String> msgProjectionMap;
	private static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/msgs");
	private static final UriMatcher sUriMatcher;
	private SQLiteDatabase db;

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
		
		Intent intent = new Intent(getContext().getApplicationContext(), ListenService.class);
		intent.putExtra("myPort", 10000);
		getContext().getApplicationContext().startService(intent);

		return true;
	}
	
	
	@Override
	public Uri insert(Uri uri, ContentValues values) {
	
		return null;
	}
	
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

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
