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
package org.apache.directory.server.core.event;


import java.util.Comparator;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import org.apache.directory.server.schema.registries.AttributeTypeRegistry;
import org.apache.directory.server.schema.registries.OidRegistry;
import org.apache.directory.shared.ldap.NotImplementedException;
import org.apache.directory.shared.ldap.filter.ApproximateNode;
import org.apache.directory.shared.ldap.filter.EqualityNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.shared.ldap.filter.ExtensibleNode;
import org.apache.directory.shared.ldap.filter.GreaterEqNode;
import org.apache.directory.shared.ldap.filter.LessEqNode;
import org.apache.directory.shared.ldap.filter.PresenceNode;
import org.apache.directory.shared.ldap.filter.ScopeNode;
import org.apache.directory.shared.ldap.filter.SimpleNode;
import org.apache.directory.shared.ldap.filter.SubstringNode;
import org.apache.directory.shared.ldap.schema.AttributeType;
import org.apache.directory.shared.ldap.schema.MatchingRule;
import org.apache.directory.shared.ldap.schema.Normalizer;
import org.apache.directory.shared.ldap.util.AttributeUtils;


/**
 * Evaluates LeafNode assertions on candidates using a database.
 * 
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class LeafEvaluator implements Evaluator
{
    /** equality matching type constant */
    private static final int EQUALITY_MATCH = 0;
    /** ordering matching type constant */
    private static final int ORDERING_MATCH = 1;
    /** substring matching type constant */
    private static final int SUBSTRING_MATCH = 3;

    /** Oid Registry used to translate attributeIds to OIDs */
    private OidRegistry oidRegistry;
    /** AttributeType registry needed for normalizing and comparing values */
    private AttributeTypeRegistry attributeTypeRegistry;
    /** Substring node evaluator we depend on */
    private SubstringEvaluator substringEvaluator;
    /** ScopeNode evaluator we depend on */
    private ScopeEvaluator scopeEvaluator;
    
    /** Constants used for comparisons */
    private static final boolean COMPARE_GREATER = true;
    private static final boolean COMPARE_LESSER = false;


    /**
     * Creates a leaf expression node evaluator.
     *
     * @param substringEvaluator
     */
    public LeafEvaluator( OidRegistry oidRegistry, AttributeTypeRegistry attributeTypeRegistry,
        SubstringEvaluator substringEvaluator )
    {
        this.oidRegistry = oidRegistry;
        this.attributeTypeRegistry = attributeTypeRegistry;
        this.scopeEvaluator = new ScopeEvaluator();
        this.substringEvaluator = substringEvaluator;
    }


    public ScopeEvaluator getScopeEvaluator()
    {
        return scopeEvaluator;
    }


    public SubstringEvaluator getSubstringEvaluator()
    {
        return substringEvaluator;
    }


    /**
     * @see Evaluator#evaluate(ExprNode, String, Attributes)
     */
    public boolean evaluate( ExprNode node, String dn, Attributes entry ) throws NamingException
    {
        if ( node instanceof ScopeNode )
        {
            return scopeEvaluator.evaluate( node, dn, entry );
        }

        if ( node instanceof PresenceNode )
        {
            String attrId = ((PresenceNode)node).getAttribute();
            return evalPresence( attrId, entry );
        }
        else if ( ( node instanceof EqualityNode ) || ( node instanceof ApproximateNode ) )
        {
        	return evalEquality( ( EqualityNode ) node, entry );
        }
        else if ( node instanceof GreaterEqNode )
        {
        	return evalGreaterOrLesser( ( GreaterEqNode ) node, entry, COMPARE_GREATER );
        }
        else if ( node instanceof LessEqNode )
        {
        	return evalGreaterOrLesser( ( LessEqNode ) node, entry, COMPARE_LESSER );
        }
        else if ( node instanceof SubstringNode )
        {
        	return substringEvaluator.evaluate( node, dn, entry );
        }
        else if ( node instanceof ExtensibleNode )
        {
        	throw new NotImplementedException();
        }
        else
        {
        	throw new NamingException( "Unrecognized leaf node type: " + node );
        }
    }


    /**
     * Evaluates a simple greater than or less than attribute value assertion on
     * a perspective candidate.
     * 
     * @param node the greater than or less than node to evaluate
     * @param entry the perspective candidate
     * @param isGreater true if it is a greater than or equal to comparison,
     *      false if it is a less than or equal to comparison.
     * @return the ava evaluation on the perspective candidate
     * @throws javax.naming.NamingException if there is a database access failure
     */
    private boolean evalGreaterOrLesser( SimpleNode node, Attributes entry, boolean isGreaterOrLesser ) throws NamingException
    {
        String attrId = node.getAttribute();

        // get the attribute associated with the node
        AttributeType type = attributeTypeRegistry.lookup( oidRegistry.getOid( attrId ) );
        Attribute attr = AttributeUtils.getAttribute( entry, type );

        // If we do not have the attribute just return false
        if ( null == attr )
        {
            return false;
        }

        /*
         * We need to iterate through all values and for each value we normalize
         * and use the comparator to determine if a match exists.
         */
        Normalizer normalizer = getNormalizer( attrId );
        Comparator comparator = getComparator( attrId );
        Object filterValue = normalizer.normalize( node.getValue() );
        NamingEnumeration list = attr.getAll();

        /*
         * Cheaper to not check isGreater in one loop - better to separate
         * out into two loops which you choose to execute based on isGreater
         */
        if ( isGreaterOrLesser == COMPARE_GREATER )
        {
            while ( list.hasMore() )
            {
                Object value = normalizer.normalize( list.next() );

                // Found a value that is greater than or equal to the ava value
                if ( 0 >= comparator.compare( value, filterValue ) )
                {
                    return true;
                }
            }
        }
        else
        {
            while ( list.hasMore() )
            {
                Object value = normalizer.normalize( list.next() );

                // Found a value that is less than or equal to the ava value
                if ( 0 <= comparator.compare( value, filterValue ) )
                {
                    return true;
                }
            }
        }

        // no match so return false
        return false;
    }


    /**
     * Evaluates a simple presence attribute value assertion on a perspective
     * candidate.
     * 
     * @param attrId the name of the attribute tested for presence 
     * @param entry the perspective candidate
     * @return the ava evaluation on the perspective candidate
     */
    private boolean evalPresence( String attrId, Attributes entry ) throws NamingException
    {
        if ( entry == null )
        {
            return false;
        }

        AttributeType type = attributeTypeRegistry.lookup( oidRegistry.getOid( attrId ) );
        return null != AttributeUtils.getAttribute( entry, type );
    }


    /**
     * Evaluates a simple equality attribute value assertion on a perspective
     * candidate.
     *
     * @param node the equality node to evaluate
     * @param entry the perspective candidate
     * @return the ava evaluation on the perspective candidate
     * @throws javax.naming.NamingException if there is a database access failure
     */
    private boolean evalEquality( EqualityNode node, Attributes entry ) throws NamingException
    {
        Normalizer normalizer = getNormalizer( node.getAttribute() );
        Comparator comparator = getComparator( node.getAttribute() );

        // get the attribute associated with the node
        AttributeType type = attributeTypeRegistry.lookup( node.getAttribute() );
        Attribute attr = AttributeUtils.getAttribute( entry, type );

        // If we do not have the attribute just return false
        if ( null == attr )
        {
            return false;
        }

        // check if AVA value exists in attribute
        if ( AttributeUtils.containsValue( attr, node.getValue(), type ) )
        {
            return true;
        }

        // get the normalized AVA filter value
        Object filterValue = normalizer.normalize( node.getValue() );

        // check if the normalized value is present
        if ( AttributeUtils.containsValue( attr, filterValue, type ) )
        {
            return true;
        }

        /*
         * We need to now iterate through all values because we could not get
         * a lookup to work.  For each value we normalize and use the comparator
         * to determine if a match exists.
         */
        NamingEnumeration list = attr.getAll();
        
        while ( list.hasMore() )
        {
            Object value = normalizer.normalize( list.next() );

            if ( 0 == comparator.compare( value, filterValue ) )
            {
                return true;
            }
        }

        // no match so return false
        return false;
    }


    /**
     * Gets the comparator for equality matching.
     *
     * @param attrId the attribute identifier
     * @return the comparator for equality matching
     * @throws javax.naming.NamingException if there is a failure
     */
    private Comparator getComparator( String attrId ) throws NamingException
    {
        MatchingRule mrule = getMatchingRule( attrId, EQUALITY_MATCH );

        return mrule.getComparator();
    }


    /**
     * Gets the normalizer for equality matching.
     *
     * @param attrId the attribute identifier
     * @return the normalizer for equality matching
     * @throws javax.naming.NamingException if there is a failure
     */
    private Normalizer getNormalizer( String attrId ) throws NamingException
    {
        MatchingRule mrule = getMatchingRule( attrId, EQUALITY_MATCH );
        return mrule.getNormalizer();
    }


    /**
     * Gets the matching rule for an attributeType.
     *
     * @param attrId the attribute identifier
     * @return the matching rule
     * @throws javax.naming.NamingException if there is a failure
     */
    private MatchingRule getMatchingRule( String attrId, int matchType ) throws NamingException
    {
        MatchingRule mrule = null;
        String oid = oidRegistry.getOid( attrId );
        AttributeType type = attributeTypeRegistry.lookup( oid );

        switch ( matchType )
        {
            case ( EQUALITY_MATCH ):
                mrule = type.getEquality();
                break;
                
            case ( SUBSTRING_MATCH ):
                mrule = type.getSubstr();
                break;
                
            case ( ORDERING_MATCH ):
                mrule = type.getOrdering();
                break;
                
            default:
                throw new NamingException( "Unknown match type: " + matchType );
        }

        if ( mrule == null )
        {
            return type.getEquality();
        }
        
        return mrule;
    }
}
