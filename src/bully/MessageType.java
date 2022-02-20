package bully;

public enum MessageType {
	OK, // Dummy ACK to avoid prematurely closing socket before receiver consumed the content
	GREETING,
	ELECTION,
	ANSWER,
	ALIVE,
	VICTORY,
	TASK,
	TASK_REPLY,
}
