Silk Linking Engine
=================

An enhancement engine for interlinking that uses the SILK wrapper bundle (silklinking). It compares RDF data with a 
master repository via its SPARQL endpoint. The master (target) repository is set in the SILK configuration file.

Send the rdf data to a chain

    curl -u user:password -i -X POST -H "Accept: text/turtle" -H "Content-Type: application/rdf+xml" -T <rdf_file> "http://platform.fusepool.info/enhancer/chain/silk_chain"
    

Send the data directly to the engine

    curl -u admin:admin -X POST -H "Accept: text/turtle" -H "Content-type: application/rdf+xml" -T <rdf_file> http://platform.fusepool.info/enhancer/engine/PatentLinkingEnhancer
