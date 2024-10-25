import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;


/*Sources:
https://www.geeksforgeeks.org/java-io-pipedinputstream-class-java/
https://stackoverflow.com/questions/9758457/try-catch-vs-null-check-in-java -> line 45
 */
public class Poker{
        //instantiated outside main method to make them accessible throughout class
        static Socket socket;
        static DataInputStream dis;
        static DataOutputStream dos;

        public static void main(String[] args) throws IOException {
            //main method used to be much longer but chose to make a client method in order to clean code
            //scanning for test mode call
            try {
                if (args.length == 1 && args[0].equalsIgnoreCase("test")) {
                    runTestMode();  // Run in test mode
                }
                else if (args.length == 2) {
                    String host = args[0];
                    int port = Integer.parseInt(args[1]);

                    socket = new Socket(host, port);
                    dis = new DataInputStream(socket.getInputStream());
                    dos = new DataOutputStream(socket.getOutputStream());

                    System.out.println("Connected to " + host + " on port " + port);
                }
                else {
                    System.err.println("Usage: java Poker <host> <port> OR java Poker test");
                }
            }
            catch(NumberFormatException e) {
                System.err.println("Invalid port number: " + (args.length > 1 ? args[1] : "N/A"));
            }
            catch(IOException e) {
                System.err.println("I/O Error: " + e.getMessage());
            }
            //was encountering error where dis/dos were null somehow and program was timing out
            finally {
                if (dis != null && dos != null) {
                    play();
                }
                else {
                    System.err.println("Connection not established.");
                }
                //ensure resources are closed
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            }

        }

        public static void play() throws IOException{
            //reading info from dealer
            String input = read(dis);

            //setting conditional for loop to not break until it reads 'done'
            while(!input.contains("done")){
                //if contains 'login', write login info
                if(input.contains("login")){
                    login();
                }
                //else, do something with bet info
                else if (input.contains("bet1")) {
                    determineBetOrFold(input); //handle bet 1
                    readStatus();
                }
                else if (input.contains("bet2")) {
                    determineBet2OrFold(input); //handle bet 2
                    readStatus();
                }
                input = read(dis);
            }

            if(input.contains("done")){
                //reads why the game ended according to dealer
                System.out.println(input);
                socket.close();
            }
        }

        public static void readStatus() throws IOException{
            String status = read(dis);
        }

        public static void determineBetOrFold(String input) throws IOException{
            //bet1:<<number of chips in your stack>>:<<size of the current pot>>:<<current bet to match or
            //beat>>:<<your "hole" card>>:<<your "up" card>>:up:<<first player's "up" card>>:<<second
            //player's "up" card>>::etc
            String[] parts = input.split(":");
            //chips,pot, and bet
            int numOfChips = Integer.parseInt(parts[1]);
            int currentPot = Integer.parseInt(parts[2]);
            int currentBet = Integer.parseInt(parts[3]);

            //players hand
            String holeUp = parts[4];
            String upCard = parts[5];

            //opponents hand
            List<String> otherUpCards = new ArrayList<>();
            //start from 7 since elem 6 is 'up' to indicate following up cards from oppionents
            for(int i = 7; i < parts.length; i++){
                otherUpCards.add(parts[i]);
            }

            String handQuality = handGoodOrBad(holeUp, upCard);
            if(handQuality.equals("Excellent")){
                int excellentRaiseBet = currentBet + 8;
                write("bet:" + excellentRaiseBet, dos);
            }
            else if(handQuality.equals("Great")){
                int greatRaiseBet = currentBet + 5;
                write("bet:" + greatRaiseBet, dos);
            }
            else{
                write("fold", dos);
            }
        }

