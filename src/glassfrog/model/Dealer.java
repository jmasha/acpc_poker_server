package glassfrog.model;

import glassfrog.players.HandRankComparator;
import glassfrog.players.Player;
import glassfrog.players.PositionComparator;
import glassfrog.players.PotCommitedComparator;
import glassfrog.handevaluator.EvaluateHand;
import glassfrog.handevaluator.HandEvaluator;
import glassfrog.players.AAAIPlayer;
import glassfrog.players.SeatComparator;
import glassfrog.players.SocketPlayer;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedList;
import java.util.StringTokenizer;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * The Dealer class will handle all of the methods of dealing cards, evaluating
 * hands, pots, and betting actions. There should be one Dealer per Room, and
 * each dealer is assigned a list of Players and a Gamedef to which they will 
 * use to play.
 * 
 * Dealers may also be assigned a Room in which they can report game outcomes and
 * similar information.  The Room may also be used to handle the player requests
 * and actions, although I am not sure that will be the case
 * @author jdavidso
 */
public class Dealer implements Runnable, Serializable {

    private Gamedef gamedef;
    private GameState gamestate;
    private Hand currentHand;
    private LinkedList<Player> players;
    private Deck deck;
    private int currentPlayer;
    private int handsPlayed;
    private String lastAction,  matchLog,  matchLogger,  errorLog,  errorLogger,  name;
    private boolean gameOver = false;
    private boolean hasReported = false;
    private boolean shuffle = true;        
    private transient FileHandler errorFileHandler,  matchFileHandler;
    private transient FileWriter rawLog, divatLog;
    private transient BufferedWriter rawLogWriter, divatLogWriter;
    private boolean disconnected = false;

    /**
     * Default constructor. Don't use
     */
    public Dealer() {
        gamedef = new Gamedef();
        currentHand = new Hand();
        players = new LinkedList<Player>();
    }

    /**
     * Dealer constructor that doesn't take a seed.  This constructor will assign
     * a seed using a SecureRandom generated int. The dealer will create a deck
     * with this seed.
     * 
     * @param gamedef The Gamedef that will be used to play
     * @param players A list of Players that will be seated in the game.     
     */
    public Dealer(Gamedef gamedef, LinkedList<Player> players) {
        this.gamedef = gamedef;
        this.players = players;
        int seed = new SecureRandom().nextInt();
        deck = new Deck(seed);
        handsPlayed = 0;
        for (Player p : players) {
            p.setPosition(p.getSeat());
        }
    }

    /**
     * Dealer constructor that takes a seed and uses that to create the deck.
     * This allows for reconstructable games
     * 
     * @param gamedef The Gamedef that will be used to play
     * @param players A list of Players that will be seated in the game.     
     * @param seed An integer representing the seed in which to seed the deck RNG
     */
    public Dealer(Gamedef gamedef, LinkedList<Player> players, int seed) {
        this.gamedef = gamedef;
        this.players = players;
        deck = new Deck(seed);
        handsPlayed = 0;
        for (Player p : players) {
            p.setPosition(p.getSeat());
        }
    }

    /**
     * Dealer constructor that takes a seed and uses that to create the deck.
     * This allows for reconstructable games
     * 
     * @param gamedef The Gamedef that will be used to play
     * @param players A list of Players that will be seated in the game.     
     * @param seed An integer representing the seed in which to seed the deck RNG
     * @param name A String to represent the name of the game for the log file
     */
    public Dealer(Gamedef gamedef, LinkedList<Player> players, int seed, String name) {
        this.gamedef = gamedef;
        this.players = players;
        this.name = name;
        deck = new Deck(seed);
        handsPlayed = 0;
        for (Player p : players) {
            p.setPosition(p.getSeat());
        }
    }

