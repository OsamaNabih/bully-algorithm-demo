package bully;

import java.net.Socket;
import java.util.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class Node {
	Network mySocket;
	NodeState state;
	public  Map<Integer,Peer> peers = new ConcurrentHashMap<>();
	long pid;
	int sentElectionsCount = 0;
	int receivedElectionsCount = 0;
	Peer coordinator;
	boolean isCoordinator = false;
	long victoryTimer;
	long lastAliveTime = 0;
	boolean finishedDiscovery = false;
	boolean spawnedTimeoutCheckerThread = false;
	boolean spawnedVictoryTimeoutThread = false;
	Thread aliveTimeoutCheckerThread = null;
	boolean isLargest = true;
	
	
	private long getCurrTimestamp() {
		return new Timestamp(System.currentTimeMillis()).getTime();
	}
	
	private String getTimeNow() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
	}
	
	Node() {
		transitionToInit();
		this.pid = ProcessHandle.current().pid();
		System.out.println("------------------------------------------------------------");
		System.out.println("New process started with pid " + String.valueOf(pid));
		System.out.println("------------------------------------------------------------");
	}
	
	void start() {
		// Acquire a new socket
		mySocket = new Network();

		while(true) {
			if (state == NodeState.INIT) {
				spawnReceiverThread();
				discoverPeers();
				transitionToRunning();
			}
			if (state == NodeState.RUNNING) {
				//System.out.println("Running " + spawnedTimeoutCheckerThread);
//				if (spawnedTimeoutCheckerThread == false)
//					spawnAliveTimeoutCheckerThread();
			}
			if (state == NodeState.ELECTING) {
				//initElections();
			}
			if (state == NodeState.PENDING_VICTORY) {
//				if (spawnedVictoryTimeoutThread == false)
//					spawnVictoryTimeoutThread();
			}
			if (state == NodeState.COORDINATING) { // Periodically send ALIVE to all peers
//				sendAlive();
//				receiveMessages();
			}
		}
	}
	
	void spawnReceiverThread() {
		Node node = this;
		Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
            	while(true) {
            		CustomPair pair = mySocket.receive();
            		if (pair == null)
            			continue;
            		Message responseMsg = getMessageResponse(pair.msg);
            		// It will only be null in case of receiving an ELECTION msg
            		if (responseMsg != null)
            			mySocket.send(pair.socket, responseMsg);
            		updateState(pair.msg);
            	}
            }
        });
		System.out.println("Started receiver thread");
		thread.start();
	}
	
	public void updateState(Message receivedMsg) {
		long senderPid = Long.valueOf(receivedMsg.getContent());
		int senderPort = Integer.valueOf(receivedMsg.getSenderPort());
		if (!peers.containsKey(senderPort)) {
			addPeer(new Peer(senderPort, senderPid));
		}
		switch (receivedMsg.getType()) {
			case ALIVE: {
				this.lastAliveTime = getCurrTimestamp();
				break;
			}
			case ELECTION: {
//				if (senderPid > this.pid)
//					transitionToPendingVictory();
//				else
				if (senderPid < this.pid)
					transitionToElecting();
				break;
			}
			case VICTORY: {
				coordinator = new Peer(senderPort, senderPid);
				this.isCoordinator = false;
				transitionToRunning();
				break;
			}
			// OK and ANSWER cases will never happen as no node initiates sending them
			// GREETING case doesn't change state, we simply add a peer as we always do if we receive a message
			// from any peer that didn't exist previously
			default: 
				break;
			}
		return;
	}
	
	public Message getMessageResponse(Message receivedMsg) {
		long senderPid = Long.valueOf(receivedMsg.getContent());
		switch (receivedMsg.getType()) {
			case GREETING: {
				//System.out.println("Received GREETING, replying with OK");
				Message sentMsg = new Message(MessageType.OK, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
				return sentMsg;
			}
			case ALIVE: {
				//System.out.println("Received ALIVE, replying with OK");
				this.lastAliveTime = getCurrTimestamp();
				Message sentMsg = new Message(MessageType.OK, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
				return sentMsg;
			}
			case ELECTION: {
				//System.out.println("Received ELECTION, replying with ANSWER");
				// If we are pending victory or the sender has higher pid
				// Don't reply with ANSWER message
				if (state == NodeState.PENDING_VICTORY || senderPid > this.pid) {
					Message sentMsg = new Message(MessageType.OK, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
					return sentMsg;
				}
				else {
					Message sentMsg = new Message(MessageType.ANSWER, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
					return sentMsg;
				}
				
				
			}
			case VICTORY: {
				//System.out.println("Received VICTORY, replying with OK");
				Message sentMsg = new Message(MessageType.OK, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
				return sentMsg;
			}
			default: // OK and ANSWER cases will never happen as no node initiates sending them
				break;
			}
		return null;
	}
	
	public Message getAppropriateResponse(Message receivedMsg) {
		long senderPid = Long.valueOf(receivedMsg.getContent());
		int senderPort = Integer.valueOf(receivedMsg.getSenderPort());
		if (!peers.containsKey(senderPort)) {
			addPeer(new Peer(senderPort, senderPid));
		}
		System.out.println("Picking proper response for msg " + receivedMsg.print());
		switch (receivedMsg.getType()) {
			case GREETING: {
				System.out.println("Received GREETING, replying with OK");
				Message sentMsg = new Message(MessageType.OK, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
				//addPeer(new Peer(senderPort, senderPid));
				return sentMsg;
			}
			case ALIVE: {
	//			if (state == NodeState.RUNNING)
				System.out.println("Received ALIVE, replying with OK");
				this.lastAliveTime = getCurrTimestamp();
				Message sentMsg = new Message(MessageType.OK, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
				return sentMsg;
			}
			case ELECTION: {
				System.out.println("Received ELECTION, replying with ANSWER");
				Message sentMsg = new Message(MessageType.ANSWER, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
				if (senderPid > this.pid) {
					transitionToPendingVictory();
				}
				else {
					transitionToElecting();
				}
				return sentMsg;
			}
			
			case VICTORY: {
				coordinator = new Peer(senderPort, senderPid);
				this.isCoordinator = false;
				Message sentMsg = new Message(MessageType.OK, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
				return sentMsg;
			}
			case ANSWER: // Will never happen as no node initiates sending an ANSWER
				break;
			case OK: // Will never happen as no node initiates sending an OK
				break;
			default:
				break;
			}
		return null;
	}
	
	// This function iterates over all possibly occupied ports
	// If it receives an answer, it adds the peer at that port to our list of peers
	// After all threads are done, it sets the finishDiscovery flag to indicate we are ready in case of an election
	void discoverPeers() {
		System.out.println("Discovering peers");
		Message msg = new Message(MessageType.GREETING, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(pid));
		List<Thread> discoveryThreads = new ArrayList<>();
		for(int i = Consts.startingPort, j = 0; j < Consts.maxNumOfNodes; j++) {
			if (i == mySocket.getReceiverSocket().getLocalPort()) {
				i++;
				continue;
			}
				
			Thread thread = spawnDiscoveryThread(i, msg);
			discoveryThreads.add(thread);
			thread.start();
			i++;
		}
		waitAllThreads(discoveryThreads);
		this.finishedDiscovery = true;
		System.out.println("Discovered " + peers.size() + " peers");
	}
	
	Thread spawnDiscoveryThread(int targetPort, Message msg) {
		System.out.println("Spawning thread for port " + targetPort);
		Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
        		Message receivedMsg = mySocket.sendAndReceive(Consts.ip, targetPort, msg, Consts.greetingTimeout);
    			if (receivedMsg == null) {
    				System.out.println("Unable to connect to node at port " + targetPort);
    				return;
    			}
    			long senderPid = Long.valueOf(receivedMsg.getContent());
    			int senderPort = Integer.valueOf(receivedMsg.getSenderPort());
    			addPeer(new Peer(senderPort, senderPid));
        	}
        });
		return thread;
	}
	
	void waitAllThreads(List<Thread> discoveryThreads) {
		for(Thread thread : discoveryThreads)
			try {
				thread.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				continue;
			}
	}
	
	
	void spawnAliveTimeoutCheckerThread() {
		System.out.println("++++++++++++++++++++++++++++++++++++++++++");
		this.lastAliveTime = getCurrTimestamp();
		Node node = this;
		aliveTimeoutCheckerThread = new Thread(new Runnable() {
            @Override
            public void run() {
            	try {
            		while(state == NodeState.RUNNING) {
            			long currTime = getCurrTimestamp();
            			if ((currTime - node.lastAliveTime) > Consts.coordinatorDeadTimeout) {
            				System.out.println("Coordinator is down, timestamp: " + getCurrTimestamp() + " " + getTimeNow());
            				node.transitionToElecting();
            			}
            		}
            		System.out.println("----------------------------------------------");
            	} catch (Exception e) {
            		System.out.println("----------------------------------------------");
            		return;
            	}
            }
        });
		aliveTimeoutCheckerThread.start();
		//System.out.println("Started alive timeout checker thread");
	}
	
	void initElections() {
		System.out.println("Initiating elections");
		
		List<Peer> failedPeers = Collections.synchronizedList(new ArrayList<Peer>());
		List<Thread> electionThreads = new ArrayList<>();
		Node node = this;
		for(Peer peer : peers.values()) {
			if (peer.getPid() < this.pid && peer.getPid() != 0)
				continue;
			Thread electionThread = spawnElectionThread(node, failedPeers, peer);
			electionThreads.add(electionThread);
			
		}
		
		// Wait for all outgoing threads to finish
		for(Thread electionThread : electionThreads) {
			try {
				electionThread.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				continue;
				//e.printStackTrace();
			}
		}
		synchronized(failedPeers) {
			if (failedPeers.size() > 0) {
				Iterator<Peer> it = failedPeers.iterator();
				while(it.hasNext()) {
					Peer peer = it.next();
					removePeer(peer);
				}
			}
		}
		// If we received answers from all other nodes
		// and we didn't go to pending victory, then we are the coordinator
		if (node.state == NodeState.ELECTING && this.isLargest) {
			System.out.println("SURVIVED TILL THE END OF ELECTION");
			transitionToCoordinating();
		}
			
	}
	
	Thread spawnElectionThread(Node node, List<Peer> failedPeers, Peer targetPeer) {
		Message msg = new Message(MessageType.ELECTION, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
		Thread electionThread = new Thread(new Runnable() {
            @Override
            public void run() {
            	// If we transition past election state, don't send any more election messages
        		if(state == NodeState.ELECTING) { 
        			Message receivedMsg = mySocket.sendAndReceive(Consts.ip, targetPeer.getPort(), msg, Consts.electionTimeout);
        			// If we receive a null response, then peer has failed
        			if (receivedMsg == null) {
        				failedPeers.add(targetPeer);
        				return;
        			}
        			// If we receive an OK response, it means peer is awaiting victory
        			// or it has a lower PID than us
        			if (receivedMsg.getType() == MessageType.OK)
        				return;
        			long senderPid = Long.valueOf(receivedMsg.getContent());
        			if (senderPid > node.pid) {
        				isLargest = false;
    					transitionToPendingVictory();
    				}
        		}
        		return;
            }
        }) ;
		electionThread.start();
		return electionThread;
	}
	
	
	void spawnVictoryTimeoutThread() {
		Timer timer = new Timer();
		Node node = this;
		TimerTask victoryTimeout = new TimerTask() {
			@Override
			public void run() {
				// If we still haven't transitioned from pending victory
				// Then we didn't receive a victory in the designated timeout period
				// Go back to Electing state and start a new election
				// We need to check both the state and the timer difference
				// To avoid the case of changing states quickly 
				// To coordinating, then electing, then again to pending victory
				// Then this timer expires as it was spawned from the previous time we were in pending victory
				if (node.state == NodeState.PENDING_VICTORY && (node.getCurrTimestamp() - victoryTimer) > Consts.pendingVictoryTimeout) {
					System.out.println("Pending victory timed out, starting new election");
					node.transitionToElecting();
				}
					
			}
		};
		timer.schedule(victoryTimeout, Consts.victoryTimeout);
		System.out.println("Spawning victory timeout thread");
	}
	
	Thread spawnVictoryThread(int targetPort, Message msg) {
		Node node = this;
		Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
            	if (!node.isCoordinator)
            		return;
        		Message receivedMsg = mySocket.sendAndReceive(Consts.ip, targetPort, msg, Consts.victoryTimeout);
    			if (receivedMsg == null) {
    				System.out.println("Unable to connect to node at port " + targetPort);
    				return;
    			}
        	}
        });
		thread.start();
		return thread;
	}
	
	// It's important for the coordinator to have an up to date view of the other nodes
	// Because the coordinator distributes tasks over these nodes
	// So we need to account for peers failing in our requests
	// We keep a list of the nodes that didn't reply, and remove them from our peers
	void broadcastVictory() {
		System.out.println("Broadcasting victory");
		List<Peer> failedPeers = Collections.synchronizedList(new ArrayList<Peer>());
		List<Thread> victoryThreads = new ArrayList<>();
		Message msg = new Message(MessageType.VICTORY, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
		for(Peer peer : peers.values()) {
			Thread victoryThread = spawnVictoryThread(peer.getPort(), msg);
			victoryThreads.add(victoryThread);
		}
		for(Thread victoryThread : victoryThreads) {
			try {
				victoryThread.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				continue;
				//e.printStackTrace();
			}
		}
		synchronized(failedPeers) {
			if (failedPeers.size() > 0) {
				Iterator<Peer> it = failedPeers.iterator();
				while(it.hasNext()) {
					Peer peer = it.next();
					removePeer(peer);
				}
			}
		}
	}
	
	void spawnAliveThread(int targetPort, Message msg) {
		Node node = this;
		Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
            	//System.out.println("Alive thread for peer at port: " + targetPort + " is now started");
            	while(state == NodeState.COORDINATING) { // Node is on longer coordinator, stop sending alive messages
            		System.out.println("Sending alive to peer at port: " + targetPort);
            		Message responseMsg = mySocket.sendAndReceive(Consts.ip, targetPort, msg, Consts.aliveTimeout);
            		if (responseMsg == null) {
            			System.out.println("No longer sending alive messages to peer at port: " + targetPort);
            			node.removePeer(targetPort);
            			return;
            		}
            		try {
            			//System.out.println("Alive thread for peer at port: " + targetPort + " is sleeping");
						Thread.sleep(Consts.aliveInterval);
					} catch (InterruptedException e) {
						System.out.println("Something went wrong with alive thread for peer at port: " + targetPort);
						return;
					}
            	}
                
            }
        }) ;
        t.start();
	}
	
	void sendAlive() {
		Message msg = new Message(MessageType.ALIVE, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
		int timeout = Consts.aliveTimeout;
		Node node = this;
		for (Peer peer : peers.values()) {
            spawnAliveThread(peer.getPort(), msg);
            
        }
	}
	
	void transitionToInit() {
		System.out.println("Transitioning to state: INIT");
		state = NodeState.INIT;
		
	}
	
	void transitionToRunning() {
		System.out.println("Transitioning to state: RUNNING");
		state = NodeState.RUNNING;
		spawnedTimeoutCheckerThread = false;
		if (aliveTimeoutCheckerThread != null)
			aliveTimeoutCheckerThread.interrupt();
		spawnAliveTimeoutCheckerThread();
		spawnedTimeoutCheckerThread = true;
	}
	
	void transitionToElecting() {
		System.out.println("Transitioning to state: ELECTING");
		// Only initiate election process if we have discovered all possible peers
		while(!finishedDiscovery);
		state = NodeState.ELECTING;
		this.isLargest = true;
		sentElectionsCount = 0;
		receivedElectionsCount = 0;
		initElections();
	}
	
	
	void transitionToPendingVictory() {
		spawnedVictoryTimeoutThread = false;
		System.out.println("Transitioning to state: PENDING VICTORY");
		victoryTimer = getCurrTimestamp();
		spawnVictoryTimeoutThread();
		spawnedVictoryTimeoutThread = true;
	}
	
	
	void transitionToCoordinating() {
		System.out.println("Transitioning to state: COORDINATING");
		this.isCoordinator = true;
		state = NodeState.COORDINATING;
		broadcastVictory();
		sendAlive();
	}
	

	/*
	int getAppropriateTimeout(Message msg) {
		switch (msg.getType()) {
			case GREETING:{
				return Consts.greetingTimeout;
			}
			case ELECTION: {
				return Consts.electionTimeout;
			}
			case ALIVE: {
				return Consts.greetingTimeout;
			}
			case VICTORY: {
				return Consts.victoryTimeout;
			}
			default: {
				return Consts.greetingTimeout;
			}
		}
	}
	*/

	void addPeer(Peer peer) {
		System.out.println("Adding peer at port: " + peer.getPort());
		peers.put(peer.getPort(), peer);
	}
	
	void removePeer(int port) {
		peers.remove(port);
	}
	
	void removePeer(Peer peer) {
		peers.remove(peer.getPort());
	}
	
}
