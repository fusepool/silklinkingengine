/**
 * 
 */
package eu.fusepool.enancher.linking.silkjob;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.serializedform.Parser;
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

import eu.fusepool.java.silk.client.SilkClient;

/**
 * @author giorgio
 *
 */
public class SilkJob {

	private static final String CI_METADATA_TAG 		= "[CI_METADATA_FILE]" ;
	private static final String OUTPUT_TMP_TAG  		= "[OUTPUT_TMP_FILE_PATH]";
	private static final String SPARQL_ENDPOINT_01_TAG  = "[SPARQL_ENDPOINT_01]" ;
	
	// local sparql endpoint
	String sparqlEndpoint ;
	
	private static final boolean holdDataFiles = true ;
	private static final boolean debug = true ;
	//private boolean stick2RDF_XML = false ;
	
	protected BundleContext    bundleContext ;
	
	
	private String jobId ;
	SilkClient 	   silk ;
	Parser		   parser ;
	org.apache.clerezza.rdf.core.serializedform.SerializingProvider provider = null ;
	File rdfData ;
	File outputData ;
	
	String config ;
	
	final Logger logger = LoggerFactory.getLogger(this.getClass()) ;
	
	
	
	public SilkJob(BundleContext ctx, String sparqlEndpoint) {
		bundleContext = ctx ;
		this.sparqlEndpoint = sparqlEndpoint ;
		//stick2RDF_XML = stick2rdfxml ;
	}
	
	@SuppressWarnings("unused")
	public void exceuteJob(ContentItem ci) throws Exception {
		
		jobId = UUID.randomUUID().toString() ;
		try {
			InputStream content2Enhance = null ; 
			TripleCollection inputGraph = null;
			
			
			ci.getLock().writeLock().lock();
			getSilkClient() ;
			getParser() ;
			getSerializer() ;
			createTempFiles() ;
			
			OutputStream rdfOS = new FileOutputStream(rdfData) ;
			
			String mimeType=ci.getMimeType() ;	
			if(!SupportedFormat.RDF_XML.equals(mimeType)) {
				MGraph ciMetadata = ci.getMetadata() ;
				if(ciMetadata.isEmpty()) {
					// nothing to enhance :-(
					return ;
				} else {
					inputGraph = ciMetadata ;
				}
			} else {
				content2Enhance = ci.getStream() ;
				inputGraph = parser.parse(content2Enhance, SupportedFormat.RDF_XML ) ;
			}
			provider.serialize(rdfOS, inputGraph, SupportedFormat.RDF_XML) ;
			rdfOS.close() ;
			buildConfig() ;

			silk.executeStream(IOUtils.toInputStream(config, "UTF-8"), null, 1, true) ;
			
			//  output->MGraph and MGraph->Metadata
			MGraph rdfGraph = new IndexedMGraph();
			InputStream is = new FileInputStream(outputData) ;

			Set<String> formats  = parser.getSupportedFormats() ;
			parser.parse(rdfGraph, is, SupportedFormat.N_TRIPLE) ;
			is.close() ;
			logger.debug(rdfGraph.size()+"triples extracted for the job: "+jobId) ;
			if(!rdfGraph.isEmpty())
				ci.getMetadata().addAll(rdfGraph) ;
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
		InputStream cfgIs = this.getClass().getResourceAsStream("/silk-config2.xml") ;
		String roughConfig = IOUtils.toString(cfgIs, "UTF-8");
		roughConfig = StringUtils.replace(roughConfig,SPARQL_ENDPOINT_01_TAG, sparqlEndpoint ) ;
		roughConfig = StringUtils.replace(roughConfig, CI_METADATA_TAG, rdfData.getAbsolutePath()) ;
		config = StringUtils.replace(roughConfig, OUTPUT_TMP_TAG, outputData.getAbsolutePath()) ;
		logger.info("configuration built for the job:" + jobId+"\n"+config) ;
	}
	
	
	private void getSerializer() throws Exception {
		ServiceReference ref = bundleContext.getServiceReference("org.apache.clerezza.rdf.core.serializedform.SerializingProvider") ;
		if(ref!=null) {
			provider = (SerializingProvider) bundleContext.getService(ref) ;
		} else {
			logger.error("Cannot get SerializingProvider Service for the job: "+jobId) ;
			provider = null ;
			throw new Exception("Cannot get SerializingProvider Service!") ;
		}
		
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
}
