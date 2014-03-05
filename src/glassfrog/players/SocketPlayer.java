package glassfrog.players;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The SocketPlayer class allows for players to connect to the game via socket.
 * A Socket must be passed in to the constructor, which then sets up the PrintWriter
 * and BufferedReader from which the actions are sent and recieved from agents
 * 
 * @author jdavidso
 */
public class SocketPlayer extends Player{
    protected transient ServerSocket ss;
    protected transient Socket socket;
    protected transient BufferedReader br;
    protected transient PrintWriter pw;
    
    /**
     * Empty default contructor for extendability
     */
    public SocketPlayer() {
        
    }
    
    /**
     * A contructor for Socket Player that takes a name, buyIn, a seat request and
     * a port for connection
     * @param name a @String representing the name of the player
     * @param buyIn an int representing the requested buyIn amount
     * @param seat an int representing the requested seat
     * @param port an int representing a port to establish a connection on
     */
    public SocketPlayer(String name, int buyIn, int seat, int port) {
        super(name,buyIn,seat);
        try {
            ss = new ServerSocket(port);
            socket = ss.accept();
            System.out.println("Socket Player connected on port: "+port);
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            pw = new PrintWriter(socket.getOutputStream());        
            if(!br.readLine().equalsIgnoreCase("Version:1.0.0")){
                throw new IOException("Invalid version");
            }                
        } catch (IOException ex) {
            System.err.println("Socket Player "+getName()+" hit IOException:"+ex.toString());
        }
    }
            
    /**
     * The SocketPlayer constructor that takes a port and opens a socket connection
     * to handle action requests and updates to the player over a socket
     * 
     * @param name A string representing the player's name
     * @param buyIn an int representing the player's buyIn
     * @param seat an int representing the seat the player is sitting in
     * @param socket a socket to which the player is connected to an agent
     */
    public SocketPlayer(String name, int buyIn, int seat, Socket socket) throws IOException {
        super(name,buyIn,seat);
        this.socket = socket;
        initPlayer();
    }

    /**
     * A contructror for SocketPlayer that takes a @BufferedReader and a @PrintWriter
     * and established the player based on the pre-establised in and out.
     * @param name a @String representing the name of the player
     * @param buyIn an int representing the requested buyIn amount
     * @param seat an int representing the requested seat     
     * @param br A @BufferedReader the player will recieve gamestate information on
     * @param pw A @APrintWriter the player will print action to
     * @throws java.io.IOException
     */
    public SocketPlayer(String name, int buyIn, int seat, BufferedReader br, 
            PrintWriter pw) throws IOException {
        super(name,buyIn,seat);
        this.br = br;
        this.pw = pw;
        if(br.readLine().equalsIgnoreCase("Version:1.0.0")) {
            return;
        } else {
            throw new IOException("Player has wrong version");
        }
        
    }
    
    /**
     * Returns the port the agent is connected to the player on
     * @return an int representing the port number the agent is connected on
     */
    public int getPort() {
        return socket.getPort();
    }
    
    /**
     * Gets the action of the player through the BufferedReader
     * @return the action sent or throw a NullPointerException on disconnect
     */
    @Override
    public String getAction(){
        try {
            return br.readLine();
        } catch (IOException ex) {
            System.err.println("SocketPlayer "+getName()+" hit IOException during read, folding hand");  
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
     * Set up the Players reader and writer.
     * @throws java.io.IOException
     */
    private void initPlayer() throws IOException{               
        br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        pw = new PrintWriter(socket.getOutputStream());        
         if(br.readLine().equalsIgnoreCase("Version:1.0.0")) {
            return;
        } else {
            throw new IOException("Player has wrong version");
        }         
    }
    
    /**
     * Overrides the @Player implementation of isSocketPlayer and returns True
     * @return True
     */ 
    @Override
    public boolean isSocketPlayer() {
        return true;
    }
    
    /**
     * The reconnect method allows for a player to be reconnected on @Dealer load
     * @param s The @Socket for the player to be reconnected on
     * @throws java.io.IOException
     */
    public void reconnect(Socket s) throws IOException {
        this.socket = s;
        br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        pw = new PrintWriter(socket.getOutputStream());
    }
    
    /**
     * Return the @Socket the player is using
     * @return the @Socket the player is currently using
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Closes down all sockets and I/O on shutdown
     */
    @Override
    public void shutdown() {
        try {
            br.close();
            pw.close();
            socket.close();
        } catch (IOException ex) {
            System.err.println("Error while trying to close the socket for the player "+getName());
        }
    }    
}
