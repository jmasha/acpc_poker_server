package glassfrog.server;

import glassfrog.model.Gamedef;
import glassfrog.tools.XMLParser;
import glassfrog.tools.XMLValidator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * The Server class is a persistant java server that has a {@link ServerSocket}
 * serving on port 9000.  This ServerSocket handles incoming connections and 
 * sends back information depending on the request made.  The Server is capable 
 * of creating a {@link Room}, querying information about a Room or general 
 * status
 *  
 * Each connection to the server is forked into a separate thread to allow 
 * multiple conncurent connections to the Server.  Each thread only stays alive 
 * for the duration of the request connection and then is terminated.
 * 
 * @author jdavidso
 */
public class Server implements Runnable {

    private static ArrayList<Room> rooms = new ArrayList();
    private static ArrayList portList = new ArrayList();
    private static ArrayList keyList = new ArrayList();
    private static ArrayList<Thread> liveThreads = new ArrayList();
    private static ServerSocket ss;
    private FileHandler errorFileHandler,  serverFileHandler;
    private String serverLog, errorLog;
    private static boolean alive = true;
    private static final int PORT = 9000;
    private static final int TIMEOUT = 30000;
    private static final int CONNECTION_MAX = 64;

    /**
     * The constructor for the server starts up a server on port 9000 and 
     * opens a ServerSocket to handle server requests
     * @throws java.net.BindException
     * @throws java.io.IOException
     */
    public Server() throws BindException, IOException {
        ss = new ServerSocket(PORT);
        ss.setSoTimeout(TIMEOUT);
        initLogging();
    }
    
    /**
     * Initialize the logs for the game.    
     */
    private void initLogging() {
        String logPath = "logs/";
        Logger rootLogger = Logger.getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        if (handlers.length > 0) {
            if (handlers[0] instanceof ConsoleHandler) {
                rootLogger.removeHandler(handlers[0]);
            }
        }
        serverLog = logPath + "server.log";        
        errorLog = logPath + "server.err";        
        try {
            errorFileHandler = new FileHandler(errorLog, true);
            errorFileHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger("errorLogger").addHandler(errorFileHandler);
            serverFileHandler = new FileHandler(serverLog, true);
            serverFileHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger("serverLogger").addHandler(serverFileHandler);
        } catch (IOException ex) {
            System.err.println("Could not initialize logs, exit with IO Error " + ex.toString());
        } catch (SecurityException ex) {
            System.err.println("Could not initialize logs, exit with Secutirty Error " + ex.toString());
        }
    }
    
    /**
     * Utility for logging an error message to the errorLogger
     * @param errorMessage A message to log to the errror log
     */
    private void logError(String errorMessage) {
        Logger.getLogger("errorLogger").log(Level.SEVERE, errorMessage);
    }

    /**
     * Utility for logging a warning message to the errorLogger
     * @param warningMessage A message to log to the error log
     */
    private void logWarning(String warningMessage) {
        Logger.getLogger("errorLogger").log(Level.WARNING, warningMessage);
    }

    /**
     * Log a info to the serverLogger
     * @param info the message to log to the room log 
     */
    private void logInfo(String info) {
        Logger.getLogger("serverLogger").log(Level.INFO, info);
    }

    /**
     * The run method for the server.  A server will busy wait and listen for 
     * incoming connections on it's ServerSocket and then fork a ServerConnectionHandler
     * thread to deal with the connections while the server is still alive
     */
    public void run() {                
        while (alive) {
            try {
                if (liveThreads.size() < CONNECTION_MAX) {
                    Socket s = ss.accept();
                    Thread t = new Thread(new ServerConnectionHandler(s));
                    t.start();
                    liveThreads.add(t);
                } else {
                    logError("Max connections reached.  Try again in 5s");
                    Thread.sleep(4000);
                }
                houseKeeping();
            } catch (SocketTimeoutException ex) {
                houseKeeping();
            } catch (IOException ex) {                
                logError("IO Exception in server run thread: "+ex.toString());
                logError(ex.getStackTrace().toString());
                System.exit(-1);
            } catch (InterruptedException ex) {
                logError("Server thread interrupted");
                logError(ex.toString());
            }
        }
        try {
            ss.close();
        } catch (IOException ex) {
            logError("IO Error caugtht while attepting to close socket in Server run thread:"+ex.toString());
            logError(ex.getStackTrace().toString());
            System.exit(-1);
        }
    }

