/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

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