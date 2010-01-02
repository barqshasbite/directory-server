/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.ssl;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import javax.naming.AuthenticationNotSupportedException;
import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.StartTlsRequest;
import javax.naming.ldap.StartTlsResponse;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.CoreSession;
import org.apache.directory.server.core.entry.ClonedServerEntry;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.server.core.security.TlsKeyGenerator;
import org.apache.directory.server.integ.ServerIntegrationUtils;
import org.apache.directory.server.ldap.handlers.extended.StartTlsHandler;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Test case to verify proper operation of confidentiality requirements as 
 * specified in https://issues.apache.org/jira/browse/DIRSERVER-1189.  
 * 
 * Starts up the server binds via SUN JNDI provider to perform various 
 * operations on entries which should be rejected when a TLS secured 
 * connection is not established.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev: 639006 $
 */
@RunWith ( FrameworkRunner.class ) 
@CreateLdapServer ( 
    transports = 
    {
        @CreateTransport( protocol = "LDAP" ),
        @CreateTransport( protocol = "LDAPS" )
    },
    extendedOpHandlers={ StartTlsHandler.class }
    )
public class StartTlsIT extends AbstractLdapTestUnit
{
    private static final Logger LOG = LoggerFactory.getLogger( StartTlsIT.class );
    private static final String[] CERT_IDS = new String[] { "userCertificate" };
    private static final int CONNECT_ITERATIONS = 10;
    private static final boolean VERBOSE = false;
    private File ksFile;

    
    boolean oldConfidentialityRequiredValue;
    
    
    /**
     * Sets up the key store and installs the self signed certificate for the 
     * server (created on first startup) which is to be used by the StartTLS 
     * JDNDI client that will connect.  The key store is created from scratch
     * programmatically and whipped on each run.  The certificate is acquired 
     * by pulling down the bytes for administrator's userCertificate from 
     * uid=admin,ou=system.  We use sysRoot direct context instead of one over
     * the wire since the server is configured to prevent connections without
     * TLS secured connections.
     */
    @Before
    public void installKeyStoreWithCertificate() throws Exception
    {
        if ( ksFile != null && ksFile.exists() )
        {
            ksFile.delete();
        }
        
        ksFile = File.createTempFile( "testStore", "ks" );
        CoreSession session = ldapServer.getDirectoryService().getAdminSession();
        ClonedServerEntry entry = session.lookup( new LdapDN( "uid=admin,ou=system" ), CERT_IDS );
        byte[] userCertificate = entry.get( CERT_IDS[0] ).getBytes();
        assertNotNull( userCertificate );

        ByteArrayInputStream in = new ByteArrayInputStream( userCertificate );
        CertificateFactory factory = CertificateFactory.getInstance( "X.509" );
        Certificate cert = factory.generateCertificate( in );
        KeyStore ks = KeyStore.getInstance( KeyStore.getDefaultType() );
        ks.load( null, null );
        ks.setCertificateEntry( "apacheds", cert );
        ks.store( new FileOutputStream( ksFile ), "changeit".toCharArray() );
        LOG.debug( "Keystore file installed: {}", ksFile.getAbsolutePath() );
        
        oldConfidentialityRequiredValue = ldapServer.isConfidentialityRequired();
    }
    
    
    /**
     * Just deletes the generated key store file.
     */
    @After
    public void deleteKeyStore() throws Exception
    {
        if ( ksFile != null && ksFile.exists() )
        {
            ksFile.delete();
        }
        
        LOG.debug( "Keystore file deleted: {}", ksFile.getAbsolutePath() );
        ldapServer.setConfidentialityRequired( oldConfidentialityRequiredValue );
    }
    

    private LdapContext getSecuredContext() throws Exception
    {
        System.setProperty ( "javax.net.ssl.trustStore", ksFile.getAbsolutePath() );
        System.setProperty ( "javax.net.ssl.keyStore", ksFile.getAbsolutePath() );
        System.setProperty ( "javax.net.ssl.keyStorePassword", "changeit" );
        LOG.debug( "testStartTls() test starting ... " );
        
        // Set up environment for creating initial context
        Hashtable<String, Object> env = new Hashtable<String,Object>();
        env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
        
        // Must use the name of the server that is found in its certificate?
        env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapServer.getPort() );

