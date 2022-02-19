package bully;

public class Peer {
	int port;
	long pid;
	
	Peer(int port) {
		this.port = port;
	}
	
	Peer(int port, long pid) {
		this.port = port;
		this.pid = pid;
	}
	
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public long getPid() {
		return pid;
	}
	public void setPid(int pid) {
		this.pid = pid;
	}
}
