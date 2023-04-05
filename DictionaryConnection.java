package ca.ubc.cs317.dict.net;

import ca.ubc.cs317.dict.model.Database;
import ca.ubc.cs317.dict.model.Definition;
import ca.ubc.cs317.dict.model.MatchingStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import java.util.regex.Matcher;
import java.util.*;

/**
 * Created by Jonatan on 2017-09-09.
 * Implemented by Randy 2023.
 */
public class DictionaryConnection {

    private static final int DEFAULT_PORT = 2628;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    /**
     * Establishes a new connection with a DICT server using an explicit host and
     * port number, and handles initial
     * welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @param port Port number used by the DICT server
     * @throws DictConnectionException If the host does not exist, the connection
     *                                 can't be established, or the messages
     *                                 don't match their expected value.
     */
    public DictionaryConnection(String host, int port) throws DictConnectionException {

        try {
            socket = new Socket(host, port);
        } catch (UnknownHostException e) {
            throw new DictConnectionException("Unknown host: " + host + " " + port);
        } catch (IOException e) {
            throw new DictConnectionException("I/O exception occurs while establishing socket.");
        }

        String msg;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            msg = in.readLine();
            if (msg == null) {
                throw new DictConnectionException("Server didn't respond with a message");
            }
        } catch (IOException e) {
            throw new DictConnectionException("Failed to get message from server.");
        }

