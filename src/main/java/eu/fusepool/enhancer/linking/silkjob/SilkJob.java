/**
 * 
 */
package eu.fusepool.enhancer.linking.silkjob;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.serializedform.Parser;
import org.apache.clerezza.rdf.core.serializedform.Serializer;
import org.apache.clerezza.rdf.core.serializedform.SerializingProvider;
import org.apache.clerezza.rdf.core.serializedform.SupportedFormat;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.stanbol.commons.indexedgraph.IndexedMGraph;
import org.apache.stanbol.enhancer.servicesapi.ContentItem;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fusepool.enhancer.linking.SameAsSmusher;
import eu.fusepool.java.silk.client.SilkClient;

/**
 * @author giorgio
 *
 */
public class SilkJob {

	private static final String CI_METADATA_TAG 		= "[CI_METADATA_FILE]" ;
	private static final String OUTPUT_TMP_TAG  		= "[OUTPUT_TMP_FILE_PATH]";
	private static final String SPARQL_ENDPOINT_01_TAG  = "[SPARQL_ENDPOINT_01]" ;
	private static final String SPARQL_GRAPH_01_TAG 	= "[GRAPH_PARAMETER]" ;
			
	
	// sparql endpoint
	String sparqlEndpoint ;
	String sparqlGraph ;
	
	
	private static final boolean holdDataFiles = true ;
	private static final boolean isDebug = true ;
	//private boolean stick2RDF_XML = false ;
	
	protected BundleContext    bundleContext ;
	
	
	private String jobId ;
	SilkClient 	   silk ;
	Parser		   parser ;
	SerializingProvider provider = null ;
	Serializer serializer ;
	File rdfData ; // source RDF data
	File outputData ;
	
	String config ;
	
	final Logger logger = LoggerFactory.getLogger(this.getClass()) ;
	
	
	
	public SilkJob(BundleContext ctx, String sparqlEndpoint, String graphName) {
		bundleContext = ctx ;
		this.sparqlEndpoint = sparqlEndpoint ;
		this.sparqlGraph = graphName ;
		//stick2RDF_XML = stick2rdfxml ;
	}
	
	@SuppressWarnings("unused")
	public MGraph exceuteJob(ContentItem ci) throws Exception {
		
		jobId = UUID.randomUUID().toString() ;
		try {
			InputStream content2Enhance = null ; //RDF data to be passed to Silk 
			TripleCollection inputGraph = null;
			
			
			ci.getLock().writeLock().lock();
			getSilkClient() ;
			getParser() ;
			getSerializer() ;
			createTempFiles() ;
			
			OutputStream rdfOS = new FileOutputStream(rdfData) ;
			
			String mimeType=ci.getMimeType() ;	
			//If the Content type is RDF/XML it takes the contentitem data otherwise it takes 
			//the metadata if not empty
			if( !SupportedFormat.RDF_XML.equals(mimeType) ) {
				MGraph ciMetadata = ci.getMetadata() ;
				if(ciMetadata.isEmpty()) {
					// nothing to enhance :-(
					return null;
				} else {
					inputGraph = ciMetadata ;
					provider.serialize(rdfOS, inputGraph, SupportedFormat.RDF_XML) ;
				}
			} else {
				content2Enhance = ci.getStream() ;
				IOUtils.copy(content2Enhance, rdfOS) ;
				//inputGraph = parser.parse(content2Enhance, SupportedFormat.RDF_XML ) ;
			}
			/*
			if(isDebug) {
				ByteArrayOutputStream buffer = new ByteArrayOutputStream() ;
				provider.serialize(buffer, inputGraph, SupportedFormat.RDF_XML) ;
				String msg = new String(buffer.toByteArray()) ;
				logger.info("triple collection (RDF/XML):\n"+msg) ;
			}
			*/
			rdfOS.close() ;
			buildConfig() ;

			silk.executeStream(IOUtils.toInputStream(config, "UTF-8"), null, 1, true) ;
			
			//  This graph will contain the results of the duplicate detection i.e. owl:sameAs statements
			MGraph owlSameAsStatements = new IndexedMGraph();
			InputStream is = new FileInputStream(outputData) ;

			Set<String> formats  = parser.getSupportedFormats() ;
			parser.parse(owlSameAsStatements, is, SupportedFormat.N_TRIPLE) ;
			is.close() ;
			logger.info(owlSameAsStatements.size() + " triples extracted by job: " + jobId) ;
			
			if(!owlSameAsStatements.isEmpty()){
				
				//SameAsSmusher.smush(owlSameAsStatements, owlSameAsStatements);
				
				//ci.getMetadata().addAll(owlSameAsStatements) ;	
				
			}
			
			return owlSameAsStatements;
				
		} catch (Exception e) {
			System.out.println(e.getMessage());
			throw e ;
		} finally {
			ci.getLock().writeLock().unlock();
			cleanUpFiles() ;
		}
	}
	
	
	private void createTempFiles() {
		String ifName = jobId+"-in.rdf" ;
		String ofName = jobId+"-out.rdf" ;
		rdfData = bundleContext.getDataFile(ifName) ;
		outputData = bundleContext.getDataFile(ofName) ;
	}
	
	
	private void getSilkClient() throws Exception {
		String className = SilkClient.class.getName() ;
		ServiceReference ref = bundleContext.getServiceReference(className) ;
		if(ref!=null) {
			silk = (SilkClient)bundleContext.getService(ref) ;
		} else {
			silk = null ;
			logger.error("Cannot get SilkClient Service for the job: "+jobId) ;
			throw new Exception("Cannot get SilkClient Service!") ;
		}
	}
	
