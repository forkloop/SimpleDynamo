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
		sc = SimpleDynamoApp.sendSocket.get(intent.getIntExtra("sender", -1));
		if ( sc != null ) {
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
				
			case SimpleDynamoApp.JOIN_MSG:
				JoinMsg joinMsg = new JoinMsg();
				joinMsg.sender = SimpleDynamoApp.myId;
				msgByte = SimpleDynamoApp.getMsgStream(joinMsg);
				break;
				
			case SimpleDynamoApp.REC_MSG:
				RecoveryMsg recMsg = new RecoveryMsg();
				recMsg.originalMsg = null;
				recMsg.replicateMsg = null;
				int to = intent.getIntExtra("sender", -1);
				if ( isPredecessor(to) ) {
					recMsg.originalMsg = DynamoProvider.peerData.get(to);
				}
				if ( isSuccessor(to) ) {
					recMsg.replicateMsg = DynamoProvider.myData;
				}
				msgByte = SimpleDynamoApp.getMsgStream(recMsg);
				break;
				
			default:
				Log.i("log", "**********************I think I am in trouble now...");
			}
			try {
	//			sc = SimpleDynamoApp.sendSocket.get(intent.getIntExtra("sender", -1));
				Log.i("log", "Write to " + intent.getIntExtra("sender", -1) + " MSG: " + type);
				sc.write(ByteBuffer.wrap(msgByte));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	private boolean isPredecessor(int id) {
		
		int N = SimpleDynamoApp.emulatorNum;
		int idx = SimpleDynamoApp.succId.indexOf(SimpleDynamoApp.myId);
		for (int x=1; x<SimpleDynamoApp.repNum; x++) {
			if ( SimpleDynamoApp.succId.get((idx-x+N)%N) == id ) {
				return true;
			}
		}
		return false;
	}
	
	
	private boolean isSuccessor(int id) {
		
		int N = SimpleDynamoApp.emulatorNum;
		int idx = SimpleDynamoApp.succId.indexOf(SimpleDynamoApp.myId);
		for (int x=1; x<SimpleDynamoApp.repNum; x++) {
			if ( SimpleDynamoApp.succId.get((idx+x)%N) == id ) {
				return true;
			}
		}
		return false;

	}
}
