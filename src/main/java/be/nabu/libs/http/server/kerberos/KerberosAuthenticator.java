package be.nabu.libs.http.server.kerberos;

import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import be.nabu.libs.events.api.EventHandler;
import be.nabu.libs.http.HTTPCodes;
import be.nabu.libs.http.HTTPException;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.server.RealmHandler;
import be.nabu.libs.http.core.DefaultHTTPResponse;
import be.nabu.libs.http.core.HTTPUtils;
import be.nabu.libs.http.core.ServerHeader;
import be.nabu.libs.http.server.SimpleAuthenticationHeader;
import be.nabu.utils.codec.TranscoderUtils;
import be.nabu.utils.codec.impl.Base64Decoder;
import be.nabu.utils.codec.impl.Base64Encoder;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.mime.api.Header;
import be.nabu.utils.mime.impl.MimeHeader;
import be.nabu.utils.mime.impl.MimeUtils;
import be.nabu.utils.mime.impl.PlainMimeEmptyPart;

/**
 * Important!
 * - Because java wraps around low level c libraries for kerberos support, the kerberos configuration file _must_ exist on the filesystem, by default either in /etc/krb5.conf or the same file in <java-home>/lib/security
 * - You can configure where it is by setting: java.security.krb5.conf
 * - More information: https://docs.oracle.com/javase/8/docs/technotes/guides/security/jgss/tutorials/KerberosReq.html
 * 
 * More links:
 * - http://stackoverflow.com/questions/25289231/using-gssmanager-to-validate-a-kerberos-ticket
 * - http://stackoverflow.com/questions/16474649/can-not-use-kerberos-ticket-to-create-hadoop-file-with-java-code
 * - https://hc.apache.org/httpcomponents-client-ga/tutorial/html/authentication.html 
 */
public class KerberosAuthenticator implements EventHandler<HTTPRequest, HTTPResponse> {

	private static Oid SPNEGO_OID; 
	
	static {
		try {
			SPNEGO_OID = new Oid("1.3.6.1.5.5.2");
		}
		catch (GSSException e) {
			throw new RuntimeException(e);
		}
	}
	public static final String NTLM_PROLOG = "TlRMTVNT";
	
	private boolean required = true;
	
	private int lifetime = GSSCredential.DEFAULT_LIFETIME;

	private RealmHandler realmHandler;
	private LoginContext loginContext;
	
	public KerberosAuthenticator(String jaasConfigurationName, RealmHandler realmHandler) {
		this.realmHandler = realmHandler;
		initialize(jaasConfigurationName);
	}
	
	public KerberosAuthenticator(String jaasConfigurationName) {
		this(jaasConfigurationName, null);
	}
	
	private void initialize(String jaasConfigurationName) {
		try {
			String realm = "test";
			loginContext = new LoginContext(jaasConfigurationName, newPasswordHandler("HTTP/nirrti2.internal@" + realm, "alex"));
			loginContext.login();
		}
		catch (LoginException e) {
			throw new RuntimeException(e);
		}
	}

	private static CallbackHandler newPasswordHandler(final String username, final String password) {
		return new CallbackHandler() {
			public void handle(final Callback[] callback) {
				for (int i = 0; i < callback.length; i++) {
					if (callback[i] instanceof NameCallback) {
						((NameCallback) callback[i]).setName(username);
					}
					else if (callback[i] instanceof PasswordCallback) {
						((PasswordCallback) callback[i]).setPassword(password.toCharArray());
					}
					else {
						throw new RuntimeException("Unsupported callback: " + callback[i].getClass().getName());
					}
				}
			}
		};
	}
	
