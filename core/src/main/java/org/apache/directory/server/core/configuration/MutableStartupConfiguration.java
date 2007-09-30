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
package org.apache.directory.server.core.configuration;


import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.directory.server.core.authn.Authenticator;


/**
 * A mutable version of {@link StartupConfiguration}.
 *
 * @org.apache.xbean.XBean
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$, $Date$
 */
public class MutableStartupConfiguration extends StartupConfiguration
{
    private static final long serialVersionUID = -987437370955222007L;


    /**
     * Creates a new instance.
     */
    public MutableStartupConfiguration()
    {
    }


    /**
     * Creates a new instance that operates on the {@link org.apache.directory.server.core.DirectoryService} with
     * the specified ID.
     */
    public MutableStartupConfiguration( String instanceId )
    {
        super( instanceId );
    }


    public void setSystemPartitionConfiguration( PartitionConfiguration systemPartitionConfiguration )
    {
        super.setSystemPartitionConfiguration( systemPartitionConfiguration );
    }


    public void setMaxThreads( int maxThreads )
    {
        super.setMaxThreads( maxThreads );
    }


    public void setInstanceId( String instanceId )
    {
        super.setInstanceId( instanceId );
    }

    /**
     * @org.apache.xbean.Property nestedType="org.apache.directory.server.core.configuration.PartitionConfiguration"
     *
     * @param paritionConfigurations partitions to start
     */
    public void setPartitionConfigurations( Set<? extends PartitionConfiguration> paritionConfigurations )
    {
        super.setPartitionConfigurations( paritionConfigurations );
    }


    public void setAccessControlEnabled( boolean accessControlEnabled )
    {
        super.setAccessControlEnabled( accessControlEnabled );
    }


    public void setAllowAnonymousAccess( boolean enableAnonymousAccess )
    {
        super.setAllowAnonymousAccess( enableAnonymousAccess );
    }

    /**
     * @org.apache.xbean.Property nestedType="org.apache.directory.server.core.configuration.InterceptorConfiguration"
     *
     * @param interceptorConfigurations
     */
    public void setInterceptors( List interceptorConfigurations )
    {
        super.setInterceptors( interceptorConfigurations );
    }

    /**
     * @org.apache.xbean.Property nestedType="org.apache.directory.shared.ldap.ldif.Entry"
     *
     * @param testEntries
     */
    public void setTestEntries( List testEntries )
    {
        super.setTestEntries( testEntries );
    }


    public void setWorkingDirectory( File workingDirectory )
    {
        super.setWorkingDirectory( workingDirectory );
    }


    public void setShutdownHookEnabled( boolean shutdownHookEnabled )
    {
        super.setShutdownHookEnabled( shutdownHookEnabled );
    }


    public void setExitVmOnShutdown( boolean exitVmOnShutdown )
    {
        super.setExitVmOnShutdown( exitVmOnShutdown );
    }


    public void setDenormalizeOpAttrsEnabled( boolean denormalizeOpAttrsEnabled )
    {
        super.setDenormalizeOpAttrsEnabled( denormalizeOpAttrsEnabled );
    }
}
