package edu.buffalo.cse.cse486_586.simpledynamo;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class TestOne extends IntentService {

	private static final Uri TABLE_URI = Uri.parse("content://edu.buffalo.cse.cse486_586.simpledynamo.provider/msgs");
	
	public TestOne() {
		super("TestOne");
	}
	
	@Override
	public void onDestroy() {
		
	}
	
	@Override
	protected void onHandleIntent (Intent intent) {
		
		int testNum=10;
		int magic = intent.getIntExtra("magic", 1)*10;
		/**
		 * Insert
		 */
		for ( int i=0; i<testNum; i++ ) {
			try {
				Thread.sleep(3000);
				Log.i("log", "Inserting "+i);
				ContentValues inserted = new ContentValues();
				inserted.put("provider_key", ""+i);
				inserted.put("provider_value", "Put"+(i+magic));
				Uri uri = getApplicationContext().getContentResolver().insert(TABLE_URI, inserted);
				Log.i("log", "Inserting URI " + uri);
			}  catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		Log.i("log", "Stop....");
	}
	
	
	
}
