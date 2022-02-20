package bully;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Network {
	String address = Consts.ip;
	int port;
	ServerSocket receiverSocket = null;
	
	Network() throws Exception {
		acquireOrdinaryPort();
	}
	
	public void acquireOrdinaryPort() throws Exception {
		int i = Consts.startingPort;
		while(i < Consts.startingPort + Consts.maxNumOfNodes) {
			try {
				bind(i);
				System.out.println("Acquired port " + String.valueOf(i));
				return;
			} catch(Exception e) {
				//System.out.println("Error: " + e.toString());
				//System.out.println("Failed to acquire port " + String.valueOf(i));
				i++;
			}
		}
		throw new Exception("No empty ports available");
	}
	

	private void bind(int port) throws IOException {
		this.receiverSocket = new ServerSocket(port);
	}
	
	private Message parseResponse(String response) {
		if (response.isEmpty())
			return null;
		return new Message(response);
	}
	
	public CustomPair receive() {
		try {
			Socket s = receiverSocket.accept();
			DataInputStream receivedDataStream = new DataInputStream(s.getInputStream());
			String receivedData = receivedDataStream.readUTF();
			Message receivedMsg = new Message(receivedData);
			
			CustomPair pair = new CustomPair(s, receivedMsg);
			return pair;
		} catch (Exception e) {
			return null;
		}
	}
	
	public void send(Socket s, Message msg) {
		try {
			DataOutputStream sentDataStream = new DataOutputStream(s.getOutputStream());
			sentDataStream.writeUTF(msg.toString());
			sentDataStream.close();
			s.getInputStream().close();
			s.close();
		} catch (Exception e) {
			return;
		}
	}
	
	
	public Message sendAndReceive(String targetAddress, int targetPort, Message msg, int timeout) {
		try {
			Socket s = new Socket(targetAddress, targetPort);
			
			s.setSoTimeout(timeout);
			DataOutputStream sentDataStream = new DataOutputStream(s.getOutputStream());
			sentDataStream.writeUTF(msg.toString());
			sentDataStream.flush();
			//System.out.println("Sent " + msg.print() + " to " + targetPort);
			DataInputStream receivedDataStream = new DataInputStream(s.getInputStream());
			String receivedData = receivedDataStream.readUTF();
			Message receivedMsg = parseResponse(receivedData);
			//System.out.println("Received response :  " + receivedMsg.print());
			sentDataStream.close();
			receivedDataStream.close();
			s.close();
			return receivedMsg;
		} catch(Exception e) {
			//System.out.println("Error: " + e.toString());
			return null;
		}	
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public ServerSocket getReceiverSocket() {
		return receiverSocket;
	}

	public void setReceiverSocket(ServerSocket receiverSocket) {
		this.receiverSocket = receiverSocket;
	}
}
