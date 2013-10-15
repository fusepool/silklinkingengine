package eu.fusepool.enhancer.linking;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Dictionary;
import java.util.Iterator;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.Triple;
//import org.apache.clerezza.platform.graphprovider.content.ContentGraphProvider;
import org.apache.clerezza.rdf.core.serializedform.Parser;
import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.enhancer.servicesapi.ChainManager;
import org.apache.stanbol.enhancer.servicesapi.ContentItem;
import org.apache.stanbol.enhancer.servicesapi.ContentItemFactory;
import org.apache.stanbol.enhancer.servicesapi.ContentSource;
import org.apache.stanbol.enhancer.servicesapi.EngineException;
import org.apache.stanbol.enhancer.servicesapi.EnhancementEngine;
import org.apache.stanbol.enhancer.servicesapi.EnhancementJobManager;
import org.apache.stanbol.enhancer.servicesapi.impl.ByteArraySource;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fusepool.enhancer.linking.silkjob.SilkJob;


@Component(immediate = true, metatype = true,
configurationFactory = true, //allow multiple instances
 policy = ConfigurationPolicy.OPTIONAL) //create a default instance with the default configuration
@Service(Object.class)
@Property(name = "javax.ws.rs", boolValue = true)
@Path("duplicate")

public class DuplicateDetector {
	
	/**
     * Using slf4j for normal logging
     */
    private static final Logger logger = LoggerFactory
            .getLogger(DuplicateDetector.class);
    
    /**
     * This service allows accessing and creating persistent triple collections
     */
    //@Reference
    //private ContentGraphProvider contentGraphProvider;
    @Reference
    private Parser parser;
    @Reference
    private ContentItemFactory contentItemFactory;
    @Reference
    private EnhancementJobManager enhancementJobManager;
    @Reference
    private ChainManager chainManager;
    
    public static final String DEFAULT_SPARQL_ENDPOINT = "http://localhost:8080/sparql" ; 
	public static final String DEFAULT_GRAPH = "urn:x-localinstance:/content.graph" ;

	// Labels for the component configuration panel
	public static final String SPARQL_ENDPOINT_LABEL = "Endpoint";
	public static final String GRAPH_LABEL    = "Graph" ; 
	
	protected ComponentContext componentContext ;
	protected BundleContext    bundleContext ;
	
	String sparqlEndpoint = this.DEFAULT_SPARQL_ENDPOINT; 
	
	String sparqlGraph = DEFAULT_GRAPH;
	
	boolean isDebug = false;
	
    @Activate
	protected void activate(ComponentContext ce) throws IOException, ConfigurationException {
    	
    	logger.info("The DuplicateDetector service is being activated");
		//super.activate(ce);
		this.componentContext = ce ;
		this.bundleContext = ce.getBundleContext() ;
		
		@SuppressWarnings("rawtypes")
		Dictionary dict = ce.getProperties() ;
		Object o = dict.get(PatentLinkingEnhancementEngine.SPARQL_ENDPOINT_LABEL) ;
		if(o!=null && !"".equals(o.toString()))  {
			sparqlEndpoint = (String) o ;
		}
		
		o = dict.get(PatentLinkingEnhancementEngine.GRAPH_LABEL) ;
			if(o!=null && !"".equals(o.toString()))  {
				sparqlGraph = (String) o ;
			} else {
				sparqlGraph = null ;
			}
		
		
	}	

    @Deactivate
    protected void deactivate(ComponentContext context) {
        logger.info("The DuplicateDetector service is being deactivated");
    }
    
    /**
     * Load RDF data sent by HTTP POST
     */
    @POST
    @Path("detect")
    @Produces("text/plain")
	public void detect(@Context final UriInfo uriInfo,
            @HeaderParam("Content-Type") final String mediaType,
            final InputStream data) throws Exception {
    	SilkJob job = null;
		
		job = new SilkJob(bundleContext, sparqlEndpoint, sparqlGraph) ;
		
		MGraph owlSameAs = null;
		if(job != null) {
			try {
				
				if(isDebug){
					StringWriter writer = new StringWriter();
					IOUtils.copy(data, writer) ;
					logger.info("ContentItem:\n"+writer.toString()) ;
				}
				
				ContentSource contentSource = new ByteArraySource(IOUtils.toByteArray(data), "text/plain");
				
				ContentItem ci = contentItemFactory.createContentItem(contentSource);
				
				owlSameAs = job.executeJob(ci);
				
				if( (owlSameAs != null) && (!owlSameAs.isEmpty()) ) {
					
					// just for debug
					Iterator<Triple> isameas = owlSameAs.iterator();
					String stmtResult = "";
					while(isameas.hasNext()) {
						Triple sameasStmt = isameas.next();
						stmtResult += sameasStmt.toString() + "\n";
						
					}
					logger.info(stmtResult);
				}
				
			} catch (Exception e) {
				logger.error("Error : ", e) ;
				throw new EngineException(e) ;
			}
		}
		else {
			logger.info("silk job did not start");
		}
	}


}
