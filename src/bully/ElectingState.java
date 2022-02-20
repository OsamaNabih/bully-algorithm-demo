package bully;

import java.util.ArrayList;
import java.util.List;

public class ElectingState extends State {
	boolean isLargest = true;
	
	public ElectingState(Node node) {
		super(node);
		this.state = NodeState.ELECTING;
		this.isLargest = true;
		initElections();
	}
	
	void initElections() {
		System.out.println("Initiating elections");
		
		List<Peer> failedPeers = new ArrayList<Peer>();
		Message msg = new Message(MessageType.ELECTION, node.mySocket.getReceiverSocket().getLocalPort(), String.valueOf(node.pid));
		for(Peer peer : node.peers.values()) {
			if (peer.getPid() < node.pid && peer.getPid() != 0)
				continue;
			Message receivedMsg = node.mySocket.sendAndReceive(Consts.ip, peer.getPort(), msg, Consts.electionTimeout);
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
				break;
			}			
		}

		if (failedPeers.size() > 0) {
			for (Peer failedPeer : failedPeers)
				node.removePeer(failedPeer);
		}
		
		if (!this.isLargest) {
			exitState();
			node.transitionToPendingVictory();
			return;
		}
		exitState();
		System.out.println("This node with pid: " + node.pid + " won the elections");
		node.transitionToCoordinating();		
	}
	
	@Override
	void exitState() {
		// TODO Auto-generated method stub
		return;
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
	
	*/

}