        public static void determineBet2OrFold(String input) throws IOException{
            String[] parts = input.split(":");

            int numOfChips = Integer.parseInt(parts[1]);
            int currentPot = Integer.parseInt(parts[2]);
            int currentBet = Integer.parseInt(parts[3]);

            //players hand
            String holeUp = parts[4];
            String upCard1 = parts[5];
            String upCard2 = parts[6];

            List<String> otherUpCards = new ArrayList<>();
            //start from 8 since elem 6 is 'up' to indicate following up cards from oppionents
            for(int i = 8; i < parts.length; i++){
                otherUpCards.add(parts[i]);
            }
            String handQuality = handGoodOrBadBet2(holeUp,upCard1,upCard2);

            if(handQuality.equals("Excellent")) {
                int excellentRaiseBet = currentBet + 8;
                System.out.println("Placing an excellent bet: " + excellentRaiseBet);
                write("bet:" + excellentRaiseBet, dos);
            }
            else if(handQuality.equals("Great")) {
                int greatRaiseBet = currentBet + 5;
                System.out.println("Placing a great bet: " + greatRaiseBet);
                write("bet:" + greatRaiseBet, dos);
            }
            else{
                System.out.println("Folding");
                write("fold", dos);
            }
        }

        public static String handGoodOrBad(String holeUp, String upCard){
            String holeRank = extractRank(holeUp);
            String upRank = extractRank(upCard);

            if(holeRank.equals(upRank)){
                return "Excellent"; //this is a pair
            }
            else if (isHighValue(holeRank) && isHighValue(upRank)) {
                return "Great"; // both high-value cards
            }
            else if (isHighValue(holeRank) || isHighValue(upRank)) {
                return "Good"; // at least one high ranking card
            }
            else{
                return "Bad"; // no high rank card
            }
        }

        public static String handGoodOrBadBet2(String holeUp, String upCard1, String upCard2){
            String holeRank = extractRank(holeUp);
            String upRank = extractRank(upCard1);
            String upRank2 = extractRank(upCard2);

            if(holeRank.equals(upRank) && holeRank.equals(upRank2)){
                return "Excellent"; //this is three of a kind
            }
            else if (isHighValue(holeRank) && isHighValue(upRank)) {
                return "Great"; // both high-value cards
            }
            return holeRank;
        }

        private static String extractRank(String card) {
            if (card.length() == 3) { // for 10H, 10S etc
                return card.substring(0, 2);
            }
            else {
                return card.substring(0, 1); // for KS, QH, etc
            }
        }

        private static boolean isHighValue(String rank) {
            return rank.equals("A") || rank.equals("K") || rank.equals("Q") || rank.equals("J") || rank.equals("10") || rank.equals("9") || rank.equals("8");
        }

        public static void login() throws IOException {
            //isolating repeating login prompts
            write("Diego-Uxfu:Diego", dos);
        }

        public static void runTestMode() throws IOException{
            System.out.println("Running test mode...");

            /*
            piped input and output was the solution to my test not running, kept receiving
            Exception in thread "main" java.lang.NullPointerException:
            Cannot invoke "java.io.DataOutputStream.writeUTF(String)" because "<parameter2>" is null.
            post login
             */
            PipedInputStream pis = new PipedInputStream();
            PipedOutputStream pos = new PipedOutputStream(pis);
            dis = new DataInputStream(pis);
            dos = new DataOutputStream(pos);

            String[] dealerCommands = {
                    "login",
                    "bet1:208:24:12:KS:10D:up:AS:8H:10D:QD:2C",
                    "status:win:AS:KH:10D",
                    "bet2:183:66:0:KS:10D:10S:up:AS:AH:8H:6D:10D:10S:QD:JC:2C:4H",
                    "status:lose:AS:KH:10D",
                    "done:You ran out of money"
            };

            for (String command : dealerCommands) {
                System.out.println("Simulating dealer input -> " + command);

                // Handling the commands as if they're read from the server
                if(command.equals("login")) {
                    login();
                }
                else if(command.startsWith("bet1")) {
                    determineBetOrFold(command);
                    readStatus();
                }
                else if(command.startsWith("status")) {
                    readStatus();
                }
                else if(command.startsWith("bet2")) {
                    determineBet2OrFold(command);
                    readStatus();
                }
                else if(command.startsWith("done")) {
                    System.out.println(command);
                }
            }
            System.out.println("Test Mode Complete");
        }

        private static void write(String string, DataOutputStream dos) throws IOException{
            //will 'write' to dealer info that it request
            dos.writeUTF(string);
            dos.flush();
        }

        private static String read(DataInputStream dis) throws IOException{
            //returns info from dealer
            return dis.readUTF();
        }
}




