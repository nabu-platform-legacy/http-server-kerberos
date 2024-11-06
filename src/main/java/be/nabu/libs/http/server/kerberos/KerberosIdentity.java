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

public class KerberosIdentity {
	private boolean anonymous;
	private String name;
	private long lifetime;
	
	public KerberosIdentity() {
		// autoconstruct
	}
	
	public KerberosIdentity(String name, boolean anonymous, long lifetime) {
		this.name = name;
		this.anonymous = anonymous;
		this.lifetime = lifetime;
	}

	public boolean isAnonymous() {
		return anonymous;
	}
	public void setAnonymous(boolean anonymous) {
		this.anonymous = anonymous;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public long getLifetime() {
		return lifetime;
	}
	public void setLifetime(long lifetime) {
		this.lifetime = lifetime;
	}

}