    /**
     * Set the players of the game.  Used by the room to set the players up.
     * @param players LinkedList containing player objects for the game
     */
    public boolean reconnectPlayers(LinkedList<Player> players) {
        for (Player p : this.players) {
            if (p.isSocketPlayer()) {
                for (Player p1 : players) {
                    if (p1.isSocketPlayer()) {
                        if (p.getName().equalsIgnoreCase(p1.getName())) {
                            try {
                                ((SocketPlayer) p).reconnect(((SocketPlayer) p1).getSocket());
                            } catch (IOException ex) {
                                logError(ex);
                                return false;
                            } catch (NullPointerException ex) {
                                logError(ex);
                                return false;
                            }
                        }
                    }
                }
            } else if (p.isAAAIPlayer()) {
                for (Player p1 : players) {
                    if (p1.isAAAIPlayer()) {
                        if (p.getName().equalsIgnoreCase(p1.getName())) {
                            try {
                                ((AAAIPlayer) p).reconnect(((AAAIPlayer) p1).getSocket());
                            } catch (IOException ex) {
                                logError(ex);
                                return false;
                            } catch (NullPointerException ex) {
                                logError(ex);
                                return false;
                            }
                        }
                    }
                }
            }
        }
        return true;
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
        if (name == null) {
            name = "";
            for (Player p : players) {
                name += p.getName() + ":";
            }
            name = name.substring(0, name.length() - 1);
        }

        matchLog = logPath + name + ".dealer.log";
        matchLogger = "matchlogger";
        errorLog = logPath + name + ".dealer.err";
        errorLogger = "errorlogger";
        try {
            errorFileHandler = new FileHandler(errorLog, true);
            errorFileHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger(errorLogger).addHandler(errorFileHandler);
            matchFileHandler = new FileHandler(matchLog, true);
            matchFileHandler.setFormatter(new SimpleFormatter());
            Logger.getLogger(matchLogger).addHandler(matchFileHandler);
            rawLog = new FileWriter(logPath + name + ".dealer.rawlog", true);
            rawLogWriter = new BufferedWriter(rawLog);
            divatLog = new FileWriter(logPath + name + ".dealer.divatlog", true);
            divatLogWriter = new BufferedWriter(divatLog);
        } catch (IOException ex) {
            System.err.println("Could not initialize dealer logs for " + name + ", exit with IO Error " + ex.toString());
        } catch (SecurityException ex) {
            System.err.println("Could not initialize dealer logs for " + name + ", exit with Security Error " + ex.toString());
        }
    }

    /**
     * Utility for logging an error message to the errorLogger
     * @param errorMessage A message to log to the errror log
     */
    public void logError(Exception ex) {
        Logger.getLogger(errorLogger).log(Level.SEVERE, ex.toString());
        Logger.getLogger(errorLogger).log(Level.SEVERE, ex.getStackTrace().toString());
    }

    /**
     * Utility for logging a warning message to the errorLogger
     * @param warningMessage A message to log to the error log
     */
    public void logWarning(String warningMessage) {
        Logger.getLogger(errorLogger).log(Level.WARNING, warningMessage);
    }

    /**
     * Log a gamesate to the matchlogger
     * @param matchstate the gamestate message to log to the match log 
     */
    public void logState(String matchstate) {
        try {
            rawLogWriter.write(matchstate + "\n");
            rawLogWriter.flush();
        } catch (IOException ex) {
            logError(ex);
        }
        Logger.getLogger(matchLogger).log(Level.INFO, matchstate);
    }
    
    /**
     * Log the game stats
     */
    private void logStats() {
        String stats = "";        
        stats += "STATS:Current Player:" + currentPlayer + ":Hands Played:" + handsPlayed + "\n";
        for (Player p : players) {
            stats += p.toString() + "\n";
        }
        logState(stats);
    }
    
