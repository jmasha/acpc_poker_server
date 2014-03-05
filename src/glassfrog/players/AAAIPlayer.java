package glassfrog.players;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Random;

/**
 * A player used to connect the AAAI bots to the server.  
 * This class acts as an interface for the bots to connect into the code.
 * They are invoked by a BotManager and thier parameters are specified in a 
 * config file that the server parses.  This is to get around setting things like
 * the name, seat and buyin from the bot's perspective, but rather the config file
 * handles all of that information and simple adds the AAAIPlayer to the room and
 * the AAAIPlayer opens a port to the bot through which the Room is able to send
 * and recieve messages
 * 
 * @author jdavidso
 */
public class AAAIPlayer extends Player implements Runnable {

    private transient ServerSocket ss;
    private transient Socket socket;
    private transient PrintWriter pw;
    private transient BufferedReader br;
    private transient int port;
    private transient String scriptPath,  logPrefix;
    private transient final int TIMEOUT = 1800000;

    /**
     * Invoke the super constructor, set up the server socket, get the port, and
     * then run the script associated with the bot
     * @param name The players name
     * @param buyIn The buyIn amount to play the game
     * @param seat The preffered seat of the player to start the game
     * @param portBase The portBase used by the server
     * @param scriptPath A path to the shell script
     * @param logPrefix A string telling the bot what to append to the out and error logs
     */
    public AAAIPlayer(String name, int buyIn, int seat, int portBase,
            String scriptPath, String logPrefix) throws IOException {
        super(name, buyIn, seat);
        this.scriptPath = scriptPath;
        this.logPrefix = logPrefix;
        for (int i = 0; i < 3; i++) {
            port = new Random().nextInt(1000) + portBase;
            try {
                ss = new ServerSocket(port);
                ss.setSoTimeout(TIMEOUT);
                Thread t = new Thread(this);
                t.start();
                socket = ss.accept();
                pw = new PrintWriter(socket.getOutputStream());
                br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                if (!br.readLine().equalsIgnoreCase("Version:1.0.0")) {
                    throw new IOException("Incorrect protocol version");
                }
                return;
            } catch (BindException ex) {
                continue;
            } catch (SocketTimeoutException ex) {
                System.err.println("AAAIPPlayer " + getName() + " hit timeout");
                throw new IOException("Socket Timeout");
            }
        }
        throw new IOException("Could not bind AAAI player to socket");
    }

    /**
     * Gets the action of the player through the BufferedReader
     * @return the action sent or "f" on error
     */
    @Override
    public String getAction() {
        try {
            return br.readLine();
        } catch (IOException ex) {
            System.err.println("AAAIPlayer " + getName() + " hit IOException during read, folding hand");
            throw new NullPointerException("Player Disconnected");
        //return "f";
        }
    }

    /**
     * Send the gamestate to the player through the PrintWriter
     * @param gamestate The gamestate to send
     */
    @Override
    public void update(String gamestate) {
        pw.println(gamestate);
        pw.flush();
    }

    /**
     * Get the port the player is associated with
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Runs the script associated with this player.  It will wait 5s to allow
     * time for the ServerSocket to start accepting connections
     */
    public void run() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
            System.err.println("AAAI Player Thread interuppted\n");
        }
        String command = "";
        try {
            command = scriptPath + " " + InetAddress.getLocalHost().getHostAddress() + " " + port;
            System.out.println("Executing bot command: " + command);
            FileOutputStream normalOut = new FileOutputStream("output/" + logPrefix + ":" + getName() + ".out");
            FileOutputStream errOut = new FileOutputStream("output/" + logPrefix + ":" + getName() + ".err");

            Process p = Runtime.getRuntime().exec(command);
            StreamConnect sc = new StreamConnect(p.getInputStream(), normalOut);
            Thread tsc = new Thread(sc);
            tsc.start();

            StreamConnect scerr = new StreamConnect(p.getErrorStream(), errOut);
            Thread tscerr = new Thread(scerr);
            tscerr.start();

        } catch (UnknownHostException ex) {
            System.err.println("Count not reach host:" + ex.toString());
        } catch (IOException io) {
            System.err.println("I/O Exception executing a local command: " + command);
        }
    }

    /**
     * Overrides the @Player call to this method and returns True
     * @return True
     */
    @Override
    public boolean isAAAIPlayer() {
        return true;
    }

    /**
     * Used to reconnect a player upon dealer reloads.  This takes a socket
     * connection and re-initializes the @PrintWriter and @BufferedReader
     * @param s a @Socket the AAAI player is connecting on
     * @throws java.io.IOException
     */
    public void reconnect(Socket s) throws IOException {
        this.socket = s;
        br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        pw = new PrintWriter(socket.getOutputStream());
    }

    /**
     * Get the @Socket the player is connected on
     * @return the @Socket the player is connected on
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Override's the @Player implementation of this method and closes the @Socket
     * the @PrintWriter and the @BufferedReader down
     */
    @Override
    public void shutdown() {
        try {
            br.close();
            pw.close();
            socket.close();
        } catch (IOException ex) {
            System.err.println("Error while trying to close the socket for the player " + getName());
        }
    }

    /**
     * Returns AAAI appended to the @Player representation of the object
     * @return AAAI appended to the front of the @Player toString method
     */
    @Override
    public String toString() {
        return "AAAI" + super.toString();
    }
}
