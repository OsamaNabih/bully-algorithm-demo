package bully;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

public class PendingVictoryState extends State {
	AtomicLong lastPendingVictoryTime = new AtomicLong(0);
	Timer victoryTimer = null;
	
	public PendingVictoryState(Node node) {
		super(node);
		System.out.println("Process is now awaiting victory message");
		lastPendingVictoryTime.set(getCurrTimestamp());
		this.victoryTimer = spawnVictoryTimeoutThread();
	}
	
	@Override
	void exitState() {
		this.victoryTimer.cancel();
	}
	
	Timer spawnVictoryTimeoutThread() {
		Timer timer = new Timer();
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
				
				if (node.state.state == NodeState.PENDING_VICTORY && (getCurrTimestamp() - lastPendingVictoryTime.get()) > Consts.pendingVictoryTimeout) {
					System.out.println("Pending victory timed out, starting new election");
					exitState();
					node.transitionToElecting();
				}
					
			}
		};
		timer.schedule(victoryTimeout, Consts.victoryTimeout);
		return timer;
		
	}

}
