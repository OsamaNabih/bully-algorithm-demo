package bully;

import java.sql.Timestamp;

public class TimestampedMessage {
	/*
	 * The assignment required a RECEIVING timestamp for all messages
	 * Therefore I removed the sending timestamp from our message class
	 * as it wasn't of use in this demo and to make the message prints simpler
	 * However in many distributed scenarios a timestamp is essential
	 * So I made this class in case I was asked to extend the code
	 */
	
	private MessageType type;
	private int senderPort;
	private long timestamp;
	private String senderPid;
	
	
	
	
	public TimestampedMessage(String response) {
		String[] fields = response.split(",");
		this.type = MessageType.valueOf(fields[0]);
		this.senderPort = Integer.parseInt(fields[1]);
		this.timestamp = Long.parseLong(fields[2]);
		if (fields.length > 3)
			this.senderPid = fields[3];
		else
			this.senderPid = "";
	}
	
	public TimestampedMessage(MessageType type, int senderPort, String senderPid) {
		super();
		this.type = type;
		this.senderPort = senderPort;
		this.senderPid = senderPid;
		this.timestamp = getCurrTimestamp();
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

	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	public String getSenderPid() {
		return senderPid;
	}
	public void setsenderPid(String senderPid) {
		this.senderPid = senderPid;
	}
	
	public String print() {
		return "Message [type=" + type + ", senderPort=" + senderPort + 
				", timestamp=" + timestamp + ", senderPid=" + senderPid + "]";
	}
	
	
	@Override
	public String toString() {
		return type + "," + senderPort + "," + timestamp + "," + senderPid;
	}
	
	private long getCurrTimestamp() {
		return new Timestamp(System.currentTimeMillis()).getTime();
	}
	
}
