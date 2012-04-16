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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

/**
 * @author forkloop
 *
 */
public class ListenService extends IntentService {

	private ServerSocket inSocket;
	private ServerSocketChannel channel;
	private int myId;
	

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
		
		myId = SimpleDynamoApp.myId;
		
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
							ByteArrayInputStream bis = new ByteArrayInputStream(bt);
							ObjectInputStream ois = new ObjectInputStream(bis);
							msg = ois.readObject();
							String msg_type = msg.getClass().getName();
							Log.i("log", "Receive a " + msg_type);
							
							
							if (msg_type.equals("edu.buffalo.cse.cse486_586.simpledynamo.InsertMsg")) {
								
								InsertMsg insMsg = (InsertMsg) msg;
								if ( insMsg.owner == myId ) {
									DynamoProvider.myTmp.put(insMsg.key, insMsg.value);
									DynamoProvider.putQ.put(insMsg.key, 1);
									//XXX ack that I am not dead
									
									//XXX ask for quorum
								}
								else {
									Map<String, String>t = new HashMap<String, String>();
									t.put(insMsg.key, insMsg.value);
									DynamoProvider.peerTmp.put(insMsg.owner, t);
									//XXX reply ack
								}
							}

							else if (msg_type.equals("edu.buffalo.cse.cse486_586.simpledynamo.InquiryMsg")) {
								
								InquiryMsg inqMsg = (InquiryMsg) msg;
								if ( inqMsg.owner == myId ) {
									DynamoProvider.getQ.put(inqMsg.key, 1);
									//XXX ask for quorum
								}
								else {
									//XXX what if the coordinator is dead?
								}
							}
							
							else if (msg_type.equals("edu.buffalo.cse.cse486_586.simpledynamo.ReplyMsg")) {
								
								Log.i("log", "RECEIVE a reply message: " + ((ReplyMsg)msg).key + " : " + ((ReplyMsg)msg).value);
								synchronized(DynamoProvider.lock) {
									DynamoProvider.rm = (ReplyMsg) msg;
									DynamoProvider.lock.notify();
								}
							}
							
							else if (msg_type.equals("edu.buffalo.cse.cse486_586.simpledynamo.AckMsg")) {
								
								// The coordinator is not dead yet
								synchronized(DynamoProvider.lock) {
									DynamoProvider.flag = true;
								}
							}
							
							else if (msg_type.equals("edu.buffalo.cse.cse486_586.simpledynamo.QuorumMsg")) {
								
								QuorumMsg quoMsg = (QuorumMsg) msg;
								if ( quoMsg.type == 'p' ) {
									if ( DynamoProvider.putQ.get(quoMsg.key) != null ) {
										int n = DynamoProvider.putQ.get(quoMsg.key);
										if ( n+1 >= SimpleDynamoApp.W ) {
											DynamoProvider.myData.put(quoMsg.key, DynamoProvider.myTmp.get(quoMsg.key));
											DynamoProvider.myTmp.remove(quoMsg.key);
											DynamoProvider.putQ.remove(quoMsg.key);
											//XXX
										}
										else {
											DynamoProvider.putQ.put(quoMsg.key, n+1);
										}
									}
								}
								else {
									if ( DynamoProvider.getQ.get(quoMsg.key) != null ) {
										int n = DynamoProvider.getQ.get(quoMsg.key);
										if ( n+1 >= SimpleDynamoApp.R ) {
											DynamoProvider.getQ.remove(quoMsg.key);
											//XXX
										}
										else {
											DynamoProvider.getQ.put(quoMsg.key, n+1);
										}
									}
								}
							}
						}
						iter.remove();
					}
				}
			}
		} catch(IOException e){
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}		
	}
	
	
	/**
	 * Update the Dynamo when node joins or leaves
	 */
	private void update() {
		
	}
	
}