    /**
     * Do some housekeeping whenever a new connection is started or every 30 seconds
     * on the socket timeout
     */
    private void houseKeeping() {
        //Some housekeeping every 30s
        for (int i = 0; i < rooms.size(); i++) {
            Room r = rooms.get(i);
            if (!r.isAlive()) {
                if (keyList.contains(r.getName())) {
                    System.out.println("Freeing Key: " + r.getName());
                    logInfo("Freeing Key: " + r.getName());
                    keyList.remove(r.getName());
                }
                r.shutdown();
                System.out.println("Removing Room: " + r.getName() + " from active list");
                logInfo("Removing Room: " + r.getName() + " from active list");
                rooms.remove(r);
                i--;
            }
        }
        for (int i = 0; i < liveThreads.size(); i++) {
            Thread t = liveThreads.get(i);
            if (!t.isAlive()) {
                liveThreads.remove(t);
                i--;
            }
        }
    }

    /**
     * A class used to handle incoming connections to the server.  This class is 
     * used to parse the request arguments to the server such as the requests to add 
     * and kill rooms, info requests from rooms and other information regarding the 
     * state of the server
     * @author jdavidso
     */
    public class ServerConnectionHandler implements Runnable {

        private PrintWriter pw;
        private BufferedReader br;
        private Socket socket;
        private final int TIMEOUT = 300000;

        /**
         * The ServerConnectionHandler takes the socket that the ServerSocket 
         * gets from an accepted connection.  A PrintWriter and BufferedReader
         * are then set up to get the incoming request and possibly return any
         * information to the sender.
         * 
         * After 30s of inactivity from a socket, the socket will timeout
         * 
         * @param socket A Socket passed in from the server
         * @throws java.io.IOException Any exceptions from the socket handleing
         */        
        public ServerConnectionHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.socket.setSoTimeout(TIMEOUT);
            this.br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.pw = new PrintWriter(socket.getOutputStream());
        }

        /**
         * A wrapper to handle the requests from the server so that this can be
         * invoked in a thread
         */
        public void run() {
            try {
                handleRequest();
            } catch (InterruptedException ex) {
                logError("Interrupted Exception in connection handler run thread: " + ex.toString());
                logError(ex.getStackTrace().toString());
            } catch (SocketTimeoutException ex) {
                try {
                    logError("SocketTimeout Exception in connection handler run thread: " + ex.toString());
                    logError(ex.getStackTrace().toString());
                    br.close();
                    pw.close();
                    socket.close();
                } catch (IOException ex1) {
                    logError("Could not shut down socket, IO Exception caught in shutdown routine");
                }
            } catch (IOException ex) {
                logError("IO Exception in connection handler run thread: "+ex.toString());
                logError(ex.getStackTrace().toString());                
            }
        }

