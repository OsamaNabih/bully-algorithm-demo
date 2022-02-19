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
	
	Network() {
		acquireOrdinaryPort();
	}
	
	public void acquireOrdinaryPort() {
		int i = Consts.startingPort;
		boolean acquiredPort = false;
		while(!acquiredPort && i < Consts.startingPort + Consts.maxNumOfNodes) {
			try {
				bind(i);
				acquiredPort = true;
				System.out.println("Acquired port " + String.valueOf(i));
			} catch(Exception e) {
				System.out.println("Error: " + e.toString());
				System.out.println("Failed to acquire port " + String.valueOf(i));
				i++;
			}
		}
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
			System.out.println("Received message :  " + receivedMsg.print() + " from " + receivedMsg.getSenderPort());
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
	
	public Message receiveAndSend(Node node, int timeout) {
		try {
			Socket s= receiverSocket.accept(); 
            DataInputStream receivedDataStream = new DataInputStream(s.getInputStream());
            DataOutputStream sentDataStream = new DataOutputStream(s.getOutputStream());
            System.out.println("Receiving and sending");
            String receivedData = receivedDataStream.readUTF();
            Message receivedMsg = new Message(receivedData);
            System.out.println("Received message :  " + receivedMsg.print() + " from " + receivedMsg.getSenderPort());
            Message sentMsg = node.getAppropriateResponse(receivedMsg);
            System.out.println("Responded with " + sentMsg.print() + " to " + receivedMsg.getSenderPort());
            sentDataStream.writeUTF(sentMsg.toString());
            sentDataStream.flush();
            sentDataStream.close();
            receivedDataStream.close();
            return receivedMsg;
		} catch (Exception e) {
			return null;
		}
	}
	
	public Message sendAndReceive(String targetAddress, int targetPort, Message msg, int timeout) {
		try {
			Socket s = new Socket(targetAddress, targetPort);
			
			s.setSoTimeout(timeout);
			DataOutputStream sentDataStream = new DataOutputStream(s.getOutputStream());
			sentDataStream.writeUTF(msg.toString());
			sentDataStream.flush();
			System.out.println("Sent message " + msg.print() + " to " + targetPort);
			DataInputStream receivedDataStream = new DataInputStream(s.getInputStream());
			String receivedData = receivedDataStream.readUTF();
			Message receivedMsg = parseResponse(receivedData);
			System.out.println("Received response message :  " + receivedMsg.print() + " from " + receivedMsg.getSenderPort());
			sentDataStream.close();
			receivedDataStream.close();
			s.close();
			return receivedMsg;
		} catch(Exception e) {
			System.out.println("Error: " + e.toString());
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
