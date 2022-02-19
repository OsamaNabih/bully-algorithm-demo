package bully;

import java.sql.Timestamp;

public class Message {
	private MessageType type;
	private int senderPort;
	private long timestamp;
	private String content;
	
	
	private long getCurrTimestamp() {
		return new Timestamp(System.currentTimeMillis()).getTime();
	}
	
	public Message(String response) {
		String[] fields = response.split(",");
		this.type = MessageType.valueOf(fields[0]);
		this.senderPort = Integer.parseInt(fields[1]);
		this.timestamp = Long.parseLong(fields[2]);
		if (fields.length > 3)
			this.content = fields[3];
		else
			this.content = "";
	}
	
	public Message(MessageType type, int senderPort, String content) {
		super();
		this.type = type;
		this.senderPort = senderPort;
		this.content = content;
		this.timestamp = getCurrTimestamp();
	}
	
	/*
	public Message(MessageType type, int senderPort, String content, long timestamp) {
		super();
		this.type = type;
		this.senderPort = senderPort;
		this.receiverPort = receiverPort;
		this.timestamp = timestamp;
		this.content = content;
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

	public long getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	
	public String print() {
		return "Message [type=" + type + ", senderPort=" + senderPort + 
				", timestamp=" + timestamp + ", content=" + content + "]";
	}
	
	@Override
	public String toString() {
		return type + "," + senderPort + "," + timestamp + "," + content;
	}
	/*
	@Override
	public String toString() {
		return "Message [type=" + type + ", senderPort=" + senderPort + ", receiverPort=" + receiverPort
				+ ", timestamp=" + timestamp + ", content=" + content + "]";
	}
	*/
}
