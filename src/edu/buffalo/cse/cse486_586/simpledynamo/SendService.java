package edu.buffalo.cse.cse486_586.simpledynamo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

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
		case SimpleDynamoApp.INS_MSG:
			InsertMsg insMsg = new InsertMsg();
			insMsg.key = intent.getStringExtra("key");
			insMsg.value = intent.getStringExtra("value");
			insMsg.owner = intent.getIntExtra("owner", -1);
			insMsg.sender = SimpleDynamoApp.myId;
			msgByte = SimpleDynamoApp.getMsgStream(insMsg);
			break;
		
		case SimpleDynamoApp.INQ_MSG:
			InquiryMsg inqMsg = new InquiryMsg();
			inqMsg.key = intent.getStringExtra("key");
			inqMsg.owner = intent.getIntExtra("owner", -1);
			inqMsg.sender = SimpleDynamoApp.myId;
			msgByte = SimpleDynamoApp.getMsgStream(inqMsg);
			break;
			
		case SimpleDynamoApp.QUO_MSG:
			QuorumMsg quoMsg = new QuorumMsg();
			quoMsg.key = intent.getStringExtra("key");
			quoMsg.type = intent.getCharExtra("action", 'g');
			quoMsg.sender = SimpleDynamoApp.myId;
			quoMsg.owner = intent.getIntExtra("owner", -1);
			quoMsg.asker = intent.getIntExtra("asker", -1);
			msgByte = SimpleDynamoApp.getMsgStream(quoMsg);
			break;
		
		/* confirm to insert the entry */
		case SimpleDynamoApp.CON_MSG:
			ConfirmMsg conMsg = new ConfirmMsg();
			conMsg.key = intent.getStringExtra("key");
			conMsg.owner = intent.getIntExtra("owner", -1);
			msgByte = SimpleDynamoApp.getMsgStream(conMsg);
			break;
		
		/* reply inquiry or just confirm that I am alive */	
		case SimpleDynamoApp.ACK_MSG:
			AckMsg ackMsg = new AckMsg();
			ackMsg.key = intent.getStringExtra("key");
			ackMsg.value = intent.getStringExtra("value");
			msgByte = SimpleDynamoApp.getMsgStream(ackMsg);
			break;
		
		/* replicate the entry */
		case SimpleDynamoApp.REP_MSG:
			ReplicateMsg repMsg = new ReplicateMsg();
			repMsg.key = intent.getStringExtra("key");
			repMsg.value = intent.getStringExtra("value");
			repMsg.owner = intent.getIntExtra("owner", -1);
			repMsg.sender = SimpleDynamoApp.myId;
			repMsg.type = intent.getCharExtra("action", 'g');
			/* used when inquiry */
			repMsg.asker = intent.getIntExtra("asker", -1);
			msgByte = SimpleDynamoApp.getMsgStream(repMsg);
			break;
			
		case SimpleDynamoApp.CONN_MSG:
			ConnectMsg connMsg = new ConnectMsg();
			connMsg.sender = SimpleDynamoApp.myId;
			msgByte = SimpleDynamoApp.getMsgStream(connMsg);
			break;
			
		case SimpleDynamoApp.REC_MSG:
			break;
			
		default:
			Log.i("log", "**********************I think I am in trouble now...");
		}
		try {
			sc = SimpleDynamoApp.sendSocket.get(intent.getIntExtra("sender", -1));
			Log.i("log", "Write to " + intent.getIntExtra("sender", -1) + " MSG: " + type);
			sc.write(ByteBuffer.wrap(msgByte));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	
	
}
