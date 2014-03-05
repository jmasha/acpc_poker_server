package glassfrog.server;

import glassfrog.players.Player;
import glassfrog.players.SocketPlayer;
import glassfrog.model.Dealer;
import glassfrog.model.Gamedef;
import glassfrog.players.AAAIPlayer;
import glassfrog.players.GUIPlayer;
import glassfrog.tools.MatchRebuilder;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**The Room class is the class that will handle all of the game code.  The rooms
 * consist of Players and a Dealer.  The room is responsible for handleing all 
 * the connections to the Players, The room takes connections until it is "full"
 * then it will start the game with the given specifications.  After the game is 
 * started, the dealer will then handle all of the game logic, and the Room will
 * be responsible for message passing to the players.  Essentially, the Dealer
 * handles the gamestates, and then the room is responsible for sending an action
 * request to the proper player.  The response is then sent back to the Dealer.
 * Once the game is over, the room will be shutdown, or restarted, depending on 
 * specifications of the room.
 *
 * @author jdavidso
 */
public class Room implements Runnable {

    private String name,  matchLog,  errorLog,  matchLogger,  errorLogger;
    private Gamedef gamedef;
    private Dealer dealer;
    private ServerSocket serverSocket;
    private LinkedList<Player> players = new LinkedList<Player>();
    private int playerCount;
    private int port;
    private int seed;
    private String key;
    private boolean runOnce = true;
    private boolean alive = true;
    private FileHandler errorFileHandler,  matchFileHandler;
    private static final int MAX_CONNECTION_ATTEMPTS = 3;
    private static final int CONNECTION_TIMEOUT = 60000;
    private static final int SOCKET_TIMEOUT = 1000;

    /**
     * Start a Room with a name, Gamedef and a port
     * @param name a String representing the name of the Room
     * @param gamedef a @Gamedef object holding all the information about the game
     * this room will play
     * @param port an int representing the port this room will be listening for 
     * connections on
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    public Room(String name, Gamedef gamedef, int port) throws IOException,
            InterruptedException {
        this.name = name;
        this.gamedef = gamedef;
        this.port = port;
        this.seed = 0;
        System.out.println("Starting Room: " + name);
        initLogging();
        initServerSocket();
    }

    /**
     * Start a Room with a name, Gamedef, a port and a random seed for the cards
     * @param name a String representing the name of the Room
     * @param gamedef a @Gamedef object holding all the information about the game
     * this room will play
     * @param port an int representing the port this room will be listening for 
     * connections on
     * @param seed an int represending a seed that will be used for dealing the cards
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    public Room(String name, Gamedef gamedef, int port, int seed) throws IOException,
            InterruptedException {
        this.name = name;
        this.gamedef = gamedef;
        this.port = port;
        this.seed = seed;
        System.out.println("Starting Room: " + name);
        initLogging();
        initServerSocket();
    }

    /**
     * Start a Room with a name, Gamedef, a port and a random seed for the cards as
     * well as a key for re-creating the room
     * @param name a String representing the name of the Room
     * @param gamedef a @Gamedef object holding all the information about the game
     * this room will play
     * @param port an int representing the port this room will be listening for 
     * connections on
     * @param seed an int represending a seed that will be used for dealing the cards
     * @param key a String representing a key the room uses for saving and reconnecting
     * @throws java.io.IOException
     * @throws java.lang.InterruptedException
     */
    public Room(String name, Gamedef gamedef, int port, int seed, String key)
            throws IOException, InterruptedException {
        this.name = name;
        this.gamedef = gamedef;
        this.port = port;
        this.seed = seed;
        this.key = key;
        System.out.println("Starting Room: " + name);
        initLogging();
        initServerSocket();
    }

