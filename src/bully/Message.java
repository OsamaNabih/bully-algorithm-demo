package bully;

public class Message {
	private MessageType type;
	private int senderPort;
	//private long timestamp;
	private String senderPid;
	
	
	public Message(String response) {
		String[] fields = response.split(",");
		this.type = MessageType.valueOf(fields[0]);
		this.senderPort = Integer.parseInt(fields[1]);
		//this.timestamp = Long.parseLong(fields[2]);
		if (fields.length > 2)
			this.senderPid = fields[2];
		else
			this.senderPid = "";
	}
	
	public Message(MessageType type, int senderPort, String senderPid) {
		super();
		this.type = type;
		this.senderPort = senderPort;
		this.senderPid = senderPid;
		//this.timestamp = getCurrTimestamp();
	}
	
	/*
	public Message(MessageType type, int senderPort, String senderPid, long timestamp) {
		super();
		this.type = type;
		this.senderPort = senderPort;
		this.receiverPort = receiverPort;
		this.timestamp = timestamp;
		this.senderPid = senderPid;
	}
	*/
	
	public MessageType getType() {
		return type;
	}
	public void setType(MessageType type) {
		this.type = type;
	}
	public int getSenderPort() {
		return senderPort;
	}
	public void setSenderPort(int senderPort) {
		this.senderPort = senderPort;
	}

//	public long getTimestamp() {
//		return timestamp;
//	}
//	public void setTimestamp(long timestamp) {
//		this.timestamp = timestamp;
//	}
	
	public String getSenderPid() {
		return senderPid;
	}
	public void setsenderPid(String senderPid) {
		this.senderPid = senderPid;
	}
	
//	public String print() {
//		return "Message [type=" + type + ", senderPort=" + senderPort + 
//				", timestamp=" + timestamp + ", senderPid=" + senderPid + "]";
//	}
	
	public String print() {
		return "Message [type=" + type + ", senderPort=" + senderPort + 
				 ", senderPid=" + senderPid + "]";
	}
	
	@Override
	public String toString() {
		return type + "," + senderPort + "," + senderPid;
	}
	
//	@Override
//	public String toString() {
//		return type + "," + senderPort + "," + timestamp + "," + senderPid;
//	}
	
	
	
	/*
	@Override
	public String toString() {
		return "Message [type=" + type + ", senderPort=" + senderPort + ", receiverPort=" + receiverPort
				+ ", timestamp=" + timestamp + ", senderPid=" + senderPid + "]";
	}
	*/
}
