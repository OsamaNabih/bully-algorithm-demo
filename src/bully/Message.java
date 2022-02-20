package bully;

public class Message {
	
	private MessageType type;
	private int senderPort;
	private String senderPid;
	private String content;
	
	public Message(String response) {
		String[] fields = response.split(";");
		this.type = MessageType.valueOf(fields[0]);
		this.senderPort = Integer.parseInt(fields[1]);
		this.senderPid = fields[2];
		if (fields.length > 3) 
			this.content = fields[3];
		else
			this.content = "";
	}
	
	public Message(MessageType type, int senderPort, String senderPid) {
		super();
		this.type = type;
		this.senderPort = senderPort;
		this.senderPid = senderPid;
		this.content = "";
	}
	
	public Message(MessageType type, int senderPort, String senderPid, String content) {
		super();
		this.type = type;
		this.senderPort = senderPort;
		this.senderPid = senderPid;
		this.content = content;
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
	
	public void setSenderPid(String senderPid) {
		this.senderPid = senderPid;
	}
	
	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
	
	
	public String print() {
		return "Message [type=" + type + ", senderPort=" + senderPort + 
				 ", senderPid=" + senderPid + ", content=" + content + "]";
	}
	
	@Override
	public String toString() {
		return type + ";" + senderPort + ";" + senderPid + ";" + content;
	}

	
}
