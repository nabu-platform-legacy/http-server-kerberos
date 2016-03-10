# Kerberos

First things first: java wraps around a low level c implementation which is tightly coupled to the filesystem. I have tried to abstract away as much as possible from the filesystem but the fundamental kerberos configuration _has_ to live there. 

Upon installation in linux it is in general in `/etc/krb5.conf`. Java will also look for it in `<java-home>/lib/security`.
Additionally you can set a custom file location by setting the system property `java.security.krb5.conf`.

## Kerberos Server

First I created a new VM that would host the kerberos server.

The ubuntu wiki was (as per usual) very informative for installation: https://help.ubuntu.com/community/Kerberos

```
$ sudo apt-get install krb5-kdc krb5-admin-server
```

Because this was the server, I did not actually fill in most of the requested fields when it was asking for (presumably upstream?) kerberos servers.

My default realm is called `test` and it is hosted on a static internal ip.

We create the kerberos database using:

```
$ krb5_newrealm
```

Next we log into the kadmin control panel:

```
$ sudo kadmin.local
# We add a user
addprinc alex
# We add a service and we'll be using a keytab instead of username/password
addprinc http/full.domain@test
# Quit the interactive shell
quit
```

The `@test` at the end is optional, if not given it will take the default realm. This is the same for the user principal.

Kerberos is finnicky about DNS and it is **very** important that the `full.domain` can be looked up through DNS (can be in the hosts file) _and_ it should also be able to find the fully qualified name based on a reverse lookup of the IP. The latter is rather hard to ensure if you are working with locally scoped hosts and don't want a full fledged DNS system, so you can turn this off in the kerberos configuration file:

`
[libdefaults]
        rdns = false
`

In theory this should only have to be configured on the clients, not the server. Doesn't hurt though.

### Key Tabs

The above assumes that the service will also be logging in with username/password, if you want to go for a keytab, do this:

```
# Generate a random key (not entirely necessary but hey)
addprinc -randkey http/full.domain@test
# Export to a keytab file
ktadd -k /etc/my.keytab http/full.domain@test
```

You then have to transfer the keytab file to the server that will be using it and preferably set it to `0700` permissions. The reason nabu doesn't do this is because it again couples the code to the filesystem.

See below how to reference the keytab file.

## Client 

On the client machine I run:

```
$ sudo apt-get install krb5-user
```

It will prompt during installation for some parameters regarding the default realm "test" (see above) and the server hosting it (the static IP mentioned above).

### Firefox

Now that we have kerberos setup on linux and we have a ticket-granting-ticket (TGT), we still need firefox to use it to resolve tickets. Unfortunately you need to delve into "about:config" for this. Search for "nego" and update these two (normally empty) fields:

- network.negotiate-auth.delegation-uris
- network.negotiate-auth.trusted-uris

The value is a comma separated list of domains that firefox should attempt kerberos authentication on, in this example set `full.domain` assuming you also browse to `http://full.domain/` which has been correctly fixed in the hosts file.

## JAAS

This library uses JAAS to connect to kerberos. Some interesting things to note:

- The class used to connect is hardcoded as a sun class, not sure if there are "generic" options: com.sun.security.auth.module.Krb5LoginModule
- If you want to use the keytab, you have to have these two options:
	- useKeyTab = true
	- keyTab = file:/home/alex/tmp/nirrti.internal.keytab
- I'm not entirely sure what the setting "isInitiator" does exactly but it limits initial interaction with the kerberos server while everything keeps working
- storeKey **has** to be set to true, otherwise the code will complain at a later time that it doesn't have the key
- You can set the system property `sun.security.krb5.debug` to `true` to enable debug logging of the kerberos stuff
- [This page](https://docs.oracle.com/javase/7/docs/jre/api/security/jaas/spec/com/sun/security/auth/module/Krb5LoginModule.html) contains the information for the possible settings 

# General Flow

Currently setting up a new pc for this ad-hoc kerberos network consists of installing the client software and then running:

```
$ kinit -p alex@test
```

Note that this yields a TGT with a validity of default ~11 hours. Which is nice but not all that awesome. If the TGT is gone, firefox simply does not try to negotiate, it simply stops after the initial 401.
So make sure there is a valid ticket by running `klist` and only then will firefox start to negotiate.

If you want to request a ticket for a specific service you can do:

```
$ kvno service
```

## Known Issues

### Caching

I wanted to switch a server from keytab to user/password behavior (two different services with different subdomains but the same target ip) but the browser (which worked perfectly with the keytab) kept throwing "checksum failed" exceptions with the username/password combination.
I tried every combination of `kdestroy`, `kinit`and `kvno` to try and rectify the tickets but alas it kept failing.

Also curiously: after a kdestroy & kinit, if the browser went to the page (and generated the error), the klist would show the old entry, **not** the new one.
I quickly checked on another pc and it indeed had no problem with the new service.

After a combination of kdestroy, manually deleting the cache file `/tmp/krb5cc_%{uid}` and updating the hosts to get rid of old subdomain reference that was listed before the new one in the hosts, it finally worked. 

### Reverse DNS

This has already been mentioned above but can not be emphasized enough: the full domain **must be resolvable** and either also reverse resolvable or "rdns=false" in the client kerberos configuration.

Additionally I have noticed that the domain **must** exist of multiple parts, in the beginning I was simply testing with "mytest", which through the host file resolved to the proper local IP (not 127.0.0.1).
However it would not work until I updated the line in the host file:

```
10.0.0.1 mytest.internal mytest
```

As you can see, I added a "fully qualified" version of the mytest simply by adding a (fictional) extension. If you register the service with that extension everything works. It seems a simplistic "does it have a dot?" check.

## Random Links

- http://docs.oracle.com/javase/7/docs/technotes/guides/security/jgss/tutorials/AcnOnly.html#ConfigFile
- https://help.ubuntu.com/community/Kerberos
- http://spnego.sourceforge.net/