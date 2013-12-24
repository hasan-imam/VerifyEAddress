package mail.validation;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

/**
 * Class that lets you verify existence of email addresses
 * @author Hasan Imam
 */
public class AddressVerifier {
	/**
	 * Port to use for connection. Values: 25 (SMTP), 587 (Submission), 465 (legacy secure SMTP -
	 * violates specification).
	 */
	private static final int SMTP_PORT = 25;
	/**
	 * 'Carriage return + line feed' - to add to the end of commands to SMTP server
	 */
	private static final String CRLF = "\r\n";
	/**
	 * Some server reply codes
	 */
	private static final int 
			CODE_SERVICE_READY = 220, 
			CODE_OK = 250;
	/**
	 * Log4j logger
	 */
	private static Logger logger = Logger.getLogger(AddressVerifier.class);
	/**
	 * List of address that realistically should not be on the user list. 
	 * Used to test if the server is actually using a lookup or not. 
	 * Add more if necessary.
	 */
	private static List<String> invalidAddrs = Arrays.asList("a.b.c.d.e.f.g.h",
			"a_b_c_d_e_f_g_h", "00009999iiii");
	
	/**
	 * Sender email address to use.
	 */
	private String senderAddr;
	/**
	 * SMTP server name/address
	 */
	private String serverName;
	/**
	 * The socket to the SMTP server
	 */
	private Socket connection;
	/**
	 * Streams for reading from socket
	 */
	private BufferedReader fromServer;
	/**
	 * Streams for writing to socket
	 */
	private DataOutputStream toServer;
	
	/**
	 * Construct an {@link AddressVerifier} object. Creates a connection with the given server. Checks service
	 * status and sends an SMTP HELO. Constructor fails if fails to connect or performs these steps.
	 * Note that the connection is closed during object destruction.
	 * 
	 * @param serverName
	 *            Server to connect to (e.g. 173.194.78.27 or alt3.gmail-smtp-in.l.google.com)
	 * @throws IOException
	 * @throws UnknownHostException
	 */
	public AddressVerifier(String serverName) throws UnknownHostException, IOException {
		this(serverName, "asdf@gmail.com");
	}
	
	/**
	 * Construct an {@link AddressVerifier} object. Creates a connection with the given server. Checks service
	 * status and sends an SMTP HELO. Constructor fails if fails to connect or performs these steps.
	 * Note that the connection is closed during object destruction.
	 * 
	 * @param serverName
	 *            Server to connect to (e.g. 173.194.78.27 or alt3.gmail-smtp-in.l.google.com)
	 * @param senderAddress
	 *            Sender email address to use (e.g. asdf@gmail.com)
	 * @throws IOException
	 * @throws UnknownHostException
	 */
	public AddressVerifier(String serverName, String senderAddress) throws UnknownHostException, IOException {
		this.senderAddr = senderAddress;
		this.serverName = serverName;
		// Setup connection
		connection = new Socket(InetAddress.getByName(serverName), SMTP_PORT);
		fromServer = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		toServer = new DataOutputStream(connection.getOutputStream());

		// Read a line from server to get the reply for 'connect' command
		String reply = readServerReply();
		if (!checkReply(reply, AddressVerifier.CODE_SERVICE_READY)) {
			// If service not available, throw an IOException.
			AddressVerifier.logger.error(serverName + " not serving. Server reply: " + reply,
					new IOException(serverName + " not serving. Server reply: " + reply));
		}
		
		// Get the name of local machine and initiate SMTP handshake by saying HELO
		reply = sendCommand("HELO " + InetAddress.getLocalHost().getHostName() + CRLF);
		if (!checkReply(reply, AddressVerifier.CODE_OK)) {
			// If command fails, throw an IOException.
			AddressVerifier.logger.error("HELO command failed. " + serverName + " reply: " + reply,
					new IOException("HELO command failed. " + serverName + " reply: " + reply));
		}	
	}
	
	/**
	 * Destructor. Closes the connection if something bad happens.
	 * @throws Throwable 
	 */
	protected void finalize() throws Throwable {
		if (connection != null && connection.isConnected()) {
			// QUIT
			sendCommand("QUIT" + CRLF);
			// Don't wait to read reply. Close connection
			connection.close();
			// Note: at the point when this is being called, the logger object for the class might
			// have been destroyed. sendCommand() uses this logger. However, this does not cause an
			// error since,
			// "If an uncaught exception is thrown by the finalize method, 
			// the exception is ignored and finalization of that object terminates"
			// Source: Object.finalize() javadoc.
		}
		super.finalize();
	}
	
	/**
	 * Given a list of addresses, checks if they exist in the mail servers registry 
	 */
	public Map<String, Boolean> doesAddrExist(List<String> addressList)
			throws IOException {
		specifySender(); // Specify sender
		HashMap<String, Boolean> results = new HashMap<String, Boolean>();
		// Perform check for each address
		for (String addr : addressList) {
			results.put(
					addr,
					new Boolean(checkReply(sendCommand("RCPT TO:<" + addr + ">"
							+ CRLF), AddressVerifier.CODE_OK)));
		}
		reset(); // Reset once done checking
		return results;
	}
	
