package edu.buffalo.cse.cse486_586.simpledynamo;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

public class TestTwo extends IntentService {

	private static final Uri TABLE_URI = Uri.parse("content://edu.buffalo.cse.cse486_586.simpledynamo.provider/msgs");
	
	public TestTwo() {
		super("TestTwo");
	}
	
	@Override
	public void onDestroy() {
		
	}
	
	@Override
	protected void onHandleIntent (Intent intent) {
		
		int testNum = 10;
		
		/** 
		 * Inquiry 
		 * 
		 */
		for ( int i=0; i<testNum; i++ ) {
			try {
				Thread.sleep(3000);
				Log.i("log", "Inquirying "+i);
				Cursor c = getApplicationContext().getContentResolver().query(TABLE_URI, null, ""+i, null, null);
				Log.i("log", "Size of return query is "+c.getCount());
				c.moveToFirst();
				Log.i("log", "QUERY RESULT: " + c.getString(0) + " : " + c.getString(1));
				Intent disIntent = new Intent();
				disIntent.putExtra("key", c.getString(0));
				disIntent.putExtra("value", c.getString(1));
				disIntent.setAction("us.forkloop.sockettalk.RECV");
				sendBroadcast(disIntent);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		Log.i("log", "Stop....");
	}
	
	
	
}