    /**
     * Initialize the logs for the game.
     * The logname will be of the form roomname_timestamp.(log || .err) where 
     * roomname is the name passed from the server to the room and the timestamp      
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
        matchLog = logPath + name + ".room.log";
        matchLogger = name + ".roomlogger";
        errorLog = logPath + name + ".room.err";
        errorLogger = name + ".errorlogger";
        try {
            errorFileHandler = new FileHandler(errorLog, true);
            errorFileHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger(errorLogger).addHandler(errorFileHandler);
            matchFileHandler = new FileHandler(matchLog, true);
            matchFileHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger(matchLogger).addHandler(matchFileHandler);
        } catch (IOException ex) {
            System.err.println("Could not initialize logs, exit with IO Error " + ex.toString());
        } catch (SecurityException ex) {
            System.err.println("Could not initialize logs, exit with Secutirty Error " + ex.toString());
        }
    }

     /**
     * Utility for logging an error message to the errorLogger
     * @param ex An exception
     */
    public void logError(Exception ex) {
        Logger.getLogger(errorLogger).log(Level.SEVERE, ex.toString());        
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));        
        Logger.getLogger(errorLogger).log(Level.SEVERE, sw.toString());        
    }

    /**
     * Utility for logging a warning message to the errorLogger
     * @param warningMessage A message to log to the error log
     */
    public void logWarning(String warningMessage) {
        Logger.getLogger(errorLogger).log(Level.WARNING, warningMessage);
    }

    /**
     * Log a gamesate to the roomLogger
     * @param info the message to log to the room log 
     * @param toOut True to print to stdout
     */
    public void logInfo(String info, boolean toOut) {
        Logger.getLogger(matchLogger).log(Level.INFO, info);
        if(toOut) {
            System.out.println(info);
        }
    }

    /**
     * Set up the server socket. If we can't bind to the port specified by the
     * server, we wait 10s, and try again for a maximum of 3 attempts. After that
     * we throw an exception, which will get passed back to the server
     * @throws java.io.IOException
     */
    private void initServerSocket() throws IOException, InterruptedException {
        int attempts = 0;
        while (attempts++ < MAX_CONNECTION_ATTEMPTS) {
            serverSocket = new ServerSocket(port);
            if (serverSocket.isBound()) {
                return;
            } else {
                Thread.sleep(CONNECTION_TIMEOUT);
            }
        }
        throw new SocketException("Could not bind to an open port in the port range");
    }

    /**
     * Listen for incoming connections.  Create a thread to handle the connection
     * which will figure out if the connection request is a the dealer, server or 
     * player then do the appropriate operations.
     * When handling connections, if we are waiting to start the game, then 
     * this method will create and execute a dealer thread to be run in the game
     * This thread will check for new connections every 3s
     * 
     */
    private void listen() throws IOException {
        logInfo("Room " + name + " listening for incoming connections on port: " + port, true);
        serverSocket.setSoTimeout(SOCKET_TIMEOUT);
        Thread dealerThread = null;
        while (alive) {
            try {
                Socket socket = serverSocket.accept();
                Thread t = new Thread(new RoomConnectionHandler(socket));
                t.start();
            } catch (SocketTimeoutException ex) {
                if (playerCount == gamedef.getMinPlayers() && dealerThread == null) {
                    try {
                        String filename = "save/" + name + ".dealer.ser";
                        FileInputStream fis = new FileInputStream(filename);
                        ObjectInputStream in = new ObjectInputStream(fis);
                        logInfo("Loading a dealer for Room:" + name, true);
                        dealer = (Dealer) in.readObject();
                        in.close();
                        if(!dealer.reconnectPlayers(players)) {
                            logInfo("Could not reconnect players, shutting down room", true);                                                        
                            return;
                        }                        
                    } catch (ClassNotFoundException ex1) {
                        logError(ex1);
                        logInfo("Room:" + name + " could not load dealer from save, attempting logfile restore...", true);                        
                        startNewDealer();
                    } catch (InvalidClassException ex1) {
                        logError(ex1);
                        logInfo("Room:" + name + " could not load dealer from save, attempting logfile restore...", true);
                        startNewDealer();
                    } catch (FileNotFoundException ex1) {
                        startNewDealer();
                        try {
                            logInfo("Room:" + name + " checking for restore point", true);
                            dealer = MatchRebuilder.restore(name);
                            if(!dealer.reconnectPlayers(players)) {
                                logInfo("Could not reconnect players, shutting down room", true);                                                        
                                return;
                            }
                        } catch (FileNotFoundException ex2) {
                            //Could not restore a dealer, default to new.
                            logInfo("Room:" + name + " could not find restore point", true);
                            logInfo("Room:" + name + " starting a new dealer", true);
                        } catch (ClassNotFoundException ex2) {
                            logInfo("Room:" + name + " error restoring dealer", true);                            
                            logInfo("Room:" + name + " starting a new dealer", true);
                        }                                                
                    }
                    dealerThread = new Thread(dealer);
                    dealerThread.start();
                }
                if (dealerThread != null) {                    
                    if (dealer.isDisconnected() || dealer.isGameOver()) {
                        saveDealer();
                        String gameStatus = getStatus();
                        logInfo(gameStatus, true);                        
                        if (runOnce) {
                            shutdown();
                        }
                    }
                }
            }
        }
    }

    /**
     * Load up a new dealer and save it for the first time.
     */
    private void startNewDealer() {
        if (seed != 0) {
            dealer = new Dealer(gamedef, players, seed, name);
        } else {
            dealer = new Dealer(gamedef, players);
        }
        saveDealer();
    }

    /**
     * Save the instance of the dealer to file for reloading of the game
     */
    private void saveDealer() {
        try {
            String filename = "save/" + name + ".dealer.ser";
            FileOutputStream fos = new FileOutputStream(filename);
            ObjectOutputStream out = new ObjectOutputStream(fos);
            out.writeObject(dealer);
            out.close();
        } catch (IOException ex) {
            logError(ex);
        }
    }

    /**
     * Calls the listen method to wait for connections.  Listen will only end
     * in one of two situations.
     * 1) The server tells the room to shutdown
     * 2) The room has finished playing a game and the constructor or server set
     * the runOnce flag for the room.  The runOnce flag tells the room to only 
     * execute one run of the dealer, and once that ends (either in a disconnect
     * or a gameOver) then the room will shut down
     */
    public void run() {
        try {
            listen();
        } catch (IOException ex) {
            logError(ex);
            alive = false;
        }
    }

    /**
     * Return the current status of the room.  The current hand, players, stats, 
     * and some vague information about the game
     * 
     * @return The room's current status
     */
    public String getStatus() {
        String status = "ROOM:" + name + ":" + port + "\n";
        status += dealer.getStats();
        return status;
    }

    /**
     * Return the key associated with the room if it exits, else return "None"
     * @return A String representation of the key the room uses
     */
    public String getKey() {
        if (key != null) {
            return key;
        }
        return "None";
    }

    /**
     * Getter for the room's name
     * @return The room name
     */
    public String getName() {
        return name;
    }

    /**
     * Check to see if the room is still alive.  This is for shutting down the
     * rooms from the server
     * @return The alive status of the room
     */
    public boolean isAlive() {
        return alive;
    }

    /**
     * Shutdown a room via request from the server or game is over
     */
    public void shutdown() {
        alive = false;
        try {
            serverSocket.close();
            errorFileHandler.close();
            matchFileHandler.close();
            for (Player p : players) {
                p.shutdown();
            }
        } catch (IOException ex) {
            logError(ex);
        }
    }

    /**
     * Return a human readable, parser friendly : delimited representation of the
     * Room's info
     * @return A String containing all the info relevant to the Room
     */
    @Override
    public String toString() {
        String roomString = "ROOM:Name:" + name + ":PORT:" + port + ":PLAYERS:" +
                playerCount + "/" + gamedef.getMaxPlayers();
        return roomString;
    }

    /**
     * An Inner Class used to handle incoming connections to the room.
     * These connections should be player connections, and each type of player
     * will have thier own way to connect depending on the type of player.
     * 
     */
    private class RoomConnectionHandler implements Runnable {

        private String connectionArgs;
        private Socket socket;
        private PrintWriter pw;
        private BufferedReader br;

        /**
         * The constructor for the ConnectionHandler inner class.  The connection
         * handler takes a socket passed in from the room, opens a print writer 
         * and buffered reader on the socket and then parses the first argument
         * sent by the connection.  The connections should be players connections
         * or commands send by the server
         * 
         * @param socket A socket passed in to handle the connection in and outputs
         * 
         */
        public RoomConnectionHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.socket.setSoTimeout(SOCKET_TIMEOUT);
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            pw = new PrintWriter(socket.getOutputStream());

        }

        /**
         * This method handles the request sent in on the socket.  
         * If the request is of the player type, then handlePlayerRequest is called.  
         * If the request is of the server type, then the handleServerRequest is
         * called.
         * 
         */
        public void run() {
            while (socket.isConnected()) {
                try {
                    connectionArgs = br.readLine();
                    StringTokenizer st = new StringTokenizer(connectionArgs, ":");
                    if (st.hasMoreTokens()) {
                        String type = st.nextToken();
                        if (type.equalsIgnoreCase("SocketPlayer")) {
                            SocketPlayer p;
                            p = new SocketPlayer(st.nextToken(),
                                    new Integer(st.nextToken()).intValue(),
                                    new Integer(st.nextToken()).intValue(), br, pw);
                            addPlayer(p);
                        } else if (type.equalsIgnoreCase("GUIPlayer")) {
                            GUIPlayer p;
                            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                            oos.writeObject(gamedef);
                            oos.flush();                            
                            int playerPort;          
                            ServerSocket ss;
                            Socket playerSocket;
                            for(int i = 0; i< 3; i++) {
                                playerPort  = new Random().nextInt(1000) + port;                                
                                try{
                                    ss = new ServerSocket(playerPort);
                                    pw.println("Listening on port:" + playerPort);
                                    pw.flush();
                                    playerSocket = ss.accept();
                                    p = new GUIPlayer(st.nextToken(),
                                    new Integer(st.nextToken()).intValue(),
                                    new Integer(st.nextToken()).intValue(), playerSocket);                                                                        
                                    addPlayer(p);
                                    logInfo("Room " + name + " added new player " + p.toString(), true);
                                    return;
                                } catch (BindException ex) {
                                    continue;
                                }                                                                
                            }
                            pw.println("ERROR:Could not connect to the server, the server may be full\n.Please try again later.");
                            pw.flush();
                            logError(new BindException("Could not bind a player port"));
                            socket.close();
                            alive = false;
                        } else if (type.equalsIgnoreCase("AAAIPlayer")) {
                            AAAIPlayer p;
                            try {
                                p = new AAAIPlayer(st.nextToken(),
                                    new Integer(st.nextToken()).intValue(),
                                    new Integer(st.nextToken()).intValue(),
                                    port, st.nextToken(), name);
                                    addPlayer(p);
                                    logInfo("Room " + name + " added new player " + p.toString(), true);
                            } catch(IOException ex) {
                                logError(ex);
                                socket.close();
                                alive = false;
                            }
                        } else if (type.equalsIgnoreCase("Status")) {
                            getStatus();
                        } else {
                            logWarning("Unknown request: " + type);
                        }
                    }
                } catch (SocketTimeoutException ex) {
                    try {
                        socket.close();
                        return;
                    } catch (IOException ex1) {
                        logError(ex1);
                        return;
                    }
                } catch (IOException ex) {
                    logError(ex);
                    return;
                } catch (NullPointerException ex) {
                    logError(ex);
                    return;
                }
            }
        }

        /**
         * Check to see if the name and the seat are already assigned to a player
         * if so, use the next open seat or assign a random int between 1 and 10
         * to the player name
         * 
         * @param newPlayer The player to ba added to the players list
         */
        private void addPlayer(Player newPlayer) {
            for (Player p : players) {
                if (p.getName().equalsIgnoreCase(newPlayer.getName())) {
                    newPlayer.setName(newPlayer.getName() + new Random().nextInt(10));
                    addPlayer(newPlayer);
                    return;
                }

                if (p.getSeat() == newPlayer.getSeat()) {
                    newPlayer.setSeat(new Random().nextInt(players.size() + 1));
                    addPlayer(newPlayer);
                    return;
                }
            }
            //If we are using Doyles Game, ignore the buyin
            if(gamedef.isDoylesGame()) {
                newPlayer.setBuyIn(gamedef.getStackSize());
            }
            players.add(newPlayer);
            playerCount++;
        }
    }
}