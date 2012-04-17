package edu.buffalo.cse.cse486_586.simpledynamo;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class SimpleDynamoActivity extends Activity implements OnClickListener {
    /** Called when the activity is first created. */
	private static final Uri TABLE_URI = Uri.parse("content://edu.buffalo.cse.cse486_586.simpledynamo.provider/msgs");

	static int totalCount = 0;
	private RelativeLayout display;
	private Receiver recvHandler;
	public Resources res;
	public Drawable shape;

	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
                
        setContentView(R.layout.main);
        
        display = (RelativeLayout) findViewById(R.id.msg_display);
        
        Button testButton1 = (Button) findViewById(R.id.put1);
        testButton1.setOnClickListener(this);
        
        Button testButton2 = (Button) findViewById(R.id.put2);
        testButton2.setOnClickListener(this);
        
        Button testButton3 = (Button) findViewById(R.id.put3);
        testButton3.setOnClickListener(this);
        
        Button testButton4 = (Button) findViewById(R.id.get);
        testButton4.setOnClickListener(this);
        
        Button testButton5 = (Button) findViewById(R.id.dump);
        testButton5.setOnClickListener(this);

    }
    
	@Override
	public void onResume() {
		super.onResume();
		if(recvHandler == null) recvHandler = new Receiver();
		IntentFilter intentFilter = new IntentFilter("us.forkloop.sockettalk.RECV");
		registerReceiver(recvHandler, intentFilter);
	}
    
	@Override
	public void onPause() {
		super.onPause();
		if(recvHandler != null) unregisterReceiver(recvHandler);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceiver(recvHandler);
	}
	
	
	public void onClick(View v) {
		switch (v.getId()) {
		
		case R.id.put1:
			Log.i("log", "Test One...");
			Test1();
			break;
			
		case R.id.put2:
			Log.i("log", "Test Two...");
			Test2();
			break;
		
		case R.id.put3:
			Log.i("log", "Test Three...");
			Test3();
			break;
		
		case R.id.get:
			Log.i("log", "Test Four...");
			Test4();
			break;
			
		case R.id.dump:
			Log.i("log", "Test Five...");
			Test5();
			break;
		}	
	}

	private class Receiver extends BroadcastReceiver {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			if ( intent.getAction().equals("us.forkloop.sockettalk.RECV") ) {
					String msg = intent.getStringExtra("key") + " : " + intent.getStringExtra("value");
					displayMsg(msg);
			}
		}
	}

	/**
	 * Test1() to Test3() are just insert
	 */
	private void Test1() {
		
		Intent intent = new Intent(this, TestOne.class);
		intent.putExtra("magic", 1);
		startService(intent);
	}

	
	private void Test2() {
		
		Intent intent = new Intent(this, TestOne.class);
		intent.putExtra("magic", 2);
		startService(intent);
	}

	
	private void Test3() {
		
		Intent intent = new Intent(this, TestOne.class);
		intent.putExtra("magic", 3);
		startService(intent);
	}

	/**
	 * Query()
	 */
	private void Test4() {
		
	}

	/**
	 * Dump
	 */
	private void Test5() {
		
		String[] args = {"dump"};
		Cursor c = getApplicationContext().getContentResolver().query(TABLE_URI, null, null, args, null);
		if(c.getCount() > 0 ) {
			while(c.moveToNext()) {
				String msg = c.getString(0) + " : " + c.getString(1);
				displayMsg(msg);
			}
		}
	}

	
	public void displayMsg(String msg) {
		
		TextView tv = new TextView(this);
		tv.setText(msg);
		tv.setTextColor(Color.RED);
		tv.setId( ++totalCount );
	    RelativeLayout.LayoutParams layRule = 
	    		new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, 
	    				RelativeLayout.LayoutParams.WRAP_CONTENT);
	    layRule.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
	    layRule.setMargins(2, 3, 2, 3);
	    if (totalCount == 0) {
	    		layRule.addRule(RelativeLayout.ALIGN_PARENT_TOP);
	    }
	    else {
	    		layRule.addRule(RelativeLayout.BELOW, totalCount-1);
	    }
		display.addView(tv, layRule);
	}
}