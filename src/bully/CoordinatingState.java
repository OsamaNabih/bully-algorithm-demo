package bully;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CoordinatingState extends State {
	List<Thread> aliveThreads = new ArrayList<>();
	List<Thread> taskThreads = new ArrayList<>();
	AtomicInteger expectedReplies = new AtomicInteger();
	int actualReplies = 0;
	boolean taskReplyTimeout = false;
	Timer taskDeadline = null;
	int min = Consts.maxInt;
	
	public CoordinatingState(Node node) {
		super(node);
		this.state = NodeState.COORDINATING;
		System.out.println("Becoming the COORDINATOR");
		node.isCoordinator = true;
		this.min = Consts.maxInt;
		state = NodeState.COORDINATING;
		taskReplyTimeout = false;
		taskDeadline = null;
		expectedReplies.set(0);
		actualReplies = 0;
		if (node.peers.size() > 0) {
			broadcastVictory();
			sendAlive();
			startTask();
		}
	}
	
	
	@Override
	void exitState() {
		// TODO Auto-generated method stub
		node.isCoordinator = false;
		for(Thread aliveThread : aliveThreads)
			aliveThread.interrupt();
		for(Thread taskThread : taskThreads)
			taskThread.interrupt();
		if (this.taskDeadline != null)
			this.taskDeadline.cancel();
	}

	Thread spawnVictoryThread(int targetPort, Message msg) {
		Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
            	if (!node.isCoordinator)
            		return;
        		Message receivedMsg = node.mySocket.sendAndReceive(Consts.ip, targetPort, msg, Consts.victoryTimeout);
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
		Message msg = new Message(MessageType.VICTORY, node.mySocket.getReceiverSocket().getLocalPort(), String.valueOf(node.pid));
		for(Peer peer : node.peers.values()) {
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
					node.removePeer(peer);
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
		Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
            	while(node.state.state == NodeState.COORDINATING) { // Node is on longer coordinator, stop sending alive messages
            		Message responseMsg = node.mySocket.sendAndReceive(Consts.ip, targetPort, msg, Consts.aliveTimeout);
            		if (responseMsg == null) {
            			System.out.println("Peer at port " + targetPort + " has failed");
            			node.removePeer(targetPort);
            			return;
            		}
            		try {
						Thread.sleep(Consts.aliveInterval);
					} catch (InterruptedException e) {
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
		Message msg = new Message(MessageType.ALIVE, node.mySocket.getReceiverSocket().getLocalPort(), String.valueOf(node.pid));
		for (Peer peer : node.peers.values()) {
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
		Message msg = new Message(MessageType.TASK, node.mySocket.getReceiverSocket().getLocalPort(), String.valueOf(node.pid), chunk);
		Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				
				Message responseMsg = node.mySocket.sendAndReceive(Consts.ip, targetPeer.getPort(), msg, Consts.taskTimeout);
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
				int peerIdx = 0;
				int peerCount = node.peers.size();
				int chunkSize = Math.max((int)Math.ceil(1.0*randomNums.size() / peerCount), 1);
				for(Peer peer : node.peers.values()) {
					int startIdx = peerIdx * chunkSize;
					int endIdx = Math.min((peerIdx + 1) * chunkSize, randomNums.size()); // Exclusive
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
					node.removePeer(peer);
				}
				// Any task reply sent after this time is ignored
				// The answer is considered the minimum response we received till that deadline
				taskDeadlineTimer();
				if (state != NodeState.COORDINATING)
					return;
			}
		});
		thread.start();
	}
	
	void startTask() {
		// Spawn a new thread to handle sending so as not to block our receiver loop
		spawnTaskCoordinatorThread(); 
	}


	public List<Thread> getAliveThreads() {
		return aliveThreads;
	}


	public void setAliveThreads(List<Thread> aliveThreads) {
		this.aliveThreads = aliveThreads;
	}


	public List<Thread> getTaskThreads() {
		return taskThreads;
	}


	public void setTaskThreads(List<Thread> taskThreads) {
		this.taskThreads = taskThreads;
	}


	public AtomicInteger getExpectedReplies() {
		return expectedReplies;
	}


	public void setExpectedReplies(AtomicInteger expectedReplies) {
		this.expectedReplies = expectedReplies;
	}


	public int getActualReplies() {
		return actualReplies;
	}


	public void setActualReplies(int actualReplies) {
		this.actualReplies = actualReplies;
	}


	public boolean isTaskReplyTimeout() {
		return taskReplyTimeout;
	}


	public void setTaskReplyTimeout(boolean taskReplyTimeout) {
		this.taskReplyTimeout = taskReplyTimeout;
	}


	public Timer getTaskDeadline() {
		return taskDeadline;
	}


	public void setTaskDeadline(Timer taskDeadline) {
		this.taskDeadline = taskDeadline;
	}


	public int getMin() {
		return min;
	}


	public void setMin(int min) {
		this.min = min;
	}
	

}
