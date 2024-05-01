package Project.Server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.Random;

import Project.Common.Constants;
import Project.Common.Payload; //rn364
import Project.Server.ServerThread; //rn364


public class Room implements AutoCloseable 
{
    protected static Server server;// used to refer to accessible server
    // functions
    private String name;
    private Payload p;
    private List<ServerThread> clients = new ArrayList<ServerThread>();

    private boolean isRunning = false;
    // Commands
    private final static String COMMAND_TRIGGER = "/";
    private final static String CREATE_ROOM = "createroom";
    private final static String JOIN_ROOM = "joinroom";
    private final static String DISCONNECT = "disconnect";
    private final static String LOGOUT = "logout";
    private final static String LOGOFF = "logoff"; 
    //uncommented the commands and adding roll and flip rn364
    private final static String ROLL = "roll";
    private final static String FLIP = "flip";
    private final static String BOLD = "bold";
    private final static String ITALIC = "italic";
    private final static String UNDERLINE = "underline";
    private final static String COLOR = "color";
    private final static String TEXT = "text";
    private final static String PRIVATE = "@"; //person can input private command
	private final static String MUTE = "mute"; //person can input mute command
	private final static String UNMUTE = "unmute"; //person can input unmute command


    private Logger logger = Logger.getLogger(Room.class.getName());

    public Room(String name) {
        this.name = name;
        isRunning = true;
    }

    private void info(String message) {
        logger.info(String.format("Room[%s]: %s", name, message));
    }

    public String getName() {
        return name;
    }

    protected synchronized void addClient(ServerThread client) {
        if (!isRunning) {
            return;
        }
        client.setCurrentRoom(this);
        client.sendJoinRoom(getName());// clear first
        if (clients.indexOf(client) > -1) {
            info("Attempting to add a client that already exists");
        } else {
            clients.add(client);
            // connect status second
            sendConnectionStatus(client, true);
            syncClientList(client);
        }


    }

    protected synchronized void removeClient(ServerThread client) {
        if (!isRunning) {
            return;
        }
        clients.remove(client);
        // we don't need to broadcast it to the server
        // only to our own Room
        if (clients.size() > 0) {
            // sendMessage(client, "left the room");
            sendConnectionStatus(client, false);
        }
        checkClients();
    }

    /***
     * Checks the number of clients.
     * If zero, begins the cleanup process to dispose of the room
     */
    private void checkClients() {
        // Cleanup if room is empty and not lobby
        if (!name.equalsIgnoreCase(Constants.LOBBY) && clients.size() == 0) {
            close();
        }
    }

    /***
     * Helper function to process messages to trigger different functionality.
     * 
     * @param message The original message being sent
     * @param client  The sender of the message (since they'll be the ones
     *                triggering the actions)
     */
    private boolean processCommands(String message, ServerThread client) {
        boolean wasCommand = false;
        try {
            if (message.startsWith(COMMAND_TRIGGER)) {
                String[] comm = message.split(COMMAND_TRIGGER);
                String part1 = comm[1];
                String[] comm2 = part1.split(" ");
                String command = comm2[0];
                String roomName;
                wasCommand = true;
                switch (command) {
                    case CREATE_ROOM:
                        roomName = comm2[1];
                        Room.createRoom(roomName, client);
                        break;
                    case JOIN_ROOM:
                       roomName = comm2[1];
                       Room.joinRoom(roomName, client);
                       break;
                    //case ROLL: //rn364
                      // rollDice();
                       //break;
                    case FLIP: //rn364
                       flipCoin();
                       break;
                    case UNMUTE: //unmute command, unmutes a specific client
                            comm2[1] = comm2[1].toLowerCase();
                client.unmuteClient(comm2[1]);
                       break; 
                    case MUTE: //mute command, mutes a specific client
                            comm2[1] = comm2[1].toLowerCase();
                client.muteClient(comm2[1]);
                   break;
                    case DISCONNECT:
                    case LOGOUT:
                    case LOGOFF:
                        Room.disconnectClient(client, this);
                        break;
                    default:
                        wasCommand = false;
                        break;
                }

            }
        } catch (Exception e) 
        {
            e.printStackTrace();
        }
        return wasCommand;
    }

    // Command helper methods
    private synchronized void syncClientList(ServerThread joiner) 
    {
        Iterator<ServerThread> iter = clients.iterator();
        while (iter.hasNext()) 
        {
            ServerThread st = iter.next();
            if (st.getClientId() != joiner.getClientId()) 
            {
                joiner.sendClientMapping(st.getClientId(), st.getClientName());
            }
        }
    }
    protected static void createRoom(String roomName, ServerThread client)
    {
        if (Server.INSTANCE.createNewRoom(roomName)) {
            Room.joinRoom(roomName, client);
        } else {
            client.sendMessage(Constants.DEFAULT_CLIENT_ID, String.format("Room %s already exists", roomName));
        }
    }

    protected static void joinRoom(String roomName, ServerThread client) 
    {
        if (!Server.INSTANCE.joinRoom(roomName, client)) {
            client.sendMessage(Constants.DEFAULT_CLIENT_ID, String.format("Room %s doesn't exist", roomName));
        }
    }

