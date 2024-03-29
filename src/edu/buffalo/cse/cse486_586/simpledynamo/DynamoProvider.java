package edu.buffalo.cse.cse486_586.simpledynamo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
	static AckMsg rm;
	
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
		SimpleDynamoApp.recvSocket = new HashMap<Integer, SocketChannel>();
		SimpleDynamoApp.sendSocket = new HashMap<Integer, SocketChannel>();
		SimpleDynamoApp.nodeMap = new TreeMap<String, Integer>();
		
		/* hardcoded */
		for (int x=0; x<SimpleDynamoApp.emulatorNum; x++) {
			int tid = 5554+x*2;
			if ( tid == id ) {
				SimpleDynamoApp.nodeMap.put(SimpleDynamoApp.myIdHash, id);
			}
			else {
				try {
					SimpleDynamoApp.nodeMap.put(SimpleDynamoApp.genHash(""+tid), tid);
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
			}
		}
		SimpleDynamoApp.succId = new ArrayList<Integer>();
		Set<String> k = SimpleDynamoApp.nodeMap.keySet();
		for ( String s : k ) {
			int x = SimpleDynamoApp.nodeMap.get(s);
			SimpleDynamoApp.succId.add(x);
		}
		lock = new Boolean(false);

		myData = new HashMap<String, String>();
		peerData = new HashMap<Integer, Map<String, String>>();
		putQ = new HashMap<String, Integer>();
		getQ = new HashMap<String, Integer>();
		tputQ = new HashMap<String, Integer>();
		tgetQ = new HashMap<String, Integer>();
		myTmp = new HashMap<String, String>();
		peerTmp = new HashMap<Integer, Map<String, String>>();

		int[] pred = SimpleDynamoApp.getPredecessor(id);
		for ( int x=0; x<pred.length; x++ ) {
			Map<String, String> t = new HashMap<String, String>();
			peerData.put(pred[x], t);
			Map<String, String> tt = new HashMap<String, String>();
			peerTmp.put(pred[x], tt);
		}
		
		/* start */
		Intent intent = new Intent(getContext().getApplicationContext(), ListenService.class);
		intent.putExtra("myPort", 10000);
		getContext().getApplicationContext().startService(intent);

		return true;
	}
	
	
	@Override
	public Uri insert(Uri uri, ContentValues values) {
	
		//FIXME NO URI check !!!
		String key = (String) values.get("provider_key");
		Log.i("log", "Insert provider_key:" + key);
		String keyHash;
		
		try {
			keyHash = SimpleDynamoApp.genHash(key);
			pid = SimpleDynamoApp.checkRange(keyHash);
			int[] succ = SimpleDynamoApp.getSuccessor(pid);
			
			if ( pid == id ) {
				myTmp.put(key, (String) values.get("provider_value"));
				putQ.put(key, 1);
				/* Replicate insert */
				for ( int x=0; x<succ.length; x++ ) {
					sc = SimpleDynamoApp.sendSocket.get(succ[x]);
					if (sc != null) {
						ReplicateMsg repMsg = new ReplicateMsg();
						repMsg.key = key;
						repMsg.value = (String) values.getAsString("provider_value");
						repMsg.type = 'p';
						repMsg.owner = pid;
						repMsg.sender = id;
						byte[] msgByte = SimpleDynamoApp.getMsgStream(repMsg);
						try {
							sc.write(ByteBuffer.wrap(msgByte));
						} catch (ClosedChannelException e) {
							SimpleDynamoApp.sendSocket.put(succ[x], null);
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				} // FIXME  ? BLOCK ?
			}
			else {
				rm = null;
				
				InsertMsg insMsg = new InsertMsg();
				insMsg.key = (String) values.get("provider_key");
				insMsg.value = (String) values.get("provider_value");
				insMsg.owner = pid;
				insMsg.sender = id;
				byte[] msgByte = SimpleDynamoApp.getMsgStream(insMsg);
				sc = SimpleDynamoApp.sendSocket.get(pid);
				if (sc != null) {
					try {
						sc.write(ByteBuffer.wrap(msgByte));
						Log.i("log", "Write to " + pid + " : " + sc.isConnected());
						synchronized (lock) {
							lock.wait(300);	/* wait for ack */
						}
					} catch (ClosedChannelException e) {
						SimpleDynamoApp.sendSocket.put(pid, null);
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				/* coordinator dead, send to its FIRST successor */
				if ( rm == null ) {
				//	SimpleDynamoApp.sendSocket.put(pid, null);
					Log.i("log", "Coordinator " + pid + " is DEAD");
					if ( succ[0] != id ) {
						sc = SimpleDynamoApp.sendSocket.get(succ[0]);
						if (sc != null) {
							try {
								sc.write(ByteBuffer.wrap(msgByte));
							} catch (ClosedChannelException e) {
								SimpleDynamoApp.sendSocket.put(succ[0], null);
								e.printStackTrace();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
					else {
						Map<String, String>t = new HashMap<String, String>();
						t.put(key, (String) values.get("provider_value"));
						peerTmp.put(pid, t);
						tputQ.put(key, 1);
						for ( int x=1; x<succ.length; x++ ) {
							Intent repIntent = new Intent(getContext().getApplicationContext(), SendService.class);
							repIntent.putExtra("key", key);
							repIntent.putExtra("value", (String) values.get("provider_value"));
							repIntent.putExtra("sender", succ[x]);
							repIntent.putExtra("owner", pid);
							repIntent.putExtra("action", 'p');
							repIntent.putExtra("type", SimpleDynamoApp.REP_MSG);
							getContext().getApplicationContext().startService(repIntent);
						}
					}
				} //FIXME again ? BLOCK ?
			}
		} catch(NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		/** dump */
		if ( selectionArgs != null ) {
	//	if ( selectionArgs[0].equals("dump") ) {
			MatrixCursor mc = new MatrixCursor(SCHEMA);
			db = dbHelper.getWritableDatabase();
			String[] v0 = {"@"+id, ""};
			mc.addRow(v0);
			Set<String> myKeys = myData.keySet();
			for ( String s : myKeys ) {
				String[] v = {s, myData.get(s)};
				mc.addRow(v);
				ContentValues inserted = new ContentValues();
				inserted.put("provider_key", v[0]);
				inserted.put("provider_value",v[1]);
			//	db.insert(MSG_TABLE_NAME, null, inserted);
			}
			Set<Integer> pred = peerData.keySet();
			for ( int x : pred ) {
				Map<String, String> data = peerData.get(x);
				String[] v1 = {"@"+x, ""};
				mc.addRow(v1);
				Set<String> peerKeys = data.keySet();
				for ( String s : peerKeys ) {
					String[] v = {s, data.get(s)};
					mc.addRow(v);
					ContentValues inserted = new ContentValues();
					inserted.put("provider_key", v[0]);
					inserted.put("provider_value",v[1]);
				//	db.insert(MSG_TABLE_NAME, null, inserted);
				}
			}
			return mc;
		}
		
		/** inquiry */
		String key = selection;
		Log.i("log", "INQUIRY KEY: "+key);
		String keyHash;
		
		try {
			keyHash = SimpleDynamoApp.genHash(key);
			pid = SimpleDynamoApp.checkRange(keyHash);
			int[] succ = SimpleDynamoApp.getSuccessor(pid);
			/* I am the coordinator */
			if ( pid == id ) {
				getQ.put(key, 1);
				/* Replicate inquiry */
				for ( int x=0; x<succ.length; x++) {
					sc = SimpleDynamoApp.sendSocket.get(succ[x]);
					if (sc != null) {
						ReplicateMsg repMsg = new ReplicateMsg();
						repMsg.key = key;
						repMsg.type = 'g';
						repMsg.sender = id;
						repMsg.owner = pid;
						repMsg.asker = id;
						byte[] msgByte = SimpleDynamoApp.getMsgStream(repMsg);
						try {
							Log.i("log", "Coordinator ask for quorum from: " + succ[x]);
							sc.write(ByteBuffer.wrap(msgByte));
						} catch (ClosedChannelException e) {
							SimpleDynamoApp.sendSocket.put(succ[x], null);
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
				/* wait for quorum */
				synchronized(lock) {
					lock.wait();
				}
				/* Since I am the coordinator, I can
				 * get the data directly
				 */
				MatrixCursor mc = new MatrixCursor(SCHEMA);
				String[] v = {key, myData.get(key)};
				mc.addRow(v);
				mc.setNotificationUri(getContext().getContentResolver(), uri);
				return mc;
			}
			/* Send to coordinator */
			else {
				rm = null;
				InquiryMsg inqMsg = new InquiryMsg();
				inqMsg.key = key;
				inqMsg.owner = pid;
				inqMsg.sender = id;
				byte[] msgByte = SimpleDynamoApp.getMsgStream(inqMsg);
				sc = SimpleDynamoApp.sendSocket.get(pid);
				if (sc != null) {
					try {
						Log.i("log", "Send inquiry to coordinator: " + pid);
						sc.write(ByteBuffer.wrap(msgByte));
						synchronized (lock) {
							lock.wait(500);
						}
					} catch (ClosedChannelException e) {
						SimpleDynamoApp.sendSocket.put(pid, null);
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				if ( rm != null ) {
					MatrixCursor mc = new MatrixCursor(SCHEMA);
					String[] v = {rm.key, rm.value};
					mc.addRow(v);
					mc.setNotificationUri(getContext().getContentResolver(), uri);
					rm = null;
					return mc;
				}
				/*coordinator is dead, ask its first successor */
				else {
					/* I am the first successor */
					if ( succ[0] == id ) {
						tgetQ.put(key, 1);
						for ( int x=1; x<succ.length; x++ ) {
							Intent repIntent = new Intent(getContext().getApplicationContext(), SendService.class);
							repIntent.putExtra("key", key);
							repIntent.putExtra("action", 'g');
							repIntent.putExtra("sender", succ[x]);
							repIntent.putExtra("owner", pid);
							repIntent.putExtra("asker", id);
							repIntent.putExtra("type", SimpleDynamoApp.REP_MSG);
							getContext().getApplicationContext().startService(repIntent);
						}
						synchronized (lock) {
							lock.wait();
						}
						MatrixCursor mc = new MatrixCursor(SCHEMA);
						String[] v = {key, peerData.get(pid).get(key)};
						mc.addRow(v);
						mc.setNotificationUri(getContext().getContentResolver(), uri);
						return mc;
					}
					else {
						sc = SimpleDynamoApp.sendSocket.get(succ[0]);
						if (sc != null) {
							try {
								sc.write(ByteBuffer.wrap(msgByte));
							} catch (ClosedChannelException e) {
								SimpleDynamoApp.sendSocket.put(succ[0], null);
								e.printStackTrace();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						synchronized (lock) {
							lock.wait();
						}
						if ( rm != null ) {
							MatrixCursor mc = new MatrixCursor(SCHEMA);
							String[] v = {rm.key, rm.value};
							mc.addRow(v);
							mc.setNotificationUri(getContext().getContentResolver(), uri);
							rm = null;
							return mc;
						}
					}
				}
			}
		} catch (NoSuchAlgorithmException e) {
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
