package be.nabu.libs.http.server.kerberos;

import javax.jws.WebParam;

import be.nabu.libs.authentication.api.Token;

public interface KerberosAuthenticator {
	public Token authenticate(@WebParam(name = "context") String context, @WebParam(name = "realm") String realm, @WebParam(name = "identity") KerberosIdentity identity);
}