        /**
         * Handles any request made from a connection to the server
         * @throws java.io.IOException
         * @throws java.socket.SocketTimeoutException
         */
        private void handleRequest() throws IOException, SocketTimeoutException,
                InterruptedException {
            String request = br.readLine();
            StringTokenizer st = new StringTokenizer(request, ":");
            if (st.countTokens() < 1) {
                logError("Server request " + request + "empty or missing parameters");                
                return;
            }
            String requestType = st.nextToken();

            //Handle Status Request
            if (requestType.equalsIgnoreCase("LIST")) {
                pw.println(getRooms());
                pw.flush();
            }
            if (requestType.equalsIgnoreCase("STATUS")) {
                while (st.hasMoreTokens()) {
                    pw.println(getStatus(st.nextToken()));
                    pw.flush();
                }
            } else if (requestType.equalsIgnoreCase("KILL")) {
                //Handle Kill Request
                while (st.hasMoreTokens()) {
                    kill(st.nextToken());
                }
            } else if (requestType.equalsIgnoreCase("NEW")) {
                //Handle New Room from Command Line
                if (st.countTokens() < 2) {
                    pw.println("Invalid request, missing arguments.  Please " +
                            "specify the room with a name, a path to a gamedef, " +
                            "and optionally a port to start the room on");
                    pw.flush();
                }
                String name = st.nextToken();
                String gamedefName = st.nextToken();
                Gamedef gd;
                int port;
                try {
                    gd = new Gamedef(st.nextToken());
                } catch (ParserConfigurationException ex) {
                    logError("Caught ParserConfigurationException while trying to parse gamedef" + gamedefName);
                    return;
                } catch (SAXParseException ex) {
                    logError("Caught SaxParserException while trying to parse gamedef" + gamedefName);
                    return;
                } catch (SAXException ex) {
                    logError("Caught SaxException while trying to parse gamedef" + gamedefName);
                    return;
                }
                if (st.countTokens() == 3) {
                    try {
                        port = new Integer(st.nextToken()).intValue();
                    } catch (NumberFormatException ex) {
                        port = new Random().nextInt(1000) + PORT;
                    }
                } else {
                    port = new Random().nextInt(1000) + PORT;
                }
                Room r = new Room(name, gd, port);
                rooms.add(r);
                pw.println("New room added successfully on port " + port);
                pw.flush();
            } else if (requestType.equalsIgnoreCase("CONFIG")) {
                //Handle New room from Config File
                parseConfigFile(st.nextToken(), "", 0, 0);
            } else if (requestType.equalsIgnoreCase("AUTOCONNECT")) {
                /* Auto create a Heads Up Limit 1 2 Game using the key given.  First
                 * check the key, if it is valid, create the game, if not, return
                 * invalid key message
                 */
                String key = st.nextToken().toUpperCase();
                if (keyList.contains(key)) {
                    pw.println("ERROR:Key already in use, Please try another key or " +
                            "logout where the first key is in use");
                    pw.flush();
                } else {
                    try {
                        autoConnect(key);
                    } catch (NullPointerException ex) {
                        logError("Invalid seed from key" + key);
                        pw.println("ERROR:Invalid key, Please check the key for " +
                                "errors and try again");
                        pw.flush();
                    } catch (ParserConfigurationException ex) {
                        logError("Could not parse keys.xml\n" + ex.toString());
                        pw.println("ERROR:Validation server down, please try again later");
                        pw.flush();
                        System.exit(-1);
                    } catch (SAXParseException ex) {
                        logError("Could not parse keys.xml\n" + ex.toString());
                        pw.println("ERROR:Validation server down, please try again later");
                        pw.flush();
                        System.exit(-1);
                    } catch (SAXException ex) {
                        logError("Could not parse keys.xml\n" + ex.toString());
                        pw.println("ERROR:Validation server down, please try again later");
                        pw.flush();
                        System.exit(-1);
                    }
                }
            } else if (requestType.equalsIgnoreCase("GETINFO")) {
                /* Monitor Script, return some stats to the python monitor */
                pw.println("Rooms in Use: " + rooms.size() + ": Keys In Use: " + keyList.size());
                pw.flush();
            } else {
                pw.println("Invalid request: " + requestType);
                pw.flush();
            }
        }

        /**
         * The auto connect routine for a key value and the online client
         * @param key
         */
        private void autoConnect(String key) throws NullPointerException,
                ParserConfigurationException, SAXParseException, SAXException,
                IOException {
            int seed;
            String response, config;
            response = validateKey(key);
            StringTokenizer st = new StringTokenizer(response, ":");
            pw.println("Username:" + st.nextToken());
            pw.flush();
            seed = new Integer(st.nextToken()).intValue();
            config = st.nextToken();
            int port = new Random().nextInt(1000) + PORT;
            while (portList.contains(port)) {
                port = new Random().nextInt(1000) + PORT;
            }
            portList.add(port);
            keyList.add(key);
            parseConfigFile("config/"+config, key, seed, port);
        }

