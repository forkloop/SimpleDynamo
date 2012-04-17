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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import android.app.IntentService;
import android.content.Intent;
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
					SimpleDynamoApp.nodeHash.put(i, SimpleDynamoApp.genHash(""+i));
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
							//XXX check this
							int pid = sc.socket().getPort()/2;
							Log.i("log", "Remote port is "+pid);
							SimpleDynamoApp.outSocket.put(pid, sc);
							SimpleDynamoApp.nodeMap.put(SimpleDynamoApp.genHash(""+pid), pid);
							SimpleDynamoApp.nodeHash.put(pid, SimpleDynamoApp.genHash(""+pid));
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
								
								//XXX what if coordinator is dead
								InsertMsg insMsg = (InsertMsg) msg;
								if ( insMsg.owner == myId ) {
									DynamoProvider.myTmp.put(insMsg.key, insMsg.value);
									DynamoProvider.putQ.put(insMsg.key, 1);
									/* ack. I am not dead */
									Intent ackIntent = new Intent(this, SendService.class);
									ackIntent.putExtra("key", insMsg.key);
									ackIntent.putExtra("value", insMsg.value);
									ackIntent.putExtra("sender", insMsg.sender);
									ackIntent.putExtra("type", SimpleDynamoApp.ACK_MSG);
									startService(ackIntent);
									/* replicate */
									Intent repIntent = new Intent(this, SendService.class);
									repIntent.putExtra("key", insMsg.key);
									repIntent.putExtra("value", insMsg.value);
									repIntent.putExtra("action", 'p');
									repIntent.putExtra("type", SimpleDynamoApp.REP_MSG);
									startService(repIntent);
								}
								else {
									Map<String, String>t = new HashMap<String, String>();
									t.put(insMsg.key, insMsg.value);
									DynamoProvider.peerTmp.put(insMsg.owner, t);
									DynamoProvider.tputQ.put(insMsg.key, 1);
									/* reply back. quorum */
									Intent ackIntent = new Intent(this, SendService.class);
									ackIntent.putExtra("key", insMsg.key);
									ackIntent.putExtra("sender", insMsg.sender);
									ackIntent.putExtra("type", SimpleDynamoApp.ACK_MSG);
									startService(ackIntent);
									/* replicate */
									Intent repIntent = new Intent(this, SendService.class);
									repIntent.putExtra("key", insMsg.key);
									repIntent.putExtra("value", insMsg.value);
									repIntent.putExtra("action", 'p');
									repIntent.putExtra("type", SimpleDynamoApp.REP_MSG);
									startService(repIntent);
								}
							}

							else if (msg_type.equals("edu.buffalo.cse.cse486_586.simpledynamo.InquiryMsg")) {
								
								InquiryMsg inqMsg = (InquiryMsg) msg;
								if ( inqMsg.owner == myId ) {
									DynamoProvider.getQ.put(inqMsg.key, 1);
								}
								else {
									DynamoProvider.tgetQ.put(inqMsg.key, 1);
								}
								/* ask for quora */
								int[] succ = SimpleDynamoApp.getSuccessor(inqMsg.owner);
								for ( int x=0; x<succ.length; x++ ) {
									Intent repIntent = new Intent(this, SendService.class);
									repIntent.putExtra("key", inqMsg.key);
									repIntent.putExtra("action", 'g');
									repIntent.putExtra("sender", succ[x]);
									repIntent.putExtra("owner", inqMsg.owner);
									repIntent.putExtra("type", SimpleDynamoApp.REP_MSG);
									repIntent.putExtra("asker", inqMsg.sender);
									startService(repIntent);
								}
							}
							
							else if (msg_type.equals("edu.buffalo.cse.cse486_586.simpledynamo.AckMsg")) {
								
								//XXX Reply the inquiry or insert 
								synchronized(DynamoProvider.lock) {
									DynamoProvider.rm = (AckMsg) msg;
									DynamoProvider.lock.notify();
								}
							}
							
							else if (msg_type.equals("edu.buffalo.cse.cse486_586.simpledynamo.ReplicateMsg")) {
								
								ReplicateMsg repMsg = (ReplicateMsg) msg;
								if ( repMsg.type == 'p' ) {
									Map<String, String> t = new HashMap<String, String>();
									t.put(repMsg.key, repMsg.value);
									DynamoProvider.peerTmp.put(repMsg.owner, t);
									/* reply quorum */
									Intent quoIntent = new Intent(this, SendService.class);
									quoIntent.putExtra("sender", repMsg.sender);
									quoIntent.putExtra("action", 'p');
									quoIntent.putExtra("key", repMsg.key);
									quoIntent.putExtra("owner", repMsg.owner);
									quoIntent.putExtra("type", SimpleDynamoApp.QUO_MSG);
									startService(quoIntent);
								}
								else {
									/* reply quorum */
									Intent quoIntent = new Intent(this, SendService.class);
									quoIntent.putExtra("sender", repMsg.sender);
									quoIntent.putExtra("action", 'g');
									quoIntent.putExtra("key", repMsg.key);
									quoIntent.putExtra("owner", repMsg.owner);
									quoIntent.putExtra("type", SimpleDynamoApp.QUO_MSG);
									quoIntent.putExtra("asker", repMsg.asker);
									startService(quoIntent);
								}
							}
							/* confirm the successor to insert the entry */
							else if (msg_type.equals("edu.buffalo.cse.cse486_586.simpledynamo.ConfirmMsg")) {
								
								ConfirmMsg conMsg = (ConfirmMsg) msg;
								DynamoProvider.peerData.get(conMsg.owner).put(conMsg.key, 
										DynamoProvider.peerTmp.get(conMsg.owner).get(conMsg.key));
								DynamoProvider.peerTmp.get(conMsg.owner).remove(conMsg.key);
							}
							
							else if (msg_type.equals("edu.buffalo.cse.cse486_586.simpledynamo.QuorumMsg")) {
								
								QuorumMsg quoMsg = (QuorumMsg) msg;
								if ( quoMsg.type == 'p' ) {
									if ( quoMsg.owner == myId ) {
										if ( DynamoProvider.putQ.get(quoMsg.key) != null ) {
											int n = DynamoProvider.putQ.get(quoMsg.key);
											if ( n+1 >= SimpleDynamoApp.W ) {
												DynamoProvider.myData.put(quoMsg.key, DynamoProvider.myTmp.get(quoMsg.key));
												DynamoProvider.myTmp.remove(quoMsg.key);
												DynamoProvider.putQ.remove(quoMsg.key);
												/* inform successor to store the data */
												int[] succ = SimpleDynamoApp.getSuccessor(myId);
												for ( int x=0; x<succ.length; x++) {
													Intent conIntent = new Intent(this, SendService.class);
													conIntent.putExtra("sender", succ[x]);
													conIntent.putExtra("key", quoMsg.key);
													conIntent.putExtra("type", SimpleDynamoApp.CON_MSG);
													startService(conIntent);
												}
											}
											else {
												DynamoProvider.putQ.put(quoMsg.key, n+1);
											}
										}
									}
									else {
										if ( DynamoProvider.tputQ.get(quoMsg.key) != null ) {
											int n = DynamoProvider.tputQ.get(quoMsg.key);
											if ( n+1 >= SimpleDynamoApp.W ) {
												DynamoProvider.peerData.get(quoMsg.owner).put(quoMsg.key, 
														DynamoProvider.peerTmp.get(quoMsg.owner).get(quoMsg.key));
												DynamoProvider.peerTmp.get(quoMsg.owner).remove(quoMsg.key);
												DynamoProvider.tputQ.remove(quoMsg.key);
												/* inform successor to store the data */
												int[] succ = SimpleDynamoApp.getSuccessor(quoMsg.owner);
												for ( int x=0; x<succ.length; x++) {
													if ( succ[x] != myId ) {
														Intent conIntent = new Intent(this, SendService.class);
														conIntent.putExtra("sender", succ[x]);
														conIntent.putExtra("key", quoMsg.key);
														conIntent.putExtra("type", SimpleDynamoApp.CON_MSG);
														startService(conIntent);
													}
												}
											}
											else {
												DynamoProvider.tputQ.put(quoMsg.key, n+1);
											}
										}
									}
								}
								/* inquiry */
								else {
									//XXX take care of ``sender'' and ``asker''
									if ( quoMsg.owner == myId ) {
										if ( DynamoProvider.getQ.get(quoMsg.key) != null ) {
											int n = DynamoProvider.getQ.get(quoMsg.key);
											if ( n+1 >= SimpleDynamoApp.R ) {
												DynamoProvider.getQ.remove(quoMsg.key);
												/* reply inquiry */
												if ( quoMsg.asker == myId ) {
													synchronized (DynamoProvider.lock) {
													//	DynamoProvider.flag = true;
														DynamoProvider.lock.notify();
													}
												}
												else {
													Intent ackIntent = new Intent(this, SendService.class);
													ackIntent.putExtra("key", quoMsg.key);
													ackIntent.putExtra("value", DynamoProvider.myData.get(quoMsg.key));
													ackIntent.putExtra("sender", quoMsg.asker);
													ackIntent.putExtra("type", SimpleDynamoApp.ACK_MSG);
													startService(ackIntent);
												}
											}
											else {
												DynamoProvider.getQ.put(quoMsg.key, n+1);
											}
										}
									}
									else {
										if ( DynamoProvider.tgetQ.get(quoMsg.key) != null ) {
											int n = DynamoProvider.tgetQ.get(quoMsg.key);
											if ( n+1 >= SimpleDynamoApp.R ) {
												DynamoProvider.tgetQ.remove(quoMsg.key);
												
												if ( quoMsg.asker == myId ) {
													synchronized (DynamoProvider.lock) {
														DynamoProvider.lock.notify();
													}
												}
												else {
												/* reply inquiry */
													Intent ackIntent = new Intent(this, SendService.class);
													ackIntent.putExtra("key", quoMsg.key);
													ackIntent.putExtra("sender", quoMsg.asker);
													ackIntent.putExtra("value", DynamoProvider.peerData.get(quoMsg.owner).get(quoMsg.key));
													ackIntent.putExtra("type", SimpleDynamoApp.ACK_MSG);
													startService(ackIntent);
												}
											}
											else {
												DynamoProvider.tgetQ.put(quoMsg.key, n+1);
											}
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
		
		Set<String> h = SimpleDynamoApp.nodeMap.keySet();
		SimpleDynamoApp.succId = new ArrayList<Integer>();
		for ( String s : h) {
			SimpleDynamoApp.succId.add(SimpleDynamoApp.nodeMap.get(s));
		}
	}
	
}