    /** 
     * Log a divat readable gamestate into a divat log.  Note: Only implemented for 
     * heads up 2 player.
     * 
     * @param value The value of the game for the first player
     */
    private void logDivat() {
        String divatLine = handsPlayed+":";
        for(Player p : players) {
            divatLine += p.getName()+",";
        }
        divatLine = divatLine.substring(0, divatLine.length()-1);
        divatLine += ":"+0;
        divatLine += gamestate.getActionString()+":";                
        for(int r=0; r<= gamestate.getRound(); r++) {
            String privateCards = "";
            for(Player p : players) {
                privateCards = currentHand.getPrivateCardsString(p.getPosition(),r);
                if(!privateCards.isEmpty()) {
                    divatLine += privateCards+",";
                }                
            }                          
            if(!privateCards.isEmpty()) {
                divatLine = divatLine.substring(0, divatLine.length()-1);            
                divatLine += "|";
            }
            String publicCards = currentHand.getPublicCardsString(r);
            if(!publicCards.isEmpty()) {
                divatLine += "/"+publicCards;
            }
        }        
        divatLine += ":";
        for(Player p : players) {
            divatLine += p.getStack() - p.getBuyIn() + ",";            
        }        
        divatLine = divatLine.substring(0, divatLine.length()-1);
        try {
            divatLogWriter.write(divatLine+"\n");
            divatLogWriter.flush();
        } catch (IOException ex) {
            logError(ex);
        }
    }

