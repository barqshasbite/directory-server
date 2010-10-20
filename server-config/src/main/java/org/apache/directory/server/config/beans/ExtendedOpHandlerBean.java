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
package org.apache.directory.server.config.beans;


/**
 * A class used to store the LdapServerExtendedOpHandler configuration.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class ExtendedOpHandlerBean
{
    /** The Extended Operation ID */
    private String extendedopid;
    
    /** The Extended Operation FQCN */
    private String extendedophandlerclass;

    /**
     * Create a new LdapServerExtendedOpHandlerBean instance
     */
    public ExtendedOpHandlerBean()
    {
        super();
    }

    
    /**
     * @return the extendedOpId
     */
    public String getExtendedOpId()
    {
        return extendedopid;
    }

    
    /**
     * @param extendedOpId the extendedOpId to set
     */
    public void setExtendedOpId( String extendedOpId )
    {
        this.extendedopid = extendedOpId;
    }

    
    /**
     * @return the ldapServerExtendedOpHandlerClass
     */
    public String getExtendedOpHandlerClass()
    {
        return extendedophandlerclass;
    }

    
    /**
     * @param extendedOpHandlerClass the ExtendedOpHandlerClass to set
     */
    public void setExtendedOpHandlerClass( String extendedOpHandlerClass )
    {
        this.extendedophandlerclass = extendedOpHandlerClass;
    }
}
