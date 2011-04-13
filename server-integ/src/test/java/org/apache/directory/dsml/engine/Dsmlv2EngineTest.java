/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.directory.dsml.engine;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.ldap.client.api.LdapNetworkConnection;
import org.apache.directory.server.annotations.CreateLdapServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.integ.AbstractLdapTestUnit;
import org.apache.directory.server.core.integ.FrameworkRunner;
import org.apache.directory.shared.dsmlv2.Dsmlv2ResponseParser;
import org.apache.directory.shared.dsmlv2.engine.Dsmlv2Engine;
import org.apache.directory.shared.dsmlv2.reponse.BatchResponseDsml;
import org.apache.directory.shared.dsmlv2.reponse.SearchResponse;
import org.apache.directory.shared.ldap.codec.api.LdapCodecServiceFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;


/**
 * Test for demonstrating the NPE generated while processing a search DSML request by Dsmlv2Engine.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
@RunWith(FrameworkRunner.class)
@CreateDS(name = "Dsmlv2EngineTest-DS")
@CreateLdapServer(transports =
    { @CreateTransport(protocol = "LDAP") })
public class Dsmlv2EngineTest extends AbstractLdapTestUnit
{
    private LdapConnection connection;

    private Dsmlv2Engine engine;


    @Before
    public void setup()
    {
        connection = new LdapNetworkConnection( "localhost", ldapServer.getPort() );
        engine = new Dsmlv2Engine( connection, "uid=admin,ou=system", "secret" );
    }


    @After
    public void unbind() throws Exception
    {
        connection.unBind();
        connection.close();
    }


    //Enable WARN level logging to see the stacktrace
    // e.x log4j.rootCategory=WARN, stdout
    @Ignore("Failes with an NPE at org.apache.directory.shared.ldap.codec.decorators.SearchRequestDecorator.computeLength(SearchRequestDecorator.java:939)")
    @Test
    public void testEngineWithDefaultBlockingResponse() throws Exception
    {
        InputStream dsmlIn = getClass().getClassLoader().getResourceAsStream( "dsml-search-req.xml" );

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();

        engine.processDSML( dsmlIn, byteOut );

        Dsmlv2ResponseParser respParser = new Dsmlv2ResponseParser( LdapCodecServiceFactory.getSingleton() );
        System.out.println( byteOut.toString() );
        respParser.setInput( byteOut.toString() );

        respParser.parseAllResponses();

        BatchResponseDsml batchResp = respParser.getBatchResponse();

        assertNotNull( batchResp );

        SearchResponse searchResp = ( SearchResponse ) batchResp.getCurrentResponse().getDecorated();

        assertEquals( 5, searchResp.getSearchResultEntryList().size() );
    }
}