        /**
         * Lookup a seed and username from a specific key value.  
         * Used for matching users to logs and seeds to recreate play
         * @param key a String used for the key value pair of the seed
         */
        private String validateKey(String key) throws NullPointerException,
                ParserConfigurationException, SAXParseException,
                SAXException, IOException {
            int seed = -1;
            String userName = "", config = "";
            XMLParser parser = parser = new XMLParser("keys/keys.xml");
            NodeList nl = parser.parseElements("Key");
            for (int i = 0; i < nl.getLength(); i++) {
                Node keyNode = nl.item(i);
                if (keyNode.getNodeType() == Node.ELEMENT_NODE && parser.getStringFromNode(keyNode, "KeyValue").equalsIgnoreCase(key)) {
                    userName = parser.getStringFromNode(keyNode, "UserName");
                    seed = parser.getIntFromNode(keyNode, "Seed");
                    config = parser.getStringFromNode(keyNode,"Config");
                }
            }
            if (seed == -1) {
                throw new NullPointerException("Key not found");
            }
            return userName + ":" + seed + ":" + config;
        }

        /**
         * Get the status for a given room name, or for all the rooms if ALL is
         * specified as the argument.  Rooms are delimited by ||
         * @param roomName A name or ALL for the specific status
         * @return The status of a given room or all the rooms
         */
        private String getStatus(String roomName) {
            String status = "";
            for (Room r : rooms) {
                if (roomName.equalsIgnoreCase("All") ||
                        r.getName().equalsIgnoreCase(roomName)) {
                    status += r.getStatus();
                }
                status += "||";
            }
            return status;
        }

        /**
         * Print all the Rooms for the server interface, just name and port and 
         * the num players / max
         * @return
         */
        private String getRooms() {
            String roomList = "";
            for (Room r : rooms) {
                roomList += r.toString() + "||";
            }
            return roomList;
        }

