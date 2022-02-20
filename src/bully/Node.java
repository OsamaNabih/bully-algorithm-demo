package bully;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Node {
	Network mySocket;
	NodeState state;
	public  Map<Integer,Peer> peers = new ConcurrentHashMap<>();
	long pid;
	Peer coordinator;
	boolean isCoordinator = false;
	
	// RUNNING state variables
	AtomicLong lastAliveTime = new AtomicLong(0);
	Thread aliveTimeoutCheckerThread = null;
	Thread taskThread = null;
	int chunkMin = Consts.maxInt;
	
	// ELECTING state variables
	boolean isLargest = true;
	
	// PENDING VICTORY state variables
	AtomicLong lastPendingVictoryTime = new AtomicLong(0);
	Timer victoryTimer = null;
	
	// COORDINATING state variables
	List<Thread> aliveThreads = new ArrayList<>();
	List<Thread> taskThreads = new ArrayList<>();
	AtomicInteger expectedReplies = new AtomicInteger();
	int actualReplies = 0;
	boolean taskReplyTimeout = false;
	Timer taskDeadline = null;
	int min = Consts.maxInt;
	
	
	private long getCurrTimestamp() {
		return new Timestamp(System.currentTimeMillis()).getTime();
	}
	
	Node() {
		this.state = NodeState.INIT;
		this.pid = ProcessHandle.current().pid();
		System.out.println("------------------------------------------------------------");
		System.out.println("New process started with pid " + String.valueOf(pid));
		System.out.println("------------------------------------------------------------");
	}
	
	void start() {
		// Acquire a new socket
		try {
			mySocket = new Network();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.out.println("Terminating process");
			return;
		}
		Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
            	discoverPeers();
            	transitionToRunning();
        	}
        });
		thread.start();
		listen();
	}
	
	public void listen() {
		while(true) {
    		CustomPair pair = mySocket.receive();
    		if (pair == null)
    			continue;
    		System.out.println("Node " + this.pid + " received at " + getCurrTimestamp() + ", " + pair.msg.print());
    			
    		Message responseMsg = getMessageResponse(pair.msg);
    		// It will only be null in case of receiving an ELECTION msg
    		if (responseMsg != null)
    			mySocket.send(pair.socket, responseMsg);
    		updateState(pair.msg);
    	}
	}
	
	public void updateState(Message receivedMsg) {
		long senderPid = Long.valueOf(receivedMsg.getSenderPid());
		int senderPort = Integer.valueOf(receivedMsg.getSenderPort());
		if (!peers.containsKey(senderPort)) {
			addPeer(new Peer(senderPort, senderPid));
		}
		switch (receivedMsg.getType()) {
			case ALIVE: {
				this.lastAliveTime.set(getCurrTimestamp());
				break;
			}
			case ELECTION: {
//				if (senderPid > this.pid)
//					transitionToPendingVictory();
//				else
				if (senderPid < this.pid && state != NodeState.ELECTING) {
					exitCurrentState();
					System.out.println("Received ELECTION from node with lower pid: " + senderPid);
					transitionToElecting();
				}
				break;
			}
			case VICTORY: {
				coordinator = new Peer(senderPort, senderPid);
				this.isCoordinator = false;
				if (state == NodeState.RUNNING)
				exitCurrentState();
				transitionToRunning();
				break;
			}
			// We've received a task from the coordinator
			case TASK: {
				spawnFindMinThread(receivedMsg);
				
				break;
			}
			case TASK_REPLY: {
				// If we're no longer coordinating, result is meaningless, ignore it
				if (state != NodeState.COORDINATING)
					break;
				this.min = Math.min(min, Integer.valueOf(receivedMsg.getContent()));
				actualReplies += 1;
				if (actualReplies == expectedReplies.get() 
					|| actualReplies == peers.size()) {
					System.out.println("Task finished with minimum: " + String.valueOf(this.min));
				}
				else if (this.taskReplyTimeout) {
					System.out.println("Not all nodes responsed, task timed out");
					System.out.println("Minimum response received: " + String.valueOf(this.min));
				}
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
		long senderPid = Long.valueOf(receivedMsg.getSenderPid());
		switch (receivedMsg.getType()) {
			case ELECTION: {
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
			// Send an OK (ACK) to alert sender we received the message
			default: 
				Message sentMsg = new Message(MessageType.OK, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
				return sentMsg;
			}
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
		System.out.println("Discovered " + peers.size() + " peers");
	}
	
	Thread spawnDiscoveryThread(int targetPort, Message msg) {
		//System.out.println("Spawning thread for port " + targetPort);
		Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
        		Message receivedMsg = mySocket.sendAndReceive(Consts.ip, targetPort, msg, Consts.greetingTimeout);
    			if (receivedMsg == null) {
    				//System.out.println("Unable to connect to node at port " + targetPort);
    				return;
    			}
    			long senderPid = Long.valueOf(receivedMsg.getSenderPid());
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
		//System.out.println("++++++++++++++++++++++++++++++++++++++++++");
		this.lastAliveTime.set(getCurrTimestamp());
		Node node = this;
		aliveTimeoutCheckerThread = new Thread(new Runnable() {
            @Override
            public void run() {
            	try {
            		while(state == NodeState.RUNNING && !Thread.currentThread().isInterrupted()) {
            			long currTime = getCurrTimestamp();
            			Long last = node.lastAliveTime.get();
            			if ((currTime - last) > Consts.coordinatorDeadTimeout) {
            				System.out.println("COORDINATOR ALIVE TIMED OUT");
            				long highestPid = node.getHighestPid();
            				if (highestPid == node.pid) {
            					exitCurrentState();
            					System.out.println("This node has the new highest pid: " + node.pid + ", becoming new coordinator");
            					node.transitionToCoordinating();
            					return;
            				}
            				else {
            					exitCurrentState();
            					node.transitionToElecting();
            					return;
            				}
            			}
            			
            		}
            		//System.out.println("----------------------------------------------");
            	} catch (Exception e) {
            		//System.out.println("----------------------------------------------");
            		return;
            	}
            }
        });
		aliveTimeoutCheckerThread.start();
		//System.out.println("Started alive timeout checker thread");
	}
	
	void initElections() {
		System.out.println("Initiating elections");
		
		List<Peer> failedPeers = new ArrayList<Peer>();
		Node node = this;
		Message msg = new Message(MessageType.ELECTION, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
		for(Peer peer : peers.values()) {
			if (peer.getPid() < this.pid && peer.getPid() != 0)
				continue;
			Message receivedMsg = mySocket.sendAndReceive(Consts.ip, peer.getPort(), msg, Consts.electionTimeout);
			// If we receive a null response, then peer has failed
			if (receivedMsg == null) {
				System.out.println("Peer with pid: " + peer.getPid() + ", at port: " + peer.getPort() + " has failed");
				failedPeers.add(peer);
				continue;
			}
			// If we receive an OK response, it means peer is awaiting victory
			// or it has a lower PID than us
			if (receivedMsg.getType() == MessageType.OK)
				continue;
			long senderPid = Long.valueOf(receivedMsg.getSenderPid());
			if (senderPid > node.pid) {
				isLargest = false;
				//transitionToPendingVictory();
				break;
			}			
		}
		

		if (failedPeers.size() > 0) {
			for (Peer failedPeer : failedPeers)
				removePeer(failedPeer);
		}
		
		if (!this.isLargest) {
			exitCurrentState();
			transitionToPendingVictory();
			return;
		}
		exitCurrentState();
		System.out.println("This node with pid: " + this.pid + " won the elections");
		transitionToCoordinating();
			
	}
	
	/*
	 * Initially I wanted the election process to be concurrent to avoid delays due to awaiting timeouts
	 * However this proved to be of harm to the amount of messages sent, as it becomes harder to
	 * stop electing myself upon receiving an ANSWER from a higher node
	 * It also added redundant messages flooding the network due to threads not immediately
	 * seeing the changed state
	 * These redundant messages made the network unnecessarily start ELECTION process several times
	 * before reaching a steady state 
	 * So I opted for a sequential approach,
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
			exitCurrentState();
			transitionToCoordinating();
		}
	}
	*/
	
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
        			long senderPid = Long.valueOf(receivedMsg.getSenderPid());
        			if (senderPid > node.pid) {
        				isLargest = false;
        				exitCurrentState();
    					transitionToPendingVictory();
    				}
        		}
        		return;
            }
        }) ;
		electionThread.start();
		return electionThread;
	}
	
	
	Timer spawnVictoryTimeoutThread() {
		//System.out.println("Spawning victory timeout thread");
		Timer timer = new Timer();
		Node node = this;
		TimerTask victoryTimeout = new TimerTask() {
			@Override
			public void run() {
				/*
				 If we still haven't transitioned from pending victory
				 Then we didn't receive a victory in the designated timeout period
				 Go back to Electing state and start a new election
				 We need to check both the state and the timer difference
				 To avoid the case of changing states quickly 
				 To coordinating, then electing, then again to pending victory
				 Then this timer expires as it was spawned from the previous time we were in pending victory
				 */
				
				if (node.state == NodeState.PENDING_VICTORY && (node.getCurrTimestamp() - lastPendingVictoryTime.get()) > Consts.pendingVictoryTimeout) {
					System.out.println("Pending victory timed out, starting new election");
					exitCurrentState();
					node.transitionToElecting();
				}
					
			}
		};
		timer.schedule(victoryTimeout, Consts.victoryTimeout);
		return timer;
		
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
    				//System.out.println("Unable to connect to node at port " + targetPort);
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
	
	/*
	 * This function starts a thread for the peer at the target port
	 * This thread periodically sends ALIVE messages to that peer
	 * Informing them that the coordinator is still alive
	 * @targetPort: port to send the message to
	 * @msg: message to be sent
	 */
	Thread spawnAliveThread(int targetPort, Message msg) {
		Node node = this;
		Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
            	//System.out.println("Alive thread for peer at port: " + targetPort + " is now started");
            	while(state == NodeState.COORDINATING) { // Node is on longer coordinator, stop sending alive messages
            		//System.out.println("Sending alive to peer at port: " + targetPort);
            		Message responseMsg = mySocket.sendAndReceive(Consts.ip, targetPort, msg, Consts.aliveTimeout);
            		if (responseMsg == null) {
            			//System.out.println("No longer sending alive messages to peer at port: " + targetPort);
            			System.out.println("Peer at port " + targetPort + " has failed");
            			node.removePeer(targetPort);
            			return;
            		}
            		try {
            			//System.out.println("Alive thread for peer at port: " + targetPort + " is sleeping");
						Thread.sleep(Consts.aliveInterval);
					} catch (InterruptedException e) {
						//System.out.println("Alive thread for peer at port: " + targetPort + " was interrupted");
						return;
					}
            	}
                
            }
        }) ;
        t.start();
        return t;
	}
	
	/*
	 * This function starts an independent thread for each peer
	 * This is more accurate than doing them sequentially as we won't be delayed by timeouts from failing peers
	 * However this might produce a lot of overhead if the number of peers is large
	 * and the RATE OF FAILURE is small
	 * This tradeoff should be taken into consideration in a real environment
	 */
	void sendAlive() {
		System.out.println("Sending periodic ALIVE to all peers");
		Message msg = new Message(MessageType.ALIVE, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid));
		for (Peer peer : peers.values()) {
			Thread aliveThread = spawnAliveThread(peer.getPort(), msg);
            aliveThreads.add(aliveThread);
        }
	}
	
	void taskDeadlineTimer() {
		taskDeadline = new Timer();
		TimerTask taskTimeout = new TimerTask() {
			@Override
			public void run() {
				taskReplyTimeout = true;
			}
		};
		taskDeadline.schedule(taskTimeout, Consts.waitingForTaskResponseTimeout);
	}
	
	List<Integer> generateRandomIntArray(int N, int max) {
		List<Integer> randomInts = IntStream.generate(() -> new Random().nextInt(max)).limit(N).boxed().collect(Collectors.toList());
		
		return randomInts;
	}
	
	Thread spawnTaskThread(Peer targetPeer, List<Integer> nums, List<Peer> failedPeers) {
		String chunk = buildNumsString(nums);
		Message msg = new Message(MessageType.TASK, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(this.pid), chunk);
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				
				//System.out.println("Sending chunk: " + chunk + " to peer: " + targetPeer.getPort());
				Message responseMsg = mySocket.sendAndReceive(Consts.ip, targetPeer.getPort(), msg, Consts.taskTimeout);
				// Don't need to remove the failed peer as the alive thread handles that
				// So we just return
				if (responseMsg == null) {
					//System.out.println("Peer " + targetPeer.getPort() + " has failed");
					failedPeers.add(targetPeer);
					return;
				}
				// If we receive a reply, then the task was sent successfully
				// We increment our count of expected replies
				expectedReplies.getAndIncrement();
			}
		});
		thread.start();
		return thread;
	}
	
	String buildNumsString(List<Integer> nums) {
		StringBuilder str = new StringBuilder("");
		for(int i = 0; i < nums.size(); i++) {
			str.append(String.valueOf(nums.get(i)));
			if (i != nums.size() - 1)
				str.append(",");
		}
		return str.toString();
	}
	
	void spawnTaskCoordinatorThread() {
		System.out.println("Sending task to peers");
		List<Peer> failedPeers = new ArrayList<Peer>();
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				// Give the alive thread a chance to detect failing nodes
				try {
					Thread.sleep((long)(Consts.aliveTimeout*1.5));
				} catch (InterruptedException e1) {
					
				}
				List<Integer> randomNums = generateRandomIntArray(Consts.randomArrSize, Consts.maxInt);
				//System.out.println("Generated random ints: " + randomNums.toString());
				int peerIdx = 0;
				int peerCount = peers.size();
				int chunkSize = Math.max((int)Math.ceil(1.0*randomNums.size() / peerCount), 1);
				//AtomicInteger min = new AtomicInteger(Consts.maxInt);
				for(Peer peer : peers.values()) {
					int startIdx = peerIdx * chunkSize;
					int endIdx = Math.min((peerIdx + 1) * chunkSize, randomNums.size()); // Exclusive
					//System.out.println("Peer" + peerCount + " start: " + startIdx + ", end: " + endIdx);
					if (startIdx >= randomNums.size()) // more processes than nums
						break;
					List<Integer> chunk = randomNums.subList(startIdx, endIdx);
					Thread taskThread = spawnTaskThread(peer, chunk, failedPeers);
					taskThreads.add(taskThread);
					peerIdx += 1;
				}
				for(Thread thread : taskThreads) {
					try {
						thread.join();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						//e.printStackTrace();
					}
				}
				
				// Once we're done, remove all failed peers
				// Note we don't remove them in the threads to avoid Concurrent Modification Exception
				// Because a thread is altering our peers map as we are iterating through it
				// in the coordinator thread
				for(Peer peer : failedPeers) {
					removePeer(peer);
				}
				// Any task reply sent after this time is ignored
				// The answer is considered the minimum response we received till that deadline
				taskDeadlineTimer();
				if (state != NodeState.COORDINATING)
					return;
				//System.out.println("Minimum is: " + min.get());
			}
		});
		thread.start();
	}
	
	void startTask() {
		// Spawn a new thread to handle sending so as not to block our receiver loop
		spawnTaskCoordinatorThread(); 
	}
	
	void spawnFindMinThread(Message receivedMsg) {
		this.chunkMin = Consts.maxInt;
		// This sleep is to give the user running the simulator a chance to kill nodes after task was sent
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Thread findMinThread = new Thread(new Runnable() {
			@Override
			public void run() {
				String numsArrayString = receivedMsg.getContent();
				// Remove lading '[' and trailing ']'
				//String numsArray = numsArrayString.substring(1, numsArrayString.length() - 1);
				//System.out.println("Received chunk: " + numsArrayString);
				String[] nums = numsArrayString.split(",");
				for(String s : nums) {
					int num = Integer.parseInt(s);
					chunkMin = Math.min(num, chunkMin);
				}
				
				// If state was changed, don't bother sending it
				// Even if it were sent, the receiver would ignore it
				// But it's better to pre-empt it to avoid loading the network
				if (state != NodeState.RUNNING || Thread.currentThread().isInterrupted())
					return;
				Message msg = new Message(MessageType.TASK_REPLY, mySocket.getReceiverSocket().getLocalPort(), String.valueOf(pid), String.valueOf(chunkMin));
				mySocket.sendAndReceive(Consts.ip, coordinator.port, msg, Consts.taskTimeout);
			}
		});
		findMinThread.start();
	}
	
	//---------------------------------------State transitions----------------------------------
	
	void transitionToRunning() {
		//System.out.println("Transitioning to state: RUNNING");
		state = NodeState.RUNNING;
		spawnAliveTimeoutCheckerThread();
	}
	
	void exitRunning() {
		aliveTimeoutCheckerThread.interrupt();
		if (taskThread != null)
			taskThread.interrupt();
		//System.out.println("-------------------------------");
	}
	
	void transitionToElecting() {
		//System.out.println("Transitioning to state: ELECTING");
		state = NodeState.ELECTING;
		this.isLargest = true;
		initElections();
	}
	
	void exitElecting() {
		return;
	}
	
	
	void transitionToPendingVictory() {
		//System.out.println("Transitioning to state: PENDING VICTORY");
		System.out.println("Process is now awaiting victory message");
		lastPendingVictoryTime.set(getCurrTimestamp());
		this.victoryTimer = spawnVictoryTimeoutThread();
	}
	
	void exitPendingVictory() {
		this.victoryTimer.cancel();
	}
	
	
	void transitionToCoordinating() {
		System.out.println("Becoming the COORDINATOR");
		this.isCoordinator = true;
		this.min = Consts.maxInt;
		state = NodeState.COORDINATING;
		taskReplyTimeout = false;
		taskDeadline = null;
		expectedReplies.set(0);
		actualReplies = 0;
		if (peers.size() > 0) {
			broadcastVictory();
			sendAlive();
			startTask();
		}
		
	}
	
	void exitCoordinating() {
		this.isCoordinator = false;
		for(Thread aliveThread : aliveThreads)
			aliveThread.interrupt();
		for(Thread taskThread : taskThreads)
			taskThread.interrupt();
		if (this.taskDeadline != null)
			this.taskDeadline.cancel();
	}
	
	void exitCurrentState() {
		switch(this.state) {
			case INIT: {
				break;
			}
			case RUNNING: {
				exitRunning();
				break;
			}
			case ELECTING: {
				exitElecting();
				break;
			}
			case PENDING_VICTORY: {
				exitPendingVictory();
				break;
			}
			case COORDINATING: {
				exitCoordinating();
				break;
			}
		}
		return;
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
		//System.out.println("Adding peer at port: " + peer.getPort());
		peers.put(peer.getPort(), peer);
	}
	
	void removePeer(int port) {
		peers.remove(port);
	}
	
	void removePeer(Peer peer) {
		peers.remove(peer.getPort());
	}
	
	/*
	 * We could also add a HEAP structure of the PIDs to get the highest pid in O(1)
	 * Space vs Time tradeoff
	 */
	long getHighestPid() {
		long highestPid = 0;
		for(Peer peer : peers.values()) {
			highestPid = Math.max(highestPid, peer.getPid());
		}
		return highestPid;
	}
	
}
