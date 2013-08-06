/**
 * 
 */
package eu.fusepool.enhancer.linking;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.Map;

import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.serializedform.SupportedFormat;
import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.enhancer.servicesapi.ContentItem;
import org.apache.stanbol.enhancer.servicesapi.EngineException;
import org.apache.stanbol.enhancer.servicesapi.EnhancementEngine;
import org.apache.stanbol.enhancer.servicesapi.ServiceProperties;
import org.apache.stanbol.enhancer.servicesapi.impl.AbstractEnhancementEngine;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.fusepool.enhancer.linking.silkjob.SilkJob;


/**
 * @author giorgio
 *
 */
@Component(immediate = true, metatype = true,
configurationFactory = true, //allow multiple instances
 policy = ConfigurationPolicy.OPTIONAL) //create a default instance with the default configuration
@Service
@Properties( value={
	@Property(name=EnhancementEngine.PROPERTY_NAME, value=PatentLinkingEnhancementEngine.DEFAULT_ENGINE_NAME),
	@Property(name=Constants.SERVICE_RANKING,intValue=PatentLinkingEnhancementEngine.DEFAULT_SERVICE_RANKING),
	@Property(name=PatentLinkingEnhancementEngine.SPARQL_ENDPOINT_LABEL, value="", description="SPARQL endpoint of the target (master) repository"),
	@Property(name=PatentLinkingEnhancementEngine.GRAPH_LABEL, value="", description="Graph")
	})

public class PatentLinkingEnhancementEngine
		extends AbstractEnhancementEngine<IOException,RuntimeException> 
		implements EnhancementEngine, ServiceProperties {

	
	public static final String DEFAULT_ENGINE_NAME = "PatentLinkingEnhancer" ;
	
	
	
	/**
	 * Default value for the {@link Constants#SERVICE_RANKING} used by this engine.
	 * This is a negative value to allow easy replacement by this engine depending
	 * to a remote service with one that does not have this requirement
	 */
	public static final int DEFAULT_SERVICE_RANKING = 101;	
	
	/**
	 * The default value for the Execution of this Engine. Currently set to
	 * {@link ServiceProperties#ORDERING_EXTRACTION_ENHANCEMENT}
	 */
	public static final Integer defaultOrder = ORDERING_EXTRACTION_ENHANCEMENT;

	public static final String DEFAULT_SPARQL_ENDPOINT = "http://localhost:8080/sparql" ; 
	public static final String DEFAULT_GRAPH = "urn:x-localinstance:/content.graph" ;

	// Labels for the component configuration panel
	public static final String SPARQL_ENDPOINT_LABEL = "Endpoint";
	public static final String GRAPH_LABEL    = "Graph" ; 
	
	
	
	protected ComponentContext componentContext ;
	protected BundleContext    bundleContext ;
	
	String sparqlEndpoint = this.DEFAULT_SPARQL_ENDPOINT; 
	
	String sparqlGraph = "";
	
	
	final Logger logger = LoggerFactory.getLogger(this.getClass()) ;
	
	private final static boolean isDebug = true ;
	
	
	/* (non-Javadoc)
	 * @see org.apache.stanbol.enhancer.servicesapi.ServiceProperties#getServiceProperties()
	 */
	public Map<String, Object> getServiceProperties() {
		return Collections.unmodifiableMap(Collections.singletonMap(
				ENHANCEMENT_ENGINE_ORDERING, (Object) defaultOrder));
	}

	/* (non-Javadoc)
	 * @see org.apache.stanbol.enhancer.servicesapi.EnhancementEngine#canEnhance(org.apache.stanbol.enhancer.servicesapi.ContentItem)
	 */
	public int canEnhance(ContentItem ci) throws EngineException {
		/*
		if(SupportedFormat.RDF_XML.equals(ci.getMimeType())) 
			return ENHANCE_ASYNC;
			
		*/
		
		// No RDF data in the content or its metadata field
		if( (! SupportedFormat.RDF_XML.equals( ci.getMimeType() ) ) && (ci.getMetadata().isEmpty()) )
			return CANNOT_ENHANCE ;
		
		return ENHANCE_SYNCHRONOUS;
		
	}

	/* (non-Javadoc)
	 * @see org.apache.stanbol.enhancer.servicesapi.EnhancementEngine#computeEnhancements(org.apache.stanbol.enhancer.servicesapi.ContentItem)
	 */
	public void computeEnhancements(ContentItem ci) throws EngineException {
		
		SilkJob job = new SilkJob(bundleContext, sparqlEndpoint, sparqlGraph) ;
		MGraph owlSameAs = null;
		try {
			if(isDebug) {
				InputStream cIs = ci.getStream() ;
				StringWriter writer = new StringWriter();
				IOUtils.copy(cIs, writer) ;
				logger.info("ContentItem:\n"+writer.toString()) ;
			}
			
			owlSameAs = job.exceuteJob(ci) ;
			
			if( (owlSameAs != null) && (!owlSameAs.isEmpty()) ) {
				ci.getMetadata().addAll(owlSameAs) ;
				
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

	@Activate
	protected void activate(ComponentContext ce) throws IOException, ConfigurationException {
		super.activate(ce);
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
	
	
	
	
	
}
