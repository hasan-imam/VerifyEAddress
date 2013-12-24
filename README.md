Context:
----
You have some email addresses and you would like to know if they are real or fake.

Solution: 
----
This program! Give it SMTP server name/address and list of address to check and it will tell you if they are real or fake. It will also warn you if the answer seems unreliable. 

CLI usage:
----
`java CheckAddress <domain name> <SMTP server> [<email address>...]`

Or, using the runnable .jar 

`java -jar VerifyEAddress.jar <domain name> <SMTP server> [<email address>...]`

 - domain name: domain name of the server (e.g. gmail.com)
 - SMTP server: a server for the domain (e.g. alt1.gmail-smtp-in.l.google.com)
 - email address: email address in the specified domain (e.g. user.name@gmail.com)

API usage:
----
Import mail.validation.AddressVerifier. Check javadocs for details.

Caveats:
----
Some SMTP servers does not have the user list. They simple catch all emails (including the ones with invalid recepient address) and forward them to the real server. If you are using such server, your answers will be positive even for bogus email addresses. This program tries to give you a warning when that might be the case. 

If I can't find the real server, I just send a black email to an invalid address. This results in a report email saying that the delivery failed. The address of the sender usually gives away the *man behind the curtain*. If that does not work, try (mail | webmail | smtp).domain.com

Also, note that the program does not do MX lookup. So, do an nslookup first to get the server list. Example: `nslookup -type=MX google.con`. In the of this command, notice the priority of the servers. Use these servers in the order of highest to lowest preference. 

Finally, modify the log4j.properties file to set the level of messeges you would like to see. Set to OFF to see only the final output.

Troubleshoot:
----
**Symptom:** Program failing with timeouts.

**Possible explanations:**

1. Many ISPs block traffic on port 25 to make sure residential lines are not being used for spamming. In this case, use the ISP's SMTP server or use a static connection which usually do not have this issue.
2. You have some local firewall setting that is stopping the communication on port 25.
 
Usage notice:
----
This is for education purposes only (hence this documentation, javadocs and the logging). Please do not abuse it in any way. 
