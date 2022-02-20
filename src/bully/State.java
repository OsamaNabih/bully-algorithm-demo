package bully;

import java.sql.Timestamp;

public abstract class State {
	//abstract void initState();
	protected Node node;
	protected NodeState state;
	
	State(Node node) {
		this.node = node;
	}
	
	abstract void exitState();
	
	long getCurrTimestamp() {
		return new Timestamp(System.currentTimeMillis()).getTime();
	}
}