        // Create initial context
        LOG.debug( "About to get initial context" );
        LdapContext ctx = new InitialLdapContext( env, null );

        // Start TLS
        LOG.debug( "About send startTls extended operation" );
        StartTlsResponse tls = ( StartTlsResponse ) ctx.extendedOperation( new StartTlsRequest() );
        LOG.debug( "Extended operation issued" );
        tls.setHostnameVerifier( new HostnameVerifier() {
            public boolean verify( String hostname, SSLSession session )
            {
                return true;
            } 
        } );
        LOG.debug( "TLS negotion about to begin" );
        tls.negotiate();
        return ctx;
    }
    

    /**
     * Checks to make sure insecure binds fail while secure binds succeed.
     */
    @Test
    public void testConfidentiality() throws Exception
    {
        ldapServer.setConfidentialityRequired( true );

        // -------------------------------------------------------------------
        // Unsecured bind should fail
        // -------------------------------------------------------------------

        try
        {
            ServerIntegrationUtils.getWiredContext( ldapServer );
            fail( "Should not get here due to violation of confidentiality requirements" );
        }
        catch( AuthenticationNotSupportedException e )
        {
        }
        
        // -------------------------------------------------------------------
        // get anonymous connection with StartTLS (no bind request sent)
        // -------------------------------------------------------------------

        LdapContext ctx = getSecuredContext();
        assertNotNull( ctx );
        
        // -------------------------------------------------------------------
        // upgrade connection via bind request (same physical connection - TLS)
        // -------------------------------------------------------------------

        ctx.addToEnvironment( Context.SECURITY_PRINCIPAL, "uid=admin,ou=system" );
        ctx.addToEnvironment( Context.SECURITY_CREDENTIALS, "secret" );
        ctx.addToEnvironment( Context.SECURITY_AUTHENTICATION, "simple" );
        ctx.reconnect( null );
        
        // -------------------------------------------------------------------
        // do a search and confirm
        // -------------------------------------------------------------------

        NamingEnumeration<SearchResult> results = ctx.search( "ou=system", "(objectClass=*)", new SearchControls() );
        Set<String> names = new HashSet<String>();
        while( results.hasMore() )
        {
            names.add( results.next().getName() );
        }
        results.close();
        assertTrue( names.contains( "prefNodeName=sysPrefRoot" ) );
        assertTrue( names.contains( "ou=users" ) );
        assertTrue( names.contains( "ou=configuration" ) );
        assertTrue( names.contains( "uid=admin" ) );
        assertTrue( names.contains( "ou=groups" ) );
        
        // -------------------------------------------------------------------
        // do add and confirm
        // -------------------------------------------------------------------

        Attributes attrs = new BasicAttributes( "objectClass", "person", true );
        attrs.put( "sn", "foo" );
        attrs.put( "cn", "foo bar" );
        ctx.createSubcontext( "cn=foo bar,ou=system", attrs );
        assertNotNull( ctx.lookup( "cn=foo bar,ou=system" ) );
        
        // -------------------------------------------------------------------
        // do modify and confirm
        // -------------------------------------------------------------------

        ModificationItem[] mods = new ModificationItem[] {
                new ModificationItem( DirContext.ADD_ATTRIBUTE, new BasicAttribute( "cn", "fbar" ) )
        };
        ctx.modifyAttributes( "cn=foo bar,ou=system", mods );
        Attributes reread = ( Attributes ) ctx.getAttributes( "cn=foo bar,ou=system" );
        assertTrue( reread.get( "cn" ).contains( "fbar" ) );
        
        // -------------------------------------------------------------------
        // do rename and confirm 
        // -------------------------------------------------------------------

        ctx.rename( "cn=foo bar,ou=system", "cn=fbar,ou=system" );
        try
        {
            ctx.getAttributes( "cn=foo bar,ou=system" );
            fail( "old name of renamed entry should not be found" );
        }
        catch ( NameNotFoundException e )
        {
        }
        reread = ( Attributes ) ctx.getAttributes( "cn=fbar,ou=system" );
        assertTrue( reread.get( "cn" ).contains( "fbar" ) );
        
        // -------------------------------------------------------------------
        // do delete and confirm
        // -------------------------------------------------------------------

        ctx.destroySubcontext( "cn=fbar,ou=system" );
        try
        {
            ctx.getAttributes( "cn=fbar,ou=system" );
            fail( "deleted entry should not be found" );
        }
        catch ( NameNotFoundException e )
        {
        }
        
        ctx.close();
    }


    private void search( int ii, LdapContext securedContext ) throws Exception
    {
        SearchControls controls = new SearchControls();
        controls.setSearchScope( SearchControls.SUBTREE_SCOPE );
        
        if ( VERBOSE )
        {
            System.out.println( "Searching on " + ii + "-th iteration:" );
        }
        
        List<String> results = new ArrayList<String>();
        NamingEnumeration<SearchResult> ne = securedContext.search( "ou=system", "(objectClass=*)", controls );
        while ( ne.hasMore() )
        {
            String dn = ne.next().getNameInNamespace();
            results.add( dn );
            
            if ( VERBOSE )
            {
                System.out.println( "\tSearch Result = " + dn );
            }
        }
        ne.close();
        
        assertEquals( "ou=system", results.get( 0 ) );
        assertEquals( "uid=admin,ou=system", results.get( 1 ) );
        assertEquals( "ou=users,ou=system", results.get( 2 ) );
        assertEquals( "ou=groups,ou=system", results.get( 3 ) );
        assertEquals( "cn=Administrators,ou=groups,ou=system", results.get( 4 ) );
        assertEquals( "ou=configuration,ou=system", results.get( 5 ) );
        assertEquals( "ou=partitions,ou=configuration,ou=system", results.get( 6 ) );
        assertEquals( "ou=services,ou=configuration,ou=system", results.get( 7 ) );
        assertEquals( "ou=interceptors,ou=configuration,ou=system", results.get( 8 ) );
        assertEquals( "prefNodeName=sysPrefRoot,ou=system", results.get( 9 ) );
    }
    
    
    /**
     * Tests StartTLS by creating a JNDI connection using the generated key 
     * store with the installed self signed certificate.  It then searches 
     * the server and verifies the presence of the expected entries and closes
     * the connection.  This process repeats for a number of iterations.  
     * Modify the CONNECT_ITERATIONS constant to change the number of 
     * iterations.  Modify the VERBOSE constant to print out information while
     * performing searches.
     */
    @Test
    public void testStartTls() throws Exception
    {
        for ( int ii = 0; ii < CONNECT_ITERATIONS; ii++ )
        {
            if ( VERBOSE )
            {
                System.out.println( "Performing " + ii + "-th iteration to connect via StartTLS." );
            }

            System.setProperty ( "javax.net.ssl.trustStore", ksFile.getAbsolutePath() );
            System.setProperty ( "javax.net.ssl.keyStore", ksFile.getAbsolutePath() );
            System.setProperty ( "javax.net.ssl.keyStorePassword", "changeit" );
            LOG.debug( "testStartTls() test starting ... " );
            
            // Set up environment for creating initial context
            Hashtable<String, Object> env = new Hashtable<String,Object>();
            env.put( Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory" );
            env.put( "java.naming.security.principal", "uid=admin,ou=system" );
            env.put( "java.naming.security.credentials", "secret" );
            env.put( "java.naming.security.authentication", "simple" );
            
            // Must use the name of the server that is found in its certificate?
            env.put( Context.PROVIDER_URL, "ldap://localhost:" + ldapServer.getPort() );
    
            // Create initial context
            LOG.debug( "About to get initial context" );
            LdapContext ctx = new InitialLdapContext( env, null );
    
            // Start TLS
            LOG.debug( "About send startTls extended operation" );
            StartTlsResponse tls = ( StartTlsResponse ) ctx.extendedOperation( new StartTlsRequest() );
            LOG.debug( "Extended operation issued" );
            tls.setHostnameVerifier( new HostnameVerifier() {
                public boolean verify( String hostname, SSLSession session )
                {
                    return true;
                } 
            } );
            LOG.debug( "TLS negotion about to begin" );
            tls.negotiate();

            search( ii, ctx );
            
            tls.close();
            ctx.close();
        }
    }
    
    /**
     * Test for DIRSERVER-1373.
     */
    @Test
    public void testUpdateCertificate() throws Exception
    {
        // create a secure connection
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put( "java.naming.factory.initial", "com.sun.jndi.ldap.LdapCtxFactory" );
        env.put( "java.naming.provider.url", "ldap://localhost:" + ldapServer.getPort() );
        env.put( "java.naming.security.principal", "uid=admin,ou=system" );
        env.put( "java.naming.security.credentials", "secret" );
        env.put( "java.naming.security.authentication", "simple" );
        LdapContext ctx = new InitialLdapContext( env, null );
        StartTlsResponse tls = ( StartTlsResponse ) ctx.extendedOperation( new StartTlsRequest() );
        tls.setHostnameVerifier( new HostnameVerifier() {
            public boolean verify( String hostname, SSLSession session )
            {
                return true;
            } 
        } );
        tls.negotiate( BogusSSLContextFactory.getInstance( false ).getSocketFactory() );

        // create a new certificate
        String newIssuerDN = "cn=new_issuer_dn";
        String newSubjectDN = "cn=new_subject_dn";
        ServerEntry entry = ldapServer.getDirectoryService().getAdminSession().lookup(
            new LdapDN( "uid=admin,ou=system" ) );
        TlsKeyGenerator.addKeyPair( entry, newIssuerDN, newSubjectDN, "RSA" );

        // now update the certificate (over the wire)
        ModificationItem[] mods = new ModificationItem[3];
        mods[0] = new ModificationItem( DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(
            TlsKeyGenerator.PRIVATE_KEY_AT, entry.get( TlsKeyGenerator.PRIVATE_KEY_AT ).getBytes() ) );
        mods[1] = new ModificationItem( DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(
            TlsKeyGenerator.PUBLIC_KEY_AT, entry.get( TlsKeyGenerator.PUBLIC_KEY_AT ).getBytes() ) );
        mods[2] = new ModificationItem( DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(
            TlsKeyGenerator.USER_CERTIFICATE_AT, entry.get( TlsKeyGenerator.USER_CERTIFICATE_AT ).getBytes() ) );
        ctx.modifyAttributes( "uid=admin,ou=system", mods );
        ctx.close();

        ldapServer.reloadSslContext();
        
        // create a new secure connection
        ctx = new InitialLdapContext( env, null );
        tls = ( StartTlsResponse ) ctx.extendedOperation( new StartTlsRequest() );
        tls.setHostnameVerifier( new HostnameVerifier() {
            public boolean verify( String hostname, SSLSession session )
            {
                return true;
            } 
        } );
        tls.negotiate( BogusSSLContextFactory.getInstance( false ).getSocketFactory() );

        // check the received certificate, it must contain the updated server certificate
        X509Certificate[] lastReceivedServerCertificates = BogusTrustManagerFactory.lastReceivedServerCertificates;
        assertNotNull( lastReceivedServerCertificates );
        assertEquals( 1, lastReceivedServerCertificates.length );
        String issuerDN = lastReceivedServerCertificates[0].getIssuerDN().getName();
        String subjectDN = lastReceivedServerCertificates[0].getSubjectDN().getName();
        assertEquals( "Expected the new certificate with the new issuer", newIssuerDN.toLowerCase(), issuerDN.toLowerCase() );
        assertEquals( "Expected the new certificate with the new subject", newSubjectDN.toLowerCase(), subjectDN.toLowerCase() );
    }

}
