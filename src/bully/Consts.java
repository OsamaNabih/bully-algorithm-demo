package bully;

public class Consts {
	/* In a real environment
	 * All these consts should be adjusted depending on number of nodes, network speed and physical proximity
	 * as well as the max number of nodes
	 */
	public static int maxNumOfNodes = 5;
	public static int startingPort = 4000;
	
	public static String host = "localhost";
	public static String ip = "127.0.0.1";
	
	public static int generalTimeout = 2000;
	public static int aliveInterval = 3000;
	public static int coordinatorDeadTimeout = (int)(aliveInterval * 2.5);
	public static int waitingForTaskResponseTimeout = 4000;
	public static int taskTimeout = 0;
	
	// defined as separate variables for more configurability
	
	public static int victoryTimeout = generalTimeout;
	public static int acquireSocketTimeout = generalTimeout;
	public static int electionTimeout = generalTimeout / 2;
	public static int greetingTimeout = generalTimeout / 2;
	public static int pendingVictoryTimeout = generalTimeout * 2;
	public static int aliveTimeout = generalTimeout;
	//public static int taskTimeout = (waitingForTaskResponseTimeout / maxNumOfNodes);
	
	
	public static int randomArrSize = 100;
	public static int maxInt = 10000;
	
	
	
}