	/**
	 * Some SMTP servers actually don't have any user list and they reply OK even for the wrong
	 * email addresses. This function checks whether the server has a registry or not for a given
	 * domain. It tries to verify some possibly invalid email addresses in the domain and checks the
	 * server reply. A server with registry is expected to give error for all or at least some of
	 * these addresses. Note that the result is not 100% accurate.
	 * 
	 * @param domain
	 *            Domain name to use for this check. For example, if checking for google services,
	 *            give 'google.com'
	 * @return True if it thinks server has user registry. False if it thinks server is using a
	 *         'catch-all' scheme and not verifying addresses against any registry
	 * @throws IOException
	 */
	public boolean serverHasRegistry(String domain) throws IOException {
		AddressVerifier.logger.debug("Testing with invalid addresses for domain: " + domain);
		
		// Add domain name to create test addresses
		List<String> testAddrs = new LinkedList<String>();
		for (String prefix : AddressVerifier.invalidAddrs) {
			testAddrs.add(prefix + "@" + domain);
		}
		
		Map<String, Boolean> results = doesAddrExist(testAddrs);
		int validCount = 0;
		for (Boolean value : results.values()) {
			if (value) {
				validCount++;
			}
		}
		AddressVerifier.logger.debug(this.serverName + " reported "
				+ validCount + "/" + AddressVerifier.invalidAddrs.size()
				+ " invalid addresses to be valid.");
		if (validCount < AddressVerifier.invalidAddrs.size()) {
			// Server rejected some or all of the possibly invalid addresses
			return true;
		} else {
			// Server accepted all the (possibly) invalid addresses. 
			return false;
		}
	}
	
	/**
	 * Call this once to set the sender for each transaction
	 * @throws IOException
	 */
	private void specifySender() throws IOException {
		String reply = sendCommand("MAIL FROM:<" + this.senderAddr + ">" + AddressVerifier.CRLF);
		if (!checkReply(reply, AddressVerifier.CODE_OK)) {
			// If command fails, throw an IOException.
			AddressVerifier.logger.error(
					"Failed to specify sender. " + this.serverName 
							+ " reply: " + reply, 
					new IOException("Failed to specify sender. " 
							+ this.serverName + " reply: " + reply));
		}
	}
	
	/**
	 * This clears the MAIL FROM: and RCPT TO: address stacks, throwing out any message delivery in
	 * progress, and resetting the connection back to the state it was in just after 'HELO'. Call
	 * this at the end of each transaction.
	 * 
	 * @throws IOException
	 */
	private void reset() throws IOException {
		String reply = sendCommand("RSET" + AddressVerifier.CRLF);
		if (!checkReply(reply, AddressVerifier.CODE_OK)) {
			// If command fails, throw an IOException.
			AddressVerifier.logger.error("Failed to RSET. " + this.serverName + " reply: "
					+ reply, new IOException("Failed to RSET. "
					+ this.serverName + " reply: " + reply));
		}
	}
	
	/**
	 * Send an SMTP command to the server and return the server reply.
	 * 
	 * @param command
	 *            Properly formatted command string to send to server
	 * @return Reply from server
	 */
	private String sendCommand(String command) throws IOException {
		// Write command to server and read reply from server. 
		AddressVerifier.logger.trace("Sending command: " + command);
		this.toServer.writeBytes(command);
		return readServerReply();
	}
	
	/**
	 * Some server replies are multi-line replies. This function reads all the reply lines (until
	 * end of line) and returns a concatenated string of replies.
	 * 
	 * @return Concatenated string of replies
	 * @throws IOException If I/O error occurs in reading the buffer
	 */
	private String readServerReply() throws IOException {
		// Read first line of server reply
		String firstSegment = fromServer.readLine();
		
		// Check if there is more to read. 
		// Sometimes longer reply can be broken in multiple parts
		if (fromServer.ready()) {
			String commonPrefix = null;
			StringBuilder builder = new StringBuilder();
			builder.append(firstSegment);
			String aux = fromServer.readLine(); // Read second line
			
			// All the replied lines have the same prefix: server reply code.
			// Detect this prefix and remove it form subsequent lines before appending.
			int minLength = Math.min(aux.length(), firstSegment.length());
		    for (int i = 0; i < minLength; i++) {
		        if (aux.charAt(i) != firstSegment.charAt(i)) {
		        	// Found first mismatch. Store and break
		            commonPrefix = aux.substring(0, i);
		            break;
		        }
		    }
			
		    // Now append the second line
		    builder.append(aux.startsWith(commonPrefix)? 
		    		" " + aux.substring(commonPrefix.length()) : " " + aux);
		    
			// Keep testing if there are more lines to read. 
		    // Note: Reply chunks are always terminated with '\n'
			while (fromServer.ready() && (aux = fromServer.readLine()) != null) {
			    builder.append(aux.startsWith(commonPrefix)? 
			    		" " + aux.substring(commonPrefix.length()) : " " + aux);
			}
			
			firstSegment = builder.toString();
		}
		
		AddressVerifier.logger.trace("Serve replied: " + firstSegment);
		return firstSegment;
	}
	
	/**
	 * Checks if the reply contains the given code
	 * 
	 * @param reply
	 *            Server reply string
	 * @param code
	 *            Expected reply code
	 * @return Whether the expected reply code was returned
	 */
	public static boolean checkReply(String reply, int code) {
		return (AddressVerifier.parseReply(reply) == code);
	}

	/**
	 * Parse the reply line from the server. Returns the reply code.
	 * 
	 * @param reply
	 *            Server reply string
	 * @return Server reply code
	 */
	private static int parseReply(String reply) {
		StringTokenizer replyTokens = new StringTokenizer(reply);
		String replyCode = replyTokens.nextToken();
		if (replyCode.matches("[0-9]+")) {
			return Integer.parseInt(replyCode);
		} else {
			// If the server is using some different format, just look at the first 3 characters.
			// These should contain the srever reply code.
			return Integer.parseInt(replyCode.substring(0, 3));
		}
	}
}
