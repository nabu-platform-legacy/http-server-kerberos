package be.nabu.libs.http.server.kerberos;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.security.auth.kerberos.KerberosPrincipal;

import org.ietf.jgss.GSSCredential;

import be.nabu.libs.authentication.api.Token;

public class KerberosToken implements Token {

	private static final long serialVersionUID = 1L;
	private Date validUntil;
	private List<Principal> credentials = new ArrayList<Principal>();
	private String name;
	private GSSCredential delegate;
	private String realm;

	public KerberosToken() {
		// auto construct
	}
	
	KerberosToken(String realm, long lifetime, GSSCredential delegate, KerberosPrincipal principal) {
		this.realm = realm;
		this.delegate = delegate;
		this.name = principal.getName();
		this.validUntil = new Date(new Date().getTime() + lifetime);
		this.credentials.add(principal);
	}
	
	@Override
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getRealm() {
		return realm;
	}
	public void setRealm(String realm) {
		this.realm = realm;
	}

	@Override
	public Date getValidUntil() {
		return validUntil;
	}
	public void setValidUntil(Date validUntil) {
		this.validUntil = validUntil;
	}

	@Override
	public List<Principal> getCredentials() {
		return credentials;
	}
	public void setCredentials(List<Principal> credentials) {
		this.credentials = credentials;
	}

	public GSSCredential getDelegate() {
		return delegate;
	}
	public void setDelegate(GSSCredential delegate) {
		this.delegate = delegate;
	}
	
}