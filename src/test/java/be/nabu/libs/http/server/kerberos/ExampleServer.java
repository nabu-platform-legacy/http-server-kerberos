package be.nabu.libs.http.server.kerberos;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import be.nabu.libs.authentication.jaas.JAASConfiguration;
import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.events.impl.EventDispatcherImpl;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.server.HTTPServer;
import be.nabu.libs.http.core.DefaultHTTPResponse;
import be.nabu.libs.http.server.HTTPServerUtils;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

public class ExampleServer {
	
	public static void main(String...args) throws IOException {
		if (args.length < 1) {
			throw new RuntimeException("Need to pass in the path to the kerberos configuration file");
		}
		String filePath = args[0];
		System.setProperty("java.security.krb5.conf", filePath);
//		System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
//		System.setProperty("java.security.auth.login.config", "/home/alex/jaas.conf");
		System.setProperty("sun.security.krb5.debug", "true");
		HTTPServer newServer = HTTPServerUtils.newServer(4001, 20, new EventDispatcherImpl());
		
		// programmatically maintain the JAAS configuration
		JAASConfiguration.register();
		JAASConfiguration instance = JAASConfiguration.getInstance();
		Map<String, String> options = noStoreOptions();
//		Map<String, String> options = storeOptions();
		instance.configure("my.example", JAASConfiguration.newKerberosEntry(options));
		
		newServer.getDispatcher().subscribe(HTTPRequest.class, new KerberosAuthenticator("my.example"));
		newServer.getDispatcher().subscribe(HTTPRequest.class, new EventHandler<HTTPRequest, HTTPResponse>() {
			@Override
			public HTTPResponse handle(HTTPRequest event) {
				System.out.println(">>>>>> YAY!!");
				return new DefaultHTTPResponse(200, "OK", new PlainMimeEmptyPart(null, new MimeHeader("Content-Length", "0")));
			}
		});
		newServer.start();
	}

	public static Map<String, String> storeOptions() {
		Map<String, String> options = new HashMap<String, String>();
		options.put("storeKey", "true");
		options.put("useKeyTab", "true");
		options.put("keyTab", "file:/home/alex/tmp/nirrti.internal.keytab");
		options.put("principal", "HTTP/nirrti.internal@test");
		options.put("isInitiator", "false");
		return options;
	}
	
	public static Map<String, String> noStoreOptions() {
		Map<String, String> options = new HashMap<String, String>();
		options.put("storeKey", "true");
		options.put("useKeyTab", "false");
		options.put("principal", "HTTP/nirrti2.internal@test");
//		options.put("doNotPrompt", "true");
//		options.put("tryFirstPass", "true");
//        options.put("useTicketCache", "true");
//        options.put("renewTGT", "true");
        options.put("isInitiator", "false");
		return options;
	}
}
