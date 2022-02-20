package bully;

import java.util.ArrayList;
import java.util.List;

public class InitState extends State {
	
	List<Thread> discoveryThreads = new ArrayList<>();
	
	public InitState(Node node) {
		// Acquire a new socket
		super(node);
		Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
            	discoverPeers();
            	waitAllThreads(discoveryThreads);
    			System.out.println("Discovered " + node.peers.size() + " peers");
            	node.transitionToRunning();
        	}
        });
		thread.start();
	}

	@Override
	void exitState() {
		return;
	}
	
	// This function iterates over all possibly occupied ports
	// If it receives an answer, it adds the peer at that port to our list of peers
	// After all threads are done, it sets the finishDiscovery flag to indicate we are ready in case of an election
	void discoverPeers() {
		System.out.println("Discovering peers");
		Message msg = new Message(MessageType.GREETING, node.mySocket.getReceiverSocket().getLocalPort(), String.valueOf(node.pid));
		
		for(int i = Consts.startingPort, j = 0; j < Consts.maxNumOfNodes; j++) {
			if (i == node.mySocket.getReceiverSocket().getLocalPort()) {
				i++;
				continue;
			}
				
			Thread thread = spawnDiscoveryThread(i, msg);
			discoveryThreads.add(thread);
			thread.start();
			i++;
		}
		
	}
		
	Thread spawnDiscoveryThread(int targetPort, Message msg) {
		Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
        		Message receivedMsg = node.mySocket.sendAndReceive(Consts.ip, targetPort, msg, Consts.greetingTimeout);
    			if (receivedMsg == null) {
    				return;
    			}
    			long senderPid = Long.valueOf(receivedMsg.getSenderPid());
    			int senderPort = Integer.valueOf(receivedMsg.getSenderPort());
    			node.addPeer(new Peer(senderPort, senderPid));
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

}
