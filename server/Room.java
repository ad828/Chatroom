package server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Room implements AutoCloseable {
    private static SocketServer server;// used to refer to accessible server functions
    private String name;
    private final static Logger log = Logger.getLogger(Room.class.getName());

    // Commands
    private final static String COMMAND_TRIGGER = "/";
    private final static String CREATE_ROOM = "createroom";
    private final static String JOIN_ROOM = "joinroom";
    private final static String FLIP = "flip";
    private final static String ROLL = "roll";
    private final static String MUTE = "mute";
    private final static String UNMUTE = "unmute";
    private final static String PM = "pm";
    
    public Room(String name) {
	this.name = name;
    }

    public static void setServer(SocketServer server) {
	Room.server = server;
    }

    public String getName() {
	return name;
    }

    private List<ServerThread> clients = new ArrayList<ServerThread>();

    protected synchronized void addClient(ServerThread client) {
	client.setCurrentRoom(this);
	if (clients.indexOf(client) > -1) {
	    log.log(Level.INFO, "Attempting to add a client that already exists");
	}
	else {
	    clients.add(client);
	    if (client.getClientName() != null) {
		client.sendClearList();
		sendConnectionStatus(client, true, "joined the room " + getName());
		updateClientList(client);
	    }
	}
    }

    private void updateClientList(ServerThread client) {
	Iterator<ServerThread> iter = clients.iterator();
	while (iter.hasNext()) {
	    ServerThread c = iter.next();
	    if (c != client) {
		boolean messageSent = client.sendConnectionStatus(c.getClientName(), true, null);
	    }
	}
    }

    protected synchronized void removeClient(ServerThread client) {
	clients.remove(client);
	if (clients.size() > 0) {
	    // sendMessage(client, "left the room");
	    sendConnectionStatus(client, false, "left the room " + getName());
	}
	else {
	    cleanupEmptyRoom();
	}
    }

    private void cleanupEmptyRoom() {
	// If name is null it's already been closed. And don't close the Lobby
	if (name == null || name.equalsIgnoreCase(SocketServer.LOBBY)) {
	    return;
	}
	try {
	    log.log(Level.INFO, "Closing empty room: " + name);
	    close();
	}
	catch (Exception e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
	}
    }

    protected void joinRoom(String room, ServerThread client) {
	server.joinRoom(room, client);
    }

    protected void joinLobby(ServerThread client) {
	server.joinLobby(client);
    }

    /***
     * Helper function to process messages to trigger different functionality.
     * 
     * @param message The original message being sent
     * @param client  The sender of the message (since they'll be the ones
     *                triggering the actions)
     */
    private boolean processCommands(String message, ServerThread client) {
	String response = null;
	boolean wasCommand = false;
	try {
	    if (message.indexOf(COMMAND_TRIGGER) > -1) {
		String[] comm = message.split(COMMAND_TRIGGER);
		log.log(Level.INFO, message);
		String part1 = comm[1];
		String[] comm2 = part1.split(" ");
		String command = comm2[0];
		if (command != null) {
		    command = command.toLowerCase();
		}
		String roomName;
		String clientName;
		switch (command) {
		case CREATE_ROOM:
		    roomName = comm2[1];
		    if (server.createNewRoom(roomName)) {
			joinRoom(roomName, client);
		    }
		    break;
		case JOIN_ROOM:
		    roomName = comm2[1];
		    joinRoom(roomName, client);
		    break;
		case ROLL:
			int num = (int)(Math.random() * 5)+1;
			response = "%You got:" + num + "%";
			sendMessage(client, response);
			break;
		case FLIP:
			double flip = Math.random();
			if (flip < 0.5) {
				response = "*Heads*";
			} else {
				response = "*Tails*";
			}
			sendMessage(client, response);
			break;
		case MUTE:
			clientName = comm2[1];
			ServerThread MutedPerson;
			if (!ServerThread.isMuted(clientName)) {
				for (ServerThread cli : clients) {
					if (cli.getClientName().equals(clientName)){
						client.mutedPeople.add(clientName);
						MutedPerson = cli;
						MutedPerson.send("Mute Daemon: ", "You were muted by " + client.getClientName());
						sendMessage(client,"Muted " + clientName);
						saveMuteList(client);
					}
				}
			}
			wasCommand = true;
			break;
		case UNMUTE:
			clientName = comm2[1];
			ServerThread UnMutedPerson;
			if (ServerThread.isMuted(clientName)) {
				for(ServerThread cli : clients) {
					if (cli.getClientName().equals(clientName)){
						client.mutedPeople.remove(clientName);
						UnMutedPerson = cli;
						//UnMutedPerson.send("Mute Daemon: ", "You were Unmuted by " + client.getClientName());
						UnMutedPerson.send("Mute Daemon: ", "You are Unmuted by " + client.getClientName());
						sendMessage(client,"unMuted " + clientName);
					}
				}
			}
			wasCommand = true;
			break;
		case PM:
			List<String> clients = new ArrayList<String>();
			sendPrivateMessage(client,clients,message);
			clients.add(client.getClientName());
			response = null;
			wasCommand = true;
			break;
		default: 
			response = message;
		break;
		}
	    }
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
	return wasCommand;
    }

    // TODO changed from string to ServerThread
    protected void sendConnectionStatus(ServerThread client, boolean isConnect, String message) {
	Iterator<ServerThread> iter = clients.iterator();
	while (iter.hasNext()) {
	    ServerThread c = iter.next();
	    boolean messageSent = c.sendConnectionStatus(client.getClientName(), isConnect, message);
	    if (!messageSent) {
		iter.remove();
		log.log(Level.INFO, "Removed client " + c.getId());
	    }
	}
    }

    /***
     * Takes a sender and a message and broadcasts the message to all clients in
     * this room. Client is mostly passed for command purposes but we can also use
     * it to extract other client info.
     * 
     * @param sender  The client sending the message
     * @param message The message to broadcast inside the room
     */
    protected void sendMessage(ServerThread sender, String message) {
    	log.log(Level.INFO, getName() + ": Sending message to " + clients.size() + " clients");
    	if (processCommands(message, sender)) {
    	    // it was a command, don't broadcast
    	    return;
    	}
    	Iterator<ServerThread> iter = clients.iterator();
    	while (iter.hasNext()) {
    	    ServerThread client = iter.next();
    	    if (!ServerThread.isMuted(sender.getClientName())) {
    	    boolean messageSent = client.send(sender.getClientName(), message);
    	    if (!messageSent) {
    		iter.remove();
    		log.log(Level.INFO, "Removed client " + client.getId());
    	    }
    	}
    	    }
        }
	protected void sendPrivateMessage(ServerThread user, List<String> users,String message) {
		log.log(Level.INFO, getName() + ": Sending message to " + clients.size() + " clients");
		if (processCommands(message, user)) { // it was a command,don't broadcast
			return;
		}
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread client = iter.next();
			if (users.contains(user.getClientName())) {
				boolean messageSent = client.send(user.getClientName(), message);
				if (!messageSent) {
					iter.remove();
					log.log(Level.INFO, "Removed client " + client.getId());
				}
			}

		}
	}
	public void saveMuteList(ServerThread client) {
		try {
			File fi = new File("MuteList.txt");
			fi.createNewFile();
			FileWriter wr = new FileWriter("MuteList.txt", false);
			Iterator<String> mtlst = client.mutedPeople.iterator();
			while (mtlst.hasNext()) {
				String clientName = mtlst.next();
				wr.write(clientName + "\n");
			}
			wr.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
    /***
     * Will attempt to migrate any remaining clients to the Lobby room. Will then
     * set references to null and should be eligible for garbage collection
     */
    @Override
    public void close() throws Exception {
	int clientCount = clients.size();
	if (clientCount > 0) {
	    log.log(Level.INFO, "Migrating " + clients.size() + " to Lobby");
	    Iterator<ServerThread> iter = clients.iterator();
	    Room lobby = server.getLobby();
	    while (iter.hasNext()) {
		ServerThread client = iter.next();
		lobby.addClient(client);
		iter.remove();
	    }
	    log.log(Level.INFO, "Done Migrating " + clients.size() + " to Lobby");
	}
	server.cleanupRoom(this);
	name = null;
	// should be eligible for garbage collection now
    }

} 