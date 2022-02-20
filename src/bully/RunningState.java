package bully;


public class RunningState extends State {
		// RUNNING state variables
		
		Thread aliveTimeoutCheckerThread = null;
		Thread taskThread = null;
		int chunkMin = Consts.maxInt;
		
		
		public RunningState(Node node) {
			super(node);
			this.state = NodeState.RUNNING;
			spawnAliveTimeoutCheckerThread();
		}
		
		@Override
		void exitState() {
			// TODO Auto-generated method stub
			aliveTimeoutCheckerThread.interrupt();
			if (taskThread != null)
				taskThread.interrupt();
		}
		
		void spawnAliveTimeoutCheckerThread() {
			node.lastAliveTime.set(getCurrTimestamp());
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
	            					exitState();
	            					System.out.println("This node has the new highest pid: " + node.pid + ", becoming new coordinator");
	            					node.transitionToCoordinating();
	            					return;
	            				}
	            				else {
	            					exitState();
	            					node.transitionToElecting();
	            					return;
	            				}
	            			}
	            		}
	            	} catch (Exception e) {
	            		return;
	            	}
	            }
	        });
			aliveTimeoutCheckerThread.start();
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
					Message msg = new Message(MessageType.TASK_REPLY, node.mySocket.getReceiverSocket().getLocalPort(), String.valueOf(node.pid), String.valueOf(chunkMin));
					node.mySocket.sendAndReceive(Consts.ip, node.coordinator.port, msg, Consts.taskTimeout);
				}
			});
			findMinThread.start();
		}
		
}
