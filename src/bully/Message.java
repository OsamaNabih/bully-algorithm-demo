package bully;

public class Message {
	
	private MessageType type;
	private int senderPort;
	private String senderPid;
	
	public Message(String response) {
		String[] fields = response.split(",");
		this.type = MessageType.valueOf(fields[0]);
		this.senderPort = Integer.parseInt(fields[1]);
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
	}	
	
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
	
	public String getSenderPid() {
		return senderPid;
	}
	
	public void setsenderPid(String senderPid) {
		this.senderPid = senderPid;
	}
	
	public String print() {
		return "Message [type=" + type + ", senderPort=" + senderPort + 
				 ", senderPid=" + senderPid + "]";
	}
	
	@Override
	public String toString() {
		return type + "," + senderPort + "," + senderPid;
	}
	
}