	private void getParser() throws Exception {
		ServiceReference ref = bundleContext.getServiceReference(Parser.class.getName()) ;
		if(ref!=null) {
			parser = (Parser)bundleContext.getService(ref) ;
		}else {
			parser = null ;
			logger.error("Unable to find parser for the job: "+jobId) ;
			throw new Exception("Cannot get parser!") ;
		}
	}
	
	/**
	 * builds the configuration for the silk job
	 * 
	 * @throws IOException
	 */
	private void buildConfig() throws IOException {
		InputStream cfgIs = this.getClass().getResourceAsStream("/silk-config-applicants-test.xml") ;
		String roughConfig = IOUtils.toString(cfgIs, "UTF-8");
		roughConfig = StringUtils.replace(roughConfig,SPARQL_ENDPOINT_01_TAG, sparqlEndpoint ) ;
		if(sparqlGraph!=null && !"".equals(sparqlGraph)) {
			String graphParamFragment = "<Param name=\"graph\" value=\"" + sparqlGraph + "\"" + "></Param>" ;
			roughConfig = StringUtils.replace(roughConfig, SPARQL_GRAPH_01_TAG, graphParamFragment) ;
		} else { 
			roughConfig = StringUtils.replace(roughConfig, SPARQL_GRAPH_01_TAG, "") ;
		}
		roughConfig = StringUtils.replace(roughConfig, CI_METADATA_TAG, rdfData.getAbsolutePath()) ;
		config = StringUtils.replace(roughConfig, OUTPUT_TMP_TAG, outputData.getAbsolutePath()) ;
		logger.info("configuration built for the job:" + jobId+"\n"+config) ;
	}
	
	
	private void getSerializer() throws Exception {
		serializer = Serializer.getInstance() ;
		/*
		ServiceReference ref = bundleContext.getServiceReference(SerializingProvider.class.getName()) ;
		if(ref!=null) {
			provider = (SerializingProvider) bundleContext.getService(ref) ;
		} else {
			logger.error("Cannot get SerializingProvider Service for the job: "+jobId) ;
			provider = null ;
			throw new Exception("Cannot get SerializingProvider Service!") ;
		}
		*/
		
	}
	
	
	/**
	 * Removes temporary files 
	 */
	private void cleanUpFiles() {
		if(holdDataFiles)
			return ;
		if(rdfData!=null)
			rdfData.delete() ;
		if(outputData!=null)
			outputData.delete() ;
	}

	/**
	 * @return the sparqlEndpoint
	 */
	public String getSparqlEndpoint() {
		return sparqlEndpoint;
	}

	/**
	 * @param sparqlEndpoint the sparqlEndpoint to set
	 */
	public void setSparqlEndpoint(String sparqlEndpoint) {
		this.sparqlEndpoint = sparqlEndpoint;
	}

	/**
	 * @return the sparqlGraph
	 */
	public String getSparqlGraph() {
		return sparqlGraph;
	}

	/**
	 * @param sparqlGraph the sparqlGraph to set
	 */
	public void setSparqlGraph(String sparqlGraph) {
		this.sparqlGraph = sparqlGraph;
	}
}
