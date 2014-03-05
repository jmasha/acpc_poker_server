package glassfrog.players;

import java.io.IOException;
import java.net.Socket;

/**
 * This class allows for the packaged Swordfish GUI to be connected as a Player.
 * At this time, the GUI only allows for graphical representation of 2 Players, 
 * and thus should only be used for 2 Player games.
 * @author jdavidso
 */
public class GUIPlayer extends SocketPlayer {    

    /**
     * Constructor for a GUI Player
     * @param name a @String representing the player's name
     * @param buyIn an int representing the requested buyIn amount
     * @param seat an int representing the requested seat
     * @param port an int representing the port for the GUI to connect to
     * @throws java.io.IOException
     */
    public GUIPlayer(String name, int buyIn, int seat, int port) throws IOException {
        super(name,buyIn,seat,port);
    }        
    
    public GUIPlayer(String name, int buyIn, int seat, Socket socket) throws IOException {
        super(name, buyIn, seat, socket);
    }
    
    /**
     * Overrides the @Player isGuiPlayer method and returns True
     * @return True
     */
    @Override
    public boolean isGuiPlayer() {
        return true;
    }
    
    /**
     * Returns GUI appended to the @Player representation of the object
     * @return GUI appended to the front of the @Player toString method
     */
    @Override
    public String toString() {
        return "GUI"+super.toString();
    }
}