	private GSSCredential getServerCredential(final GSSManager manager) {
		try {
			final PrivilegedExceptionAction<GSSCredential> action = new PrivilegedExceptionAction<GSSCredential>() {
				public GSSCredential run() throws GSSException {
					return manager.createCredential(
						null,
						lifetime, 
						SPNEGO_OID,
						GSSCredential.ACCEPT_ONLY
					);
				}
			};
			return Subject.doAs(loginContext.getSubject(), action);
		}
		catch (PrivilegedActionException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public HTTPResponse handle(HTTPRequest request) {
		try {
			if (request.getContent() != null) {
				Header header = MimeUtils.getHeader("Authorization", request.getContent().getHeaders());
				if (header != null && header.getValue().substring(0, 9).equalsIgnoreCase("Negotiate")) {
					HTTPUtils.setHeader(request.getContent(), ServerHeader.AUTHENTICATION_SCHEME, "kerberos");
					String token = header.getValue().substring("Negotiate".length()).trim();
					if (!token.isEmpty()) {
						// it has fallen back to NTLM which we can't support
						if (token.startsWith(NTLM_PROLOG)) {
							if (required) {
								throw new HTTPException(400, "There is no support for NTLM");
							}
							// we could (at a higher level) resend a WWW-Authenticate with for example "Basic" as option instead of failing
							else {
								return null;
							}
						}
						byte [] decoded = IOUtils.toBytes(TranscoderUtils.transcodeBytes(
							IOUtils.wrap(token.getBytes("ASCII"), true), 
							new Base64Decoder())
						);
						GSSManager manager = GSSManager.getInstance();
						GSSCredential credential = getServerCredential(manager);
						GSSContext context = manager.createContext(credential);
						byte[] acceptedContext = context.acceptSecContext(decoded, 0, decoded.length);
						// we are not done negotiating yet
						if (!context.isEstablished()) {
							return newAuthenticationRequiredResponse(request, acceptedContext);
						}
						else {
							// in theory there could still be bytes in the acceptedContext that have to make it back to the peer
							// however, we can't send something back right now because we need to process the original request
							// TODO: maybe at some point check if we can tag it in the request so the response handler can extract it and add it there somewhere

							// the method returns in seconds
							final long lifetime = 1000l * context.getLifetime();
							final GSSName srcName = context.getSrcName();
							// it is possible that the src name is null even though the context is established
							// in this case _something_ has gone wrong, most likely candidate is DNS:
							// kerberos expects by default that the domain used is forwards and reverse resolvable using DNS, the former can be done in hosts file, the latter can not, hence disable it (on the client) when using non-dnsed servers
							if (srcName == null) {
								throw new HTTPException(500, "Something went wrong in the kerberos handshake, try disabling reverse dns lookup on the client (set rdns=false in the kerberos config file)");
							}
							request.getContent().setHeader(new SimpleAuthenticationHeader(new KerberosToken(
								realmHandler == null ? "default" : realmHandler.getRealm(request), 
								lifetime, 
								context.getCredDelegState() ? context.getDelegCred() : null, 
								new KerberosPrincipal(srcName.toString(), KerberosPrincipal.KRB_NT_PRINCIPAL))
							));
							context.dispose();
							return null;
						}
					}
				}
			}
			return required ? newAuthenticationRequiredResponse(request, null) : null;
		}
		catch (IOException e) {
			throw new HTTPException(500, e);
		}
		catch (RuntimeException e) {
			throw new HTTPException(401, e);
		}
		catch (GSSException e) {
			throw new HTTPException(500, e);
		}
	}
	
	public static HTTPResponse newAuthenticationRequiredResponse(HTTPRequest request, byte [] token) throws IOException {
		return new DefaultHTTPResponse(request, 401, HTTPCodes.getMessage(401), new PlainMimeEmptyPart(null, 
			new MimeHeader("Content-Length", "0"),
			new MimeHeader("WWW-Authenticate", token == null ? "Negotiate" : "Negotiate " + new String(IOUtils.toBytes(TranscoderUtils.transcodeBytes(IOUtils.wrap(token, true), new Base64Encoder()))))
		));
	}

	public int getLifetime() {
		return lifetime;
	}
	public void setLifetime(int lifetime) {
		this.lifetime = lifetime;
	}
	
}