    /**
     * Deal will deal a game until the Gamedef.gameOver is true.  This allows
     * for any number of options to be specified to the Gamedef to allow for 
     * different game ending situations (bankroll, handscount, forever).
     * 
     * The deal function follows the following algorithm:
     * While not gameover:
     *   generate a new hand for the players
     *   generate a new gamestate
     *   play hand
     * 
     * The gamestate here refers to the game properties such as potsize, folded
     * players, etc.
     * 
     */
    public void deal() {
        initLogging();
        sendPlayerInfos();
        while (!gameOver && !disconnected) {
            //Initialize the winners, gamestate, and get a new hand
            gamestate = new GameState();
            gamestate.setButton(players.size() - 1);
            for (Player p : players) {
                p.resetPlayer();
            }
            Collections.sort(players, new PositionComparator());
            //Used to check specific hands that can be pre set
            if (shuffle) {                
                currentHand = deck.dealHand(players.size(), gamedef.getNumRounds(),
                        gamedef.getNumPrivateCards(), gamedef.getNumPublicCards());                
            } else {
                shuffle = true;
            }            
            logState(currentHand.toString());
            playHand();
            if(shuffle) {
                logStats();            
            }
            if (handsPlayed >= gamedef.getNumHands()) {
                gameOver = true;
            }
        }
        if (gameOver) {
            String gameOverString = "#GAMEOVER";
            if(gamedef.hasSurvey()){
                gameOverString += "||"+gamedef.getSurveyURL();
                try {
                    String command = "python scripts/emailSurvey.py " +
                            this.name + " "+gamedef.getSurveyURL();
                    Process p = Runtime.getRuntime().exec(command);
                    
                } catch (IOException ex) {
                    logError(ex);
                }
            }
            for (Player p : players) {
                p.resetPlayer();                
                p.update(gameOverString);                
            }
        }
        while (!hasReported) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                logError(ex);
            }
        }
        matchFileHandler.close();
        errorFileHandler.close();
    }

    /**
     * Send the gui players the player info for correct display.
     */
    private void sendPlayerInfos() {
        String playerInfos = "#PLAYERS||";
        LinkedList<Player> seatSorted = players;
        Collections.sort(seatSorted, new SeatComparator());
        for (Player p : seatSorted) {
            playerInfos += p.toShortString() + "||";
        }

        for (Player p : players) {
            if (p.isGuiPlayer()) {
                p.update(playerInfos);
            }
        }
    }

    /**
     * Only used for test purposed, this is a method to create a specific hand
     */
    public void setCurrentHand(Hand testHand) {
        shuffle = false;
        currentHand = testHand;
    }

    /**
     * Plays a single hand of poker.
     */
    private void playHand() {
        do {
            playRound();
            if (gamestate.getRound() >= gamedef.getNumRounds() - 1) {
                gamestate.setHandOver(true);
            } else if(!isHandOver()) {
                nextRound();
            }
        } while (!gamestate.isHandOver());
        updatePlayers();
        if (shuffle) {
            evaluateHand();
            handsPlayed++;
            for (Player p : players) {
                p.setPosition((p.getPosition() + 1) % players.size());
            }
        }
    }

    /**
     * This function follows the following algorithm:
     *  while(!roundOver):
     *    get next player to act
     *    send gamestate to player
     *    wait for action from player or timeout
     *    update gamestate
     * 
     * This essentially plays one round of poker, gets the actions from all
     * the players and keeps an up to date gamestate information.
     */
    private void playRound() {
        currentPlayer = gamestate.getButton();

        //Post blind in round 0
        if (gamestate.getRound() == 0) {
            postBlinds();
        }
        while (!isRoundOver()) {
            updatePlayers();
            Player p = getNextPlayer();
            try {
                lastAction = parseAction(p.getAction());
            } catch (NullPointerException ex) {
                gamestate.setHandOver(true);
                lastAction = "f";
                handleDisconnect();                
            }
            updateGamestate();
            if (gamestate.isHandOver()) {
                return;
            }
        }
    }

    /**
     * On a disconnect, for now we are going to do a few things.
     * First we are going to end the hand in a fold for the disconnected player
     * Second, we are going to stop the match and set a flag for the Room to pickup.
     * This will allow the room to serialize the dealer object to file, and when 
     * we reload, we will grab the dealer object, load it in and continue from the
     * very next hand.
     */
    private void handleDisconnect() {
        disconnected = true;
        int idlePlayers = players.size();
        for (Player p : players) {
            if (p.isActed()) {
                idlePlayers--;
            }
        }
        if (idlePlayers > 0 && gamestate.getRound() == 0) {
            shuffle = false;
        }
    }

    /**
     * Update the game to the next round.  Reset players current bets and action
     * flags
     */
    private void nextRound() {
        for (Player p : players) {
            p.resetRound();
        }
        gamestate.nextRound();
    }

    /**
     * This posts the "antes" or blinds for the game.  Assumes that after the
     * blinds are posted, the action falls to the next player at the table.
     */
    private void postBlinds() {
        if (gamedef.isReverseBlinds() && players.size() == 2) {
            currentPlayer = 0;
        }
        for (int i = 0; i < gamedef.getBlindStructure().length; i++) {
            Player p = getNextPlayer();
            int betValue = gamedef.getBlind(i);
            p.postBlind(betValue);
            gamestate.makeBet(betValue, 0);
            //No Limit Betting
            if (gamedef.isNoLimit()) {
                gamestate.addToActionString("b" + betValue);
            }
        }
        gamestate.setNumBets(0);
    }

    /**
     * Increments the currentPlayer index to the next player and returns that 
     * index.
     * @return An integer index to the next player in the list of players % size
     */
    private int nextPlayer() {
        currentPlayer = (currentPlayer + 1) % players.size();
        return currentPlayer;
    }

    /**
     * This function returns the next active player.  That is, the next player
     * that hasn't folded or isn't all in or the player we started at (That player
     * must have went all in and everybody else folded) Probably shouldn't be 
     * able to occur.
     * @return The next active player
     */
    private Player getNextPlayer() {
        Player p;
        int count = 0;
        do {
            p = players.get(nextPlayer());
            count++;
        } while (p.isFolded() || p.isAllIn() || count >= players.size());
        return p;
    }

    /**
     * Check to see if the round is over according to the rules of the game
     * For this to be satisfied, all the players must now be unable to act.
     * We are going to refactor these checks out and see if a player "can act"
     *  
     * @return Whether or not the round is over.
     */
    private boolean isRoundOver() {
        int activePlayers = players.size();
        for (Player p : players) {
            if (p.isFolded()) {
                //Remove folded players from active players                           
                activePlayers--;
            } else if (p.isAllIn()) {
                //Remove all in players from active players            
                activePlayers--;
            } else if (p.getCurrentBet() >= gamestate.getCurrentBet() && p.isActed()) {
                //Remove calls or raises            
                activePlayers--;
            }
        }
        return (activePlayers <= 0);
    }

    /**
     * Update the current gamestate.  This will update the game based on the 
     * action that was taken by the player and set important things like
     * is the hand or round over etc, etc.
     */
    private void updateGamestate() {
        Player p = players.get(currentPlayer);

        int raise = 0;
        int currentBet = gamestate.getCurrentBet();
        int playerBet = p.getCurrentBet();

        switch (lastAction.toLowerCase().charAt(0)) {
            case 'f':
                //fold case
                p.fold();
                gamestate.addToActionString("f");
                break;
            case 'c':
                //call case
                gamestate.makeBet(p.call(currentBet), playerBet);
                gamestate.addToActionString("c");
                if (gamedef.isNoLimit()) {
                    gamestate.addToActionString("" + currentBet);
                }
                break;
            case 'r':
                //raise case
                if (lastAction.length() > 1) {
                    try {                        
                        raise = new Integer(lastAction.substring(1)).intValue() - playerBet;
                        if (raise <= 0 || raise < gamestate.getMinBet()) {
                            lastAction = "c";
                            updateGamestate();
                            return;
                        } else {
                            gamestate.addToActionString("r" + (raise + playerBet));
                        }
                    } catch (NumberFormatException e) {
                        //Do nothing yet
                    }
                } else {
                    if (gamestate.getNumBets() >= gamedef.getBetsPerRound()[gamestate.getRound()]) {
                        lastAction = "c";
                        updateGamestate();
                        return;
                    }
                    raise = gamestate.getCurrentBet() + gamedef.getBet(gamestate.getRound()) - playerBet;
                    gamestate.addToActionString("r");
                }
                gamestate.makeBet(p.bet(raise), playerBet);
                break;
            default:
                //Something broken, raise excepion
                break;
        }
        //Check to see if everyone has folded... then we can be hand over!
        gamestate.setHandOver(isHandOver());
    }

    /**
     * Check to see if everyone is all in or has folded (basically nobody can 
     * act anymore)
     * @return True if the hand is over and we should evaluate
     */
    private boolean isHandOver() {
        int foldCount = 0;
        int allInCount = 0;
        int callCount = 0;
        for (Player p : players) {
            if (p.isFolded()) {
                //First check the fold cases            
                foldCount++;
                if (foldCount == players.size() - 1) {
                    return true;
                }
            } else if (p.isAllIn()) {
                //All Ins            
                allInCount++;
            } else if (p.getCurrentBet() == gamestate.getCurrentBet()) {
                //One caller, everone else all in
                callCount++;
                if (callCount > 1) {
                    return false;
                }
            }
            if ((allInCount + callCount + foldCount) == players.size()) {
                // Everyone has either folded, went all in (except one caller)
                return true;
            }
        }
        return false;
    }

    /**
     * The evaluateHand function runs the routine for determining which players
     * won and how much each of them won.  First it will determine the rank of 
     * the players, rank 0 being the winner(s) followed by rank 1, rank 2, etc.
     * This is done for each sidepot, and we pay the money out of the potsize.
     * 
     */
    private void evaluateHand() {
        String cardString;
        LinkedList<Player> rankedPlayers = new LinkedList<Player>();
        LinkedList<Player> foldedPlayers = new LinkedList<Player>();
        HandEvaluator h = new HandEvaluator();
        
        //Assign Hand Ranks
        for (Player p : players) {
            cardString = currentHand.getEvaluationString(players.indexOf(p));
            if (!p.isFolded()) {
                p.setHandRank(HandEvaluator.rankHand(new EvaluateHand(cardString)));
                p.setHandString(HandEvaluator.nameHand(new EvaluateHand(cardString)));
                p.setCardString(h.getBest5CardHand(new EvaluateHand(cardString)).toString());
                rankedPlayers.add(p);
            } else {
                p.setHandRank(-1);
                foldedPlayers.add(p);
            }
        }

        int i = 0;
        while (gamestate.getPotsize() > 0) {
            i++;
            Collections.sort(rankedPlayers, new PotCommitedComparator());
            int minTotalCommited = rankedPlayers.get(0).getTotalCommitedToPot();
            Collections.sort(rankedPlayers, new HandRankComparator());

            LinkedList<Player> potWinners = new LinkedList<Player>();

            int maxRank = -1;
            for (Player p : rankedPlayers) {
                if (p.getHandRank() >= maxRank) {
                    maxRank = p.getHandRank();
                    potWinners.add(p);
                }
            }

            int payout = 0, remainder = 0;
            for (int j = 0; j < foldedPlayers.size(); j++) {
                Player p = foldedPlayers.get(j);
                if (p.getTotalCommitedToPot() > 0) {
                    if (p.getTotalCommitedToPot() <= minTotalCommited) {
                        payout += p.getTotalCommitedToPot();
                        foldedPlayers.remove(p);
                        j--;
                    } else {
                        payout += minTotalCommited;
                        p.subtractTotalCommitedToPot(minTotalCommited);
                    }
                }
            }

            //Still need to adjust what happens to the remainder chips in odd
            //pot sizes
            payout += rankedPlayers.size() * minTotalCommited;
            remainder = payout % potWinners.size();
            payout /= potWinners.size();

            gamestate.subtractFromPot(remainder);

            String showdownString = "";
            for (Player p : potWinners) {
                p.payout(payout);
                showdownString += p.getName() + " won " + payout;
                if (rankedPlayers.size() > 1) {
                    showdownString += " with hand " + p.getHandString() + "/"+
                            p.getCardString()+":";
                }
                gamestate.subtractFromPot(payout);
            }

            //Let the players see the hand if there was a showdown
            if (rankedPlayers.size() > 1) {
                for (Player p : players) {
                    p.update(getShowdownGameState(p));
                    logState(getShowdownGameState(p));
                }
            }

            //Send the showdown message to the GUI players
            for (Player p : players) {
                if (p.isGuiPlayer()) {
                    p.update("#SHOWDOWN||" + showdownString);
                }
            }                        
            
            for (int j = 0; j < rankedPlayers.size(); j++) {
                Player p = rankedPlayers.get(j);
                if (p.getTotalCommitedToPot() == minTotalCommited) {
                    rankedPlayers.remove(p);
                    j--;
                } else {
                    rankedPlayers.get(rankedPlayers.indexOf(p)).subtractTotalCommitedToPot(minTotalCommited);
                }
            }
            for (Player p : players) {
                int score = p.getStack() - p.getBuyIn();
                p.addToScore(score);                
            }
            if(!gamedef.isNoLimit() && gamedef.getMaxPlayers() == 2) {
                logDivat();
            }
        }
    }

    /**
     * Get the gamestate for the specified player in the AAAI competition format
     * This is a string representation of the betting, and the private and public
     * cards
     * @param p The player for whom to show the gamestate
     * @return A string representing the current state of the game
     */
    private String getGameState(Player p) {
        String delimiter = ":";
        String currentGameState = "MATCHSTATE" + delimiter;
        currentGameState += p.getPosition() + delimiter;
        currentGameState += handsPlayed + delimiter;
        currentGameState += gamestate.getActionString() + delimiter;

        for (Player player : players) {
            if (player.equals(p)) {
                for (int round = 0; round <= gamestate.getRound(); round++) {
                    String privateCardString = currentHand.getPrivateCardsString(
                            p.getPosition(), round);
                    currentGameState += (round > 0 &&
                            !privateCardString.equalsIgnoreCase("") ? "/" : "");
                    currentGameState += privateCardString;
                }
            }
            if (player.getPosition() < (players.size() - 1)) {
                currentGameState += "|";
            }
        }

        for (int round = 0; round <= gamestate.getRound(); round++) {
            String publicCardString = currentHand.getPublicCardsString(round);
            currentGameState += (round > 0 &&
                    !publicCardString.equalsIgnoreCase("") ? "/" : "");
            currentGameState += publicCardString;
        }

        return currentGameState;
    }

    /**
     * Get the Full Showdown Gamestate to send to the players at HandOver
     * @param position The position of the player to send this too.
     * @return A string representation of the Full Showdown Gamestate
     */
    private String getShowdownGameState(Player p) {
        String delimiter = ":";
        String fullGameState = "MATCHSTATE" + delimiter;
        fullGameState += p.getPosition() + delimiter;
        fullGameState += handsPlayed + delimiter;
        fullGameState += gamestate.getActionString() + delimiter;

        for (Player player : players) {
            for (int round = 0; round < gamedef.getNumRounds(); round++) {
                String privateCardString = currentHand.getPrivateCardsString(
                        player.getPosition(), round);
                fullGameState += (round > 0 &&
                        !privateCardString.equalsIgnoreCase("") ? "/" : "");
                fullGameState += privateCardString;
            }
            if (player.getPosition() < players.size() - 1) {
                fullGameState += "|";
            }
        }

        for (int round = 0; round < gamedef.getNumRounds(); round++) {
            String publicCardString = currentHand.getPublicCardsString(round);
            fullGameState += (round > 0 &&
                    !publicCardString.equalsIgnoreCase("") ? "/" : "");
            fullGameState += publicCardString;
        }

        return fullGameState;
    }

    /**
     * Send the players thier new gamestates
     */
    private void updatePlayers() {
        for (Player p : players) {
            p.update(getGameState(p));
            logState(getGameState(p));
        }
    }

    /**
     * Parse out the last token of the response string and return it as the action
     * the player took
     * 
     * @param playerResponse
     * @return the action from the response string
     */
    private String parseAction(String response) {        
        StringTokenizer st = new StringTokenizer(response, ":");
        String action = "";
        st.nextToken();
        st.nextToken();
        int handCheck = new Integer(st.nextToken()).intValue();
        if(handCheck != handsPlayed) {
            logWarning("Dealer recieved action for hand number "+handCheck+" waiting for hand number "+handsPlayed);
            action = parseAction(players.get(currentPlayer).getAction());
            return action;
        }
        logState(response);
        while (st.hasMoreTokens()) {
            action = st.nextToken();
        }
        return action;
    }

    /**
     * Run the dealer in a thread
     */
    public void run() {
        deal();
    }

    /**
     * Return stats about the game.
     * @return A string representing the room stats
     */
    public String getStats() {
        String stats = "";
        stats += gamedef.toString() + "\n";
        stats += "STATS:Current Player:" + currentPlayer + ":Hands Played:" + handsPlayed + "\n";
        for (Player p : players) {
            stats += p.toString() + "\n";
        }
        if (gameOver || disconnected) {
            hasReported = true;
        }
        return stats;
    }

    /**
     * Check to see whether or not the games is over
     * @return True for gameOver, False otherwise
     */
    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * Check to see if one of the players has disconnected
     * @return True is there has been a disconect, False otherwise
     */
    public boolean isDisconnected() {
        return disconnected;
    }

    /**
     * Set the disconnected flag
     * @param disconnected a boolean to set the disconnected flag
     */
    public void setDisconnected(boolean disconnected) {
        this.disconnected = disconnected;
    }

    /**
     * An overriden function used to change the default serialization behavior
     * on a write
     * @param out the output stream to write the object to
     * @throws java.io.IOException
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    /**
     * An overriden function used when loading the object.  The default behavior 
     * is used and the disconnect flag is reset
     * @param in The input stream to load the object from
     * @throws java.io.IOException
     * @throws java.lang.ClassNotFoundException
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        disconnected = false;
    }

    /**
     * Restores a player matching the name.  This resets thier stack score and position
     * @param name The players name to restore
     * @param seat The seat to restore the player to
     * @param stack The stacksize the player needs restoring to
     * @param position The positions to restore the player to
     * @param score The score of the player to be restored
     * @return True on a sucessful restore, false otherwise
     */
    public boolean restorePlayer(String name, int seat, int stack, int position, int score) {
        for (Player p : players) {
            if (p.getName().equalsIgnoreCase(name)) {
                p.setPosition(position);
                p.setSeat(seat);
                p.setStack(stack);
                p.setScore(score);
                return true;
            }
        }
        return false;
    }

    /**
     * Used to restore the game to a specific hand.  The game deals out the 
     * hands from the deck until the hand number is reached
     * @param handNumber The hand to restore to
     * @return True on a sucessful restore, False otherwise
     */
    public void restoreToHand(int handNumber) {
        while (handsPlayed < handNumber) {
            currentHand = deck.dealHand(players.size(), gamedef.getNumRounds(),
                    gamedef.getNumPrivateCards(), gamedef.getNumPublicCards());
            handsPlayed++;
        }
        if (handsPlayed >= gamedef.getNumHands()) {
                gameOver = true;
       }
    }
}