    protected static List<String> listRooms(String searchString, int limit) 
    {
        return Server.INSTANCE.listRooms(searchString, limit);
    }

    protected static void disconnectClient(ServerThread client, Room room) 
    {
        client.setCurrentRoom(null);
        client.disconnect();
        room.removeClient(client);
    }
    // end command helper methods

    /***
     * Takes a sender and a message and broadcasts the message to all clients in
     * this room. Client is mostly passed for command purposes but we can also use
     * it to extract other client info.
     * 
     * @param sender  The client sending the message
     * @param message The message to broadcast inside the room
     */
        protected synchronized void sendMessage(ServerThread sender, String message) 
        {
            if (!isRunning)
                return;
    
            // Moved inside the method
            info(String.format("Sending message to " + clients.size() + " clients"));
    
            if (sender != null && processCommands(message, sender)) 
            {
                // it was a command, don't broadcast
                return;
            }
        
        /// String from = (sender == null ? "Room" : sender.getClientName());
        long from = (sender == null) ? Constants.DEFAULT_CLIENT_ID : sender.getClientId();
        Iterator<ServerThread> iter = clients.iterator();
        message = applyFormatting(message);
        while (iter.hasNext()) {
            ServerThread client = iter.next();
            if (sender!= null && client.checkingMutedList(sender.getClientName())) {
                continue;
            }
            //boolean messageSent = false;
            boolean messageSent = client.sendMessage(from, message);
            if (!messageSent) {
                handleDisconnect(iter, client);
            }
        }
        
        if (sender != null && message.startsWith("@"))//rn364
        {
            int spaceIndex = message.indexOf(" ");
            if (spaceIndex != -1) 
            {
                String recipientName = message.substring(1, spaceIndex);
                String privateMessage = message.substring(spaceIndex + 1);

                boolean recipientFound = false;
                for (ServerThread client : clients) 
                {
                    if (client.getClientName().equals(recipientName)) 
                    {
                        client.sendMessage(sender.getClientId(), "(Private Message): " + privateMessage);
                        recipientFound = true;
                        break;
                    }
                }

                if (!recipientFound) {
                    sender.sendMessage(sender.getClientId(), "Recipient not found: " + recipientName);
                }
            } else {
                sender.sendMessage(sender.getClientId(),
                        "Invalid private message format. Usage: @[recipient] [message]");
            }
        } else 
        {
            // Regular broadcast message
            while (iter.hasNext()) 
            {
                ServerThread client = iter.next();
                boolean messageSent = client
                        .sendMessage(sender == null ? Constants.DEFAULT_CLIENT_ID : sender.getClientId(), message);
                if (!messageSent) 
                {
                    handleDisconnect(iter, client);
                }
            }
        }
    }

    protected synchronized void sendConnectionStatus(ServerThread sender, boolean isConnected) {
        Iterator<ServerThread> iter = clients.iterator();
        while (iter.hasNext()) 
        {
            ServerThread client = iter.next();
            boolean messageSent = client.sendConnectionStatus(sender.getClientId(), sender.getClientName(),
                    isConnected);
            if (!messageSent) {
                handleDisconnect(iter, client);
            }
        }
    }

    private void handleDisconnect(Iterator<ServerThread> iter, ServerThread client) {
        iter.remove();
        info("Removed client " + client.getClientName());
        checkClients();
        sendMessage(null, client.getClientName() + " disconnected");
    }
    private String applyFormatting(String message)//rn364
    {
        if ((message.contains(".*")) && message.contains("*."))
        {
            message = message.replace(".*","<b>");//rn364 BOLD
            message = message.replace("*.", "</b>");
        }
        if ((message.contains(".|")) && message.contains("|."))
        {
            message = message.replace(".|","<i>");//rn364 ITALICS
            message = message.replace("|.", "</i>");
        }
        if ((message.contains("._")) && message.contains("_."))
        {
            message = message.replace("._","<u>"); //rn364 UNDERLINE
            message = message.replace("_.", "</u>");
        }
        if ((message.contains("R[")) && message.contains("]R"))
        {
            message = message.replace("R[","<font> color = RED");//rn364
            message = message.replace("]R", "</font>");
        }
        if ((message.contains("G[")) && message.contains("]G"))
        {
            message = message.replace("G[","<font> color = GREEN");//rn364
            message = message.replace("]G", "</font>");
        }
        if ((message.contains("B[")) && message.contains("]B"))
        {
            message = message.replace("B[","<font> color = BLUE");//rn364
            message = message.replace("]B", "</font>");
        }
        return message;
    }

    public void close() {
        Server.INSTANCE.removeRoom(this);
        // server = null;
        isRunning = false;
        clients = null;
    }
    public void flipCoin()
    {
        Random random = new Random();
        String result = random.nextBoolean() ? "Heads" : "Tails";
        sendMessage(null, "coinflip is: " + result);
    }
    public void rollDice(int dice, int sides) {
        Random random = new Random();
        int result, total = 0;
        for (int i = 0; i < dice; i++){
        result = random.nextInt(sides) + 1;
        total = total + result;
        }
        String finMessage = "Dice roll result is " + total;
        sendMessage(null, finMessage);
    }
   
}

