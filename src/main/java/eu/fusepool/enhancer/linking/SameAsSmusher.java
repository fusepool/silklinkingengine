package eu.fusepool.enhancer.linking;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


/*
 * Taken from package org.apache.clerezza.rdf.utils.Smusher 
 */
 

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.clerezza.rdf.core.BNode;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.NonLiteral;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.impl.TripleImpl;
import org.apache.clerezza.rdf.ontologies.OWL;
import org.apache.clerezza.rdf.ontologies.RDF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A utility to equate duplicate nodes in an Mgarph, currently only nodes with 
 * a shared ifp are equated.
 *
 * @author reto
 */
public class SameAsSmusher {

	private final static Logger log = LoggerFactory.getLogger(SameAsSmusher.class);
	
    /**
     * Smush graph given a set of triples all containing  <http://www.w3.org/2002/07/owl#sameAs> predicate.
     *
     * @param mGraph
     * @param tBox
     */
    public static void smush(MGraph mGraph, TripleCollection owlSameStatements) {
    	
    	log.info("Starting smushing");
        
    	// This hashmap contains a uri (key) and the set of equivalent uris (value)
    	final Map<NonLiteral, Set<NonLiteral>> node2EquivalenceSet = new HashMap<NonLiteral, Set<NonLiteral>>();
    	
    	log.info("Creating the sets of equivalent uris of each subject or object in the owl:sameAs statements");
    	// Determines for each subject and object in all the owl:sameAs statements the set of equivalent uris 
    	for (Iterator<Triple> it = owlSameStatements.iterator(); it.hasNext();) {            
    		final Triple triple = it.next();
            final UriRef predicate = triple.getPredicate();
            if (!predicate.equals(OWL.sameAs)) {
                throw new RuntimeException("Statements must use only <http://www.w3.org/2002/07/owl#sameAs> predicate.");
            }
            final NonLiteral subject = triple.getSubject();
            final NonLiteral object = (NonLiteral)triple.getObject();
            
            Set<NonLiteral> equivalentNodes = node2EquivalenceSet.get(subject);
            
            // if there is not a set of equivalent uris then create a new set
            if (equivalentNodes == null) {
            	equivalentNodes = node2EquivalenceSet.get(object);
            	if (equivalentNodes == null) {
                    equivalentNodes = new HashSet<NonLiteral>();
                }
            }
            
            // add both subject and object of the owl:sameAs statement to the set of equivalent uris
            equivalentNodes.add(subject);
            equivalentNodes.add(object);
            
            // use both uris in the owl:sameAs statement as keys for the set of equivalent uris
            node2EquivalenceSet.put(subject, equivalentNodes);
            node2EquivalenceSet.put(object, equivalentNodes);
            
            log.info("Sets of equivalent uris created.");
        
    	}
    	
    	// This set contains the sets of equivalent uris
    	Set<Set<NonLiteral>> unitedEquivalenceSets = new HashSet<Set<NonLiteral>>(node2EquivalenceSet.values());
    	// This hashmap contains all the uris (key) with their target uri (value)
    	Map<NonLiteral, NonLiteral> current2ReplacementMap = new HashMap<NonLiteral, NonLiteral>();
    	
    	// This graph contains the owl:sameAs statement with the equivalent uris as subject and their target uri as object
    	final MGraph newOwlSameStatements = new SimpleMGraph();
        
    	// for each set of equivalent uri select a target uri and fill a map with all the equivalent uris as keys and their target uri as value
    	for (Set<NonLiteral> equivalenceSet : unitedEquivalenceSets) {
            final NonLiteral replacement = getReplacementFor(equivalenceSet, newOwlSameStatements);
            for (NonLiteral current : equivalenceSet) {
                if (!current.equals(replacement)) {
                    current2ReplacementMap.put(current, replacement);
                }
            }
        }
    
    	// This set contains the new triples with the target uris in place of the their equivalent uris
        final Set<Triple> newTriples = new HashSet<Triple>();
        
        // replace subject and object in all triples in the graph if there is a target uri for those uris
        for (Iterator<Triple> it = mGraph.iterator(); it.hasNext();) {
            final Triple triple = it.next();            
            final NonLiteral subject = triple.getSubject();
            final Resource object = triple.getObject();
            NonLiteral subjectReplacement = current2ReplacementMap.get(subject);
            @SuppressWarnings("element-type-mismatch")
            Resource objectReplacement = current2ReplacementMap.get(object);
            if ((subjectReplacement != null) || (objectReplacement != null)) {
                it.remove(); //removes this triple from the graph
                if (subjectReplacement == null) {
                    subjectReplacement = subject;
                }
                if (objectReplacement == null) {
                    objectReplacement = object;
                }
                newTriples.add(new TripleImpl(subjectReplacement,
                        triple.getPredicate(), objectReplacement));
            }
        }
        
        // add the updated triples to the graph
        for (Triple triple : newTriples) {
        	mGraph.add(triple);
        }
        
        // add the new owl:sameAs statements to the graph. this should be avoided if the uri comes from the same dataset i.e. do not come from
        // an external dataset (like dbpedia.org)
        mGraph.addAll(newOwlSameStatements);
        
        log.info("Smush completed.");
    }

    /**
     * Takes the first uri in a set of equivalent uris to be the target (preferred) uri then creates new owl:sameAs statements
     * between the target uri and all the equivalent uris in the set.
     * @param equivalenceSet
     * @param owlSameAsGraph
     * @return
     */
    private static NonLiteral getReplacementFor(Set<NonLiteral> equivalenceSet, MGraph owlSameAsGraph) {
        
    	final Set<UriRef> uriRefs = new HashSet<UriRef>();
        
        for (NonLiteral nonLiteral : equivalenceSet) {
            if (nonLiteral instanceof UriRef) {
                uriRefs.add( (UriRef) nonLiteral );
            }
        }
        
        switch (uriRefs.size()) {
            case 1:
                return uriRefs.iterator().next();
            case 0:
                return new BNode();
        }
        
        final Iterator<UriRef> uriRefIter = uriRefs.iterator();
        
        //instead of an arbitrary one we might either decide lexicographically
        //or look at their frequency in mGraph
        final UriRef first = uriRefIter.next();
        
        while (uriRefIter.hasNext()) {
            UriRef uriRef = uriRefIter.next();
            owlSameAsGraph.add(new TripleImpl(uriRef, OWL.sameAs, first));
        }
        
        return first;
    }

   
    /**
    static class PredicateObject {

        final UriRef predicate;
        final Resource object;

        public PredicateObject(UriRef predicate, Resource object) {
            this.predicate = predicate;
            this.object = object;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final PredicateObject other = (PredicateObject) obj;
            if (this.predicate != other.predicate && !this.predicate.equals(other.predicate)) {
                return false;
            }
            if (this.object != other.object && !this.object.equals(other.object)) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 29 * hash + this.predicate.hashCode();
            hash = 13 * hash + this.object.hashCode();
            return hash;
        }

        @Override
        public String toString() {
            return "("+predicate+", "+object+")";
        }


    };
    */
}

