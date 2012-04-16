package edu.buffalo.cse.cse486_586.simpledynamo;

import android.app.IntentService;
import android.content.Intent;

public class SendService extends IntentService {

	public SendService() {
		super("SendService");
	}
	
	@Override
	public void onDestroy() {
		
	}
	
	@Override
	protected void onHandleIntent (Intent intent) {

	}
}
