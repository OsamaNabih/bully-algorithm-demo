package bully;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class Node {
	Network mySocket;
	State state;
	public  Map<Integer,Peer> peers = new ConcurrentHashMap<>();
	long pid;
	Peer coordinator;
	boolean isCoordinator = false;
	AtomicLong lastAliveTime = new AtomicLong(0);
	
	/*
	 * Utility function to get current timestamp
	 */
	private long getCurrTimestamp() {
		return new Timestamp(System.currentTimeMillis()).getTime();
	}
	
	Node() {
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
		
		// Init state is responsible for discovering other nodes
		this.state = new InitState(this);
		
		// We start listen even before finishing our discovery, in order to hear other nodes ASAP
		listen();
	}
	/*
	 * Main function of the main thread
	 * Listen to all incoming messages infinitely
	 * Decide the proper response for each message and update Node state accordingly
	 */
	public void listen() {
		while(true) {
    		CustomPair pair = mySocket.receive();
    		if (pair == null) // Occurs on timeouts
    			continue;
    		
    		System.out.println("Node " + this.pid + " received at " + getCurrTimestamp() + ", " + pair.msg.print());	
    		Message responseMsg = getMessageResponse(pair.msg);
    		mySocket.send(pair.socket, responseMsg);
    		updateState(pair.msg);
    	}
	}
	
	/*
	 * This function is our main state logic
	 * It considers our current state, and the incoming message
	 * and changes or updates our state accordingly
	 */
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
				if (senderPid < this.pid && state.state != NodeState.ELECTING) {
					exitCurrentState();
					System.out.println("Received ELECTION from node with lower pid: " + senderPid);
					transitionToElecting();
				}
				break;
			}
			case VICTORY: {
				coordinator = new Peer(senderPort, senderPid);
				this.isCoordinator = false;
				if (state.state == NodeState.RUNNING)
					exitCurrentState();
				transitionToRunning();
				break;
			}
			// We've received a task from the coordinator
			case TASK: {
				if (state.state == NodeState.RUNNING)
					((RunningState) state).spawnFindMinThread(receivedMsg);
				break;
			}
			case TASK_REPLY: {
				// If we're no longer coordinating, result is meaningless, ignore it
				if (state.state != NodeState.COORDINATING)
					break;
				CoordinatingState coordinatingState = ((CoordinatingState) state);
				coordinatingState.min = Math.min(coordinatingState.min, Integer.valueOf(receivedMsg.getContent()));
				coordinatingState.actualReplies += 1;
				if (coordinatingState.actualReplies == coordinatingState.expectedReplies.get() 
					|| coordinatingState.actualReplies == peers.size()) {
					System.out.println("Task finished with minimum: " + String.valueOf(coordinatingState.min));
				}
				else if (coordinatingState.taskReplyTimeout) {
					System.out.println("Not all nodes responsed, task timed out");
					System.out.println("Minimum response received: " + String.valueOf(coordinatingState.min));
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
	
	/*
	 * This function takes the received message and chooses the appropriate reply
	 * We reply immediately in order not to block the sender and risk a timeout
	 * THEN we update our internal state
	 */
	public Message getMessageResponse(Message receivedMsg) {
		long senderPid = Long.valueOf(receivedMsg.getSenderPid());
		switch (receivedMsg.getType()) {
			case ELECTION: {
				// If we are pending victory or the sender has higher pid
				// Don't reply with ANSWER message
				if (state.state == NodeState.PENDING_VICTORY || senderPid > this.pid) {
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
		
	
	
	//---------------------------------------State transitions----------------------------------
	
	void transitionToRunning() {
		this.state = new RunningState(this);
	}
	
	
	void transitionToElecting() {
		this.state = new ElectingState(this);
	}
	
	
	void transitionToPendingVictory() {
		this.state = new PendingVictoryState(this);
	}
	
	
	void transitionToCoordinating() {
		state = new CoordinatingState(this);
	}
	
	
	void exitCurrentState() {
		state.exitState();
	}

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
