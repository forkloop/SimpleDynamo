package edu.buffalo.cse.cse486_586.simpledynamo;

import java.nio.channels.SocketChannel;

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

		int type = intent.getIntExtra("type", -1);
		byte[] msgByte = null;
		SocketChannel sc = null;
		
		switch (type) {

		}
	}
	
	
	
	
}
