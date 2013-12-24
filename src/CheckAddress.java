import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import mail.validation.AddressVerifier;

/**
 * CLI wrapper
 * @author Hasan Imam
 */
public class CheckAddress {
	
	/**
	 * For printing colored output in console.
	 */
	public static final String 
			ANSI_RESET = "\u001B[0m",
			ANSI_RED = "\u001B[31m", 
			ANSI_GREEN = "\u001B[32m",
			ANSI_YELLOW = "\u001B[33m";

	/**
	 * @param args
	 *            First argument is the SMTP server. Next ones are email addresses to check.
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		if (args.length < 3) {
			System.err.println("Usage: java <appName> <domain name> <SMTP server name> [<email address to check>,...]");
			return;
		}
				
		AddressVerifier verifier = new AddressVerifier(args[1]);
		if (!verifier.serverHasRegistry(args[0])) {
			printColored("WARN: " + args[1]
					+ " may not have the user registry for domain: " + args[0],
					ANSI_YELLOW);
		}
		
		List<String> addressList = new ArrayList<String>(Arrays.asList(args));
		addressList.remove(args[0]);
		addressList.remove(args[1]);
		Map<String, Boolean> results = verifier.doesAddrExist(addressList);

		// Check email addresses
		for (Map.Entry<String, Boolean> entry : results.entrySet()) {
			if (entry.getValue()) {
				printColored(entry.getKey() + " : valid", ANSI_GREEN);
			} else {
				printColored(entry.getKey() + " : fake", ANSI_RED);
			}
		}
	}
	
	/**
	 * Print colored text in System.out
	 * @param output String to print
	 * @param color Color to use
	 */
	private static void printColored(String output, String color) {
		System.out.println(color + output + ANSI_RESET);
	}
}