        /**
         * Parse the given config file.  Config files can have a number of options
         * and requests in them, see the sample CONFIG.SAMPLE for more information
         * on specific config file options
         * 
         * @param path A Path to a given XML config file
         * @param name A String for the name of the room, blank for config chosen
         * @param port specify a room port, 0 for config chosen
         * @param seed Specify a dealer seed, 0 if random
         */
        private void parseConfigFile(String path, String name, int seed, int port) {

            XMLValidator validator = new XMLValidator("xsd/config.xsd");
            try {
                if (!validator.validateXML(path)) {
                    logError("Invalid config file: " + path + ".\nCheck config.xsd to ensure the config file fits the schema");
                    return;
                }
            } catch (ParserConfigurationException ex) {
                logError("ParserConfigurationException for config file: " + path);
                return;
            } catch (SAXException ex) {
                logError("SaxException for config file: " + path);
                return;
            } catch (IOException ex) {
                logError("IOException for config file: " + path);
                return;
            }

            XMLParser parser;
            //Get the Room info           
            try {
                parser = new XMLParser(path);
            } catch (ParserConfigurationException ex) {
                logError("ParserConfigurationException for config file: " + path);
                return;
            } catch (SAXParseException ex) {
                logError("SaxParserException for config file: " + path);
                return;
            } catch (SAXException ex) {
                logError("SaxException for config file: " + path);
                return;
            } catch (IOException ex) {
                logError("IOException for config file: " + path);
                return;
            }
            NodeList nl = parser.parseElements("Room");
            for (int i = 0; i < nl.getLength(); i++) {
                Node roomNode = nl.item(i);
                if (roomNode.getNodeType() == Node.ELEMENT_NODE) {
                    if (name.equalsIgnoreCase("")) {
                        name = parser.getStringFromNode(roomNode, "Name");
                    }
                    String gamedefPath = parser.getStringFromNode(roomNode, "Gamedef");
                    validator.setSchema("xsd/gamedef.xsd");
                    try {
                        if (!validator.validateXML(gamedefPath)) {
                            System.out.println("Invalid gamedef file specified in the config.\nCheck gamedef.xsd to ensure the gamedef file fits the schema");
                            return;
                        }
                    } catch (ParserConfigurationException ex) {
                        logError("ParserConfigurationException for gamedef file: " + gamedefPath);
                        return;
                    } catch (SAXException ex) {
                        logError("SaxException for gamedef file: " + gamedefPath);
                        return;
                    } catch (IOException ex) {
                        logError("IOException for gamedef file: " + gamedefPath);
                        return;
                    }
                    Gamedef gamedef;
                    try {
                        gamedef = new Gamedef(gamedefPath);
                    } catch (ParserConfigurationException ex) {
                        logError("ParserConfigurationException for gamedef file: " + gamedefPath);
                        return;
                    } catch (SAXParseException ex) {
                        logError("SaxParserException for gamedef file: " + gamedefPath);
                        return;
                    } catch (SAXException ex) {
                        logError("SaxException for gamedef file: " + gamedefPath);
                        return;
                    } catch (IOException ex) {
                        logError("IOException for gamedef file: " + gamedefPath);
                        return;
                    }
                    if (port == 0) {
                        port = parser.getIntFromNode(roomNode, "Port");
                    }
                    try {
                        Room r;
                        if (seed != 0) {
                            r = new Room(name, gamedef, port, seed);
                        } else {
                            r = new Room(name, gamedef, port);
                        }
                        Thread t = new Thread(r);
                        t.start();
                        rooms.add(r);
                    } catch (IOException ex) {
                        logError("Caught IO Exception while trying to parse config file" +ex.toString());
                        logError(ex.getStackTrace().toString());                
                        return;
                    } catch (InterruptedException ex) {
                        System.out.println(ex.toString());
                        return;
                    }
                }
            }
            //Get the BotList info            
            BotManager bm = new BotManager(port);
            nl = parser.parseElements("Bot");
            for (int i = 0; i < nl.getLength(); i++) {
                Node botNode = nl.item(i);
                if (botNode.getNodeType() == Node.ELEMENT_NODE) {
                    String type = parser.getStringFromNode(botNode, "Type");
                    String botName = parser.getStringFromNode(botNode, "Name");
                    int buyIn = parser.getIntFromNode(botNode, "BuyIn");
                    int seat = parser.getIntFromNode(botNode, "Seat");
                    String args = parser.getStringFromNode(botNode, "Args");
                    bm.addBot(type + ":" + botName + ":" + buyIn + ":" + seat + ":" + args);
                }
            }
            bm.startBots();
            pw.println("New room started on port:" + port);
            pw.flush();
        }

        /**
         * Shudown a specific room or all of the rooms if the ALL argument is 
         * passed in as the roomName
         * @param roomName The name of the room to kill
         */
        private void kill(String roomName) throws IOException {
            for (Room r : rooms) {
                if (roomName.equalsIgnoreCase("ALL") ||
                        r.getName().equalsIgnoreCase(roomName)) {
                    r.shutdown();
                    pw.println("Room " + roomName + " shutting down");
                    pw.flush();
                }
            }
            if (roomName.equalsIgnoreCase("ALL")) {
                killServer();
            }
        }

        /**
         * Closes the current connection and sets the alive boolean to false 
         * telling the server to stop listening for incoming connections and to 
         * exit
         * 
         * @throws java.io.IOException
         */
        private void killServer() throws IOException {
            pw.println("Server shutting down");
            pw.flush();
            alive = false;
        }
    }

    /**
     * Start the server from the command line.  Can also be started via the class
     * @param args Command line args
     */
    public static void main(String[] args) {
        try {
            Server instance = new Server();
            Thread t = new Thread(instance);
            t.start();
            while (t.isAlive()) {
                Thread.sleep(100000);
            }
        } catch (InterruptedException ex) {                        
            System.err.println("Server thread caught Interrupted Exception in main "+ex.toString());
            System.err.println(ex.getStackTrace().toString());                
        } catch (BindException ex) {
            System.err.println("Could not bind server port");            
            System.err.println(ex.getStackTrace().toString());                
        } catch (IOException ex) {
            System.err.println("Server thread caught IO Exception in main "+ex.toString());
            System.err.println(ex.getStackTrace().toString());                
        }
    }
}
