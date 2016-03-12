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