        // When we establish a successful connection to the server, the server should
        // respond with status code 220.
        String expectedMessage = "220 ((.)+)";
        Matcher m = Pattern.compile(expectedMessage).matcher(msg);
        if (m.matches()) {
            System.out.println("welcome msg: " + m.group(1));
        } else {
            throw new DictConnectionException("Unexpected response from server.");
        }
    }

    /**
     * Establishes a new connection with a DICT server using an explicit host, with
     * the default DICT port number, and
     * handles initial welcome messages.
     *
     * @param host Name of the host where the DICT server is running
     * @throws DictConnectionException If the host does not exist, the connection
     *                                 can't be established, or the messages
     *                                 don't match their expected value.
     */
    public DictionaryConnection(String host) throws DictConnectionException {
        this(host, DEFAULT_PORT);
    }

    /**
     * Sends the final QUIT message and closes the connection with the server. This
     * function ignores any exception that
     * may happen while sending the message, receiving its reply, or closing the
     * connection.
     *
     */
    public synchronized void close() {
        try {
            out.println("QUIT");
            // Read all data before closing socket connection
            while (!(in.readLine()).startsWith("221")) {
            }

            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
        }
    }

    /**
     * Requests and retrieves all definitions for a specific word.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param database The database to be used to retrieve the definition. A special
     *                 database may be specified,
     *                 indicating either that all regular databases should be used
     *                 (database name '*'), or that only
     *                 definitions in the first database that has a definition for
     *                 the word should be used
     *                 (database '!').
     * @return A collection of Definition objects containing all definitions
     *         returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the
     *                                 messages don't match their expected value.
     */
    public synchronized Collection<Definition> getDefinitions(String word, Database database)
            throws DictConnectionException {
        Collection<Definition> set = new ArrayList<>();

        // Construct command to send to server
        StringBuilder sb = new StringBuilder();
        sb.append("DEFINE ");
        sb.append(database.getName());
        sb.append(" ");
        sb.append("\"");
        sb.append(word);
        sb.append("\"");

        try {
            out.println(sb.toString());

            Definition currentDefinition = null;
            String msg = in.readLine();

            if (msg.startsWith("150")) {
                String regex = "151 \"((.)+)\" ((.)+) (\"(.+)\").*";
                while (!(msg = in.readLine()).startsWith("250")) {
                    /**
                     * If server responds successfully, extract definitions from response
                     */

                    // Separate each definition by .
                    if (msg.equals(".")) {
                        set.add(currentDefinition);
                    } else {
                        Matcher m = Pattern.compile(regex).matcher(msg);
                        // Extract word and database for the definition
                        if (m.matches()) {
                            String matchedWord = m.group(1);
                            String db = m.group(3);
                            currentDefinition = new Definition(matchedWord, db);
                        } else if (currentDefinition != null) { // Extract the definition of the word.
                            currentDefinition.appendDefinition(msg);
                        }
                    }
                }
            } else if (msg.startsWith("550")) {
                return new ArrayList<>();
            } else if (msg.startsWith("552")) {
                return new ArrayList<>();
            } else {
                throw new DictConnectionException("Unexpected response from server");
            }
        } catch (IOException e) {
            throw new DictConnectionException("Encountered I/O error");
        }
        return set;
    }

    /**
     * Requests and retrieves a list of matches for a specific word pattern.
     *
     * @param word     The word whose definition is to be retrieved.
     * @param strategy The strategy to be used to retrieve the list of matches
     *                 (e.g., prefix, exact).
     * @param database The database to be used to retrieve the definition. A special
     *                 database may be specified,
     *                 indicating either that all regular databases should be used
     *                 (database name '*'), or that only
     *                 matches in the first database that has a match for the word
     *                 should be used (database '!').
     * @return A set of word matches returned by the server.
     * @throws DictConnectionException If the connection was interrupted or the
     *                                 messages don't match their expected value.
     */
    public synchronized Set<String> getMatchList(String word, MatchingStrategy strategy, Database database)
            throws DictConnectionException {
        Set<String> set = new LinkedHashSet<>();

        // Construct command to send to server
        StringBuilder sb = new StringBuilder();
        sb.append("MATCH ");
        sb.append(database.getName());
        sb.append(" ");
        sb.append(strategy.getName());
        sb.append(" ");
        sb.append("\"");
        sb.append(word);
        sb.append("\"");

        try {
            out.println(sb.toString());

            String msg = in.readLine();

            if (msg.startsWith("152")) {
                /**
                 * If server responds successfully, extract matches from response.
                 */
                while (!(msg = in.readLine()).startsWith("250 ok")) {
                    String regex = "((\\w|-)+) (\"(.+)\")";
                    Matcher m = Pattern.compile(regex).matcher(msg);
                    if (m.matches()) {
                        String wordMatch = m.group(4);
                        set.add(wordMatch);
                    }
                }
            } else if (msg.startsWith("550")) {
                return new LinkedHashSet<>();
            } else if (msg.startsWith("551")) {
                return new LinkedHashSet<>();
            } else if (msg.startsWith("552")) {
                return new LinkedHashSet<>();
            } else {
                throw new DictConnectionException("Unexpected response from server.");
            }
        } catch (IOException e) {
            throw new DictConnectionException("Encountered I/O error");
        }

        return set;
    }

    /**
     * Requests and retrieves a map of database name to an equivalent database
     * object for all valid databases used in the server.
     *
     * @return A map of Database objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the
     *                                 messages don't match their expected value.
     */
    public synchronized Map<String, Database> getDatabaseList() throws DictConnectionException {
        Map<String, Database> databaseMap = new HashMap<>();

        try {
            out.println("SHOW DB");

            String msg;
            msg = in.readLine();

            if (msg.startsWith("110")) {
                /**
                 * If server responds successfully, extract database list from response.
                 */
                String databaseRegex = "((\\w|-)+) (\"(.+)\")";
                while (!(msg = in.readLine()).startsWith("250 ok")) {
                    Matcher m = Pattern.compile(databaseRegex).matcher(msg);
                    if (m.matches()) {
                        String name = m.group(1);
                        String desc = m.group(4);
                        Database d = new Database(name, desc);
                        databaseMap.put(name, d);
                    }
                }
            } else if (msg.startsWith("554")) {
                return new HashMap<>();
            } else {
                throw new DictConnectionException("Unexpected response from server.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new DictConnectionException("Encountered I/O error");
        }

        return databaseMap;
    }

    /**
     * Requests and retrieves a list of all valid matching strategies supported by
     * the server.
     *
     * @return A set of MatchingStrategy objects supported by the server.
     * @throws DictConnectionException If the connection was interrupted or the
     *                                 messages don't match their expected value.
     */
    public synchronized Set<MatchingStrategy> getStrategyList() throws DictConnectionException {
        Set<MatchingStrategy> set = new LinkedHashSet<>();
        try {
            out.println("SHOW STRATEGIES");

            String msg = in.readLine();

            if (msg.startsWith("111")) {
                /**
                 * If server responds successfully, extract strategy names and descriptions from
                 * response.
                 */
                String responseRegex = "((\\w)+) (\"(.+)\")";
                while (!(msg = in.readLine()).startsWith("250 ok")) {
                    Matcher m = Pattern.compile(responseRegex).matcher(msg);
                    if (m.matches()) {
                        String name = m.group(1);
                        String desc = m.group(4);
                        MatchingStrategy s = new MatchingStrategy(name, desc);
                        set.add(s);
                    }
                }
            } else if (msg.startsWith("555")) {
                return new LinkedHashSet<>();
            } else {
                throw new DictConnectionException("Unexpected response from server");
            }
        } catch (IOException e) {
            throw new DictConnectionException("Encountered I/O error");
        }
        return set;
    }

    /**
     * Requests and retrieves detailed information about the currently selected
     * database.
     *
     * @return A string containing the information returned by the server in
     *         response to a "SHOW INFO <db>" command.
     * @throws DictConnectionException If the connection was interrupted or the
     *                                 messages don't match their expected value.
     */
    public synchronized String getDatabaseInfo(Database d) throws DictConnectionException {
        StringBuilder result = new StringBuilder();
        try {
            // Construct command and send to server
            StringBuilder sb = new StringBuilder();
            sb.append("SHOW INFO ");
            sb.append(d.getName());
            out.println(sb.toString());

            String msg = in.readLine();
            if (msg.startsWith("112")) {
                /**
                 * If server responds successfully, extract database information from the
                 * response.
                 */
                while (!(msg = in.readLine()).startsWith(".")) {
                    result.append(msg);
                }
                while (!(msg = in.readLine()).startsWith("250")) {

                }
            } else if (msg.startsWith("550")) {
                throw new DictConnectionException("Invalid database: " + d.getName());
            } else {
                throw new DictConnectionException("Unexpected message from server");
            }
        } catch (IOException e) {
            throw new DictConnectionException("Failed to show database info");
        }
        return result.toString();
    }
}
