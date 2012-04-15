/**
 * 
 */
package edu.buffalo.cse.cse486_586.simpledynamo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * @author forkloop
 *
 */
public class ListenService extends IntentService {

	private ServerSocket inSocket;
	private ServerSocketChannel channel;


	public ListenService() {
		super("ListenService");
	}

	
	@Override
	public void onDestroy() {
		try{
			inSocket.close();
		} catch(IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		try {
			SimpleDynamoApp.selector = Selector.open();
			Log.i("log", "Open the selector successfully!");
			for (int i=5554; i<SimpleDynamoApp.emulatorNum; i+=2) {
				if ( i != SimpleDynamoApp.myId ) {
					SocketChannel sc = SocketChannel.open(new InetSocketAddress("10.0.2.2", i*2));
					sc.configureBlocking(false);
					sc.register(SimpleDynamoApp.selector, (SelectionKey.OP_READ));
					SimpleDynamoApp.nodeMap.put(SimpleDynamoApp.genHash(""+i), i);
					Log.i("log", "Connecting to " + i);
				}
			}
			update();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		
		
		
		try{
			int port = intent.getIntExtra("myPort", 10000);
			channel = ServerSocketChannel.open();
			channel.configureBlocking(false);			
			inSocket = channel.socket();
			inSocket.bind( new InetSocketAddress("10.0.2.15", port) );
			Log.i("log", "Binding successfully at " + port);
			channel.register(SimpleDynamoApp.selector, SelectionKey.OP_ACCEPT);
			
			while (true) {
				
				int num = SimpleDynamoApp.selector.select();	
				if (num > 0) {
					Iterator<SelectionKey> iter = SimpleDynamoApp.selector.selectedKeys().iterator();
					while (iter.hasNext()) {
						SelectionKey key = iter.next();
						
						/* new connection */
						if ( (key.readyOps() & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT ) {
							Log.i("log", "$$$NEW CONNECTION$$$");
							ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
							SocketChannel sc = ssc.accept();
							sc.configureBlocking(false);
							sc.register(SimpleDynamoApp.selector, (SelectionKey.OP_READ));
							//XXX
							int pid = sc.socket().getPort()/2;
							Log.i("log", "Remote port is "+pid);
							SimpleDynamoApp.outSocket.put(pid, sc);
							SimpleDynamoApp.nodeMap.put(SimpleDynamoApp.genHash(""+pid), pid);
							Log.i("log", "# of connection: " + SimpleDynamoApp.outSocket.size());
							update();
						}
						
						/* new message */
						else if ( (key.readyOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ ) {
							Log.i("log", "@@@NEW MSG@@@");
							Object msg = null;
							SocketChannel sc = (SocketChannel) key.channel();
							ByteBuffer bb = ByteBuffer.allocate(1000);	// Hope this is enough...
							sc.read(bb);
							byte[] bt = bb.array();							
							try {
								ByteArrayInputStream bis = new ByteArrayInputStream(bt);
								ObjectInputStream ois = new ObjectInputStream(bis);
								msg = ois.readObject();
								String msg_type = msg.getClass().getName();
								Log.i("log", "Receive a " + msg_type);
								
								
								if (msg_type.equals("edu.buffalo.cse.cse486_586.simpledht.InsertMsg")) {
									
									InsertMsg insMsg = (InsertMsg) msg;
									if ( checkRange(insMsg.keyHash) ) {
										ContentValues inserted = new ContentValues();
										inserted.put("provider_key", insMsg.key);
										inserted.put("provider_value", insMsg.value);
										Uri uri = getApplicationContext().getContentResolver().insert(TABLE_URI, inserted);
										Log.i("log", "Inserting a new message: " + uri.toString());
										//FIXME Send back a msg
									}
									else {
										Intent sendIntent = new Intent(this, SendService.class);
										sendIntent.putExtra("key", insMsg.key);
										sendIntent.putExtra("value", insMsg.value);
										sendIntent.putExtra("keyHash", insMsg.keyHash);
										sendIntent.putExtra("type", 1);
										startService(sendIntent);
									}
								}

								else if (msg_type.equals("edu.buffalo.cse.cse486_586.simpledht.InquiryMsg")) {
									
									InquiryMsg inqMsg = (InquiryMsg) msg;
									if ( checkRange(inqMsg.keyHash) ) {
										//FIXME OR query the contentprovider and sendback via UDP
										Cursor c = getApplicationContext().getContentResolver().query(TABLE_URI, 
												null, inqMsg.key, null, null);
										c.moveToFirst();
										Intent repIntent = new Intent(this, SendService.class);
										repIntent.putExtra("key", c.getString(0));
										repIntent.putExtra("value", c.getString(1));
										repIntent.putExtra("sender", inqMsg.sender);
										repIntent.putExtra("type", 3);
										startService(repIntent);
									}
									else {
										Intent inqIntent = new Intent(this, SendService.class);
										inqIntent.putExtra("type", 2);
										inqIntent.putExtra("key", inqMsg.key);
										inqIntent.putExtra("keyHash", inqMsg.keyHash);
										inqIntent.putExtra("sender", inqMsg.sender);
										startService(inqIntent);
									}
								}
								
								else if ( msg_type.equals("edu.buffalo.cse.cse486_586.simpledht.ReplyMsg") ) {
									Log.i("log", "RECEIVE a reply message: " + ((ReplyMsg)msg).key + " : " + ((ReplyMsg)msg).value);
									synchronized(DHTProvider.lock) {
										DHTProvider.rm = (ReplyMsg) msg;
										DHTProvider.lock.notify();
									}
								}
							} catch (ClassNotFoundException e) {
								e.printStackTrace();
							}
						}
						iter.remove();
					}
				}
			}
		} catch(IOException e){
			e.printStackTrace();
		}		

	}
	
	
	/**
	 * Update the Dynamo when node joins or leaves
	 */
	private void update() {
		
	}
	
}
