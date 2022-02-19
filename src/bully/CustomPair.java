package bully;

import java.net.Socket;

public class CustomPair {
	public CustomPair(Socket socket, Message msg) {
		super();
		this.socket = socket;
		this.msg = msg;
	}
	public Socket socket;
	public Message msg;
}
