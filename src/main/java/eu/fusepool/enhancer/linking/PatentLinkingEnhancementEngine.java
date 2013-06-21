/**
 * 
 */
package eu.fusepool.enhancer.linking;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Map;

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
	@Property(name=PatentLinkingEnhancementEngine.SPARQL_ENDPOINT_01_NAME, value=PatentLinkingEnhancementEngine.DEFAULT_SPARQL_ENDPOINT_01, description="SPARQL endpoint"),
	@Property(name=PatentLinkingEnhancementEngine.SPARQL_GRAPH_01_NAME, value="", description="Sparql graph")
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

	public static final String DEFAULT_SPARQL_ENDPOINT_01 = "http://localhost/sparql" ; 
			//"http://localhost:8080/sparql" ;
	public static final String SPARQL_ENDPOINT_01_NAME = "SPARQL_ENDPOINT_01";
	public static final String SPARQL_GRAPH_01_NAME    = "SPARQL_GRAPH_01" ; 
	
	
	
	protected ComponentContext componentContext ;
	protected BundleContext    bundleContext ;
	
	String sparqlEndpoint = //"http://localhost:8080/sparql" ; 
			"http://platform.fusepool.info/sparql" ;
	
	String sparqlGraph = null ;
	
	
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
		
		//if(stick2rdfxml==false) 
		//	return ENHANCE_SYNCHRONOUS ;
		
		if(SupportedFormat.RDF_XML.equals(ci.getMimeType())) 
			return ENHANCE_ASYNC;
			
		if(ci.getMetadata().isEmpty())
			return CANNOT_ENHANCE ;
		
		return ENHANCE_ASYNC;
		
	}

	/* (non-Javadoc)
	 * @see org.apache.stanbol.enhancer.servicesapi.EnhancementEngine#computeEnhancements(org.apache.stanbol.enhancer.servicesapi.ContentItem)
	 */
	public void computeEnhancements(ContentItem ci) throws EngineException {
		
		SilkJob job = new SilkJob(bundleContext, sparqlEndpoint, sparqlGraph) ;
		
		try {
			if(isDebug) {
				InputStream cIs = ci.getStream() ;
				StringWriter writer = new StringWriter();
				IOUtils.copy(cIs, writer) ;
				logger.info("ContentItem:\n"+writer.toString()) ;
			}
			job.exceuteJob(ci) ;
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
		Object o = dict.get(PatentLinkingEnhancementEngine.SPARQL_ENDPOINT_01_NAME) ;
		if(o!=null && !"".equals(o.toString()))  {
			sparqlEndpoint = (String) o ;
		}
		
		o = dict.get(PatentLinkingEnhancementEngine.SPARQL_GRAPH_01_NAME) ;
			if(o!=null && !"".equals(o.toString()))  {
				sparqlGraph = (String) o ;
			} else {
				sparqlGraph = null ;
			}
		
		
		
		/*
		 o = dict.get("STICK2RDF_XML") ;
			if(o!=null)  {
				stick2rdfxml = (Boolean) o ;
			}
			
			*/
		
//	    File f = bundleContext.getDataFile("vaffanculo.txt");
//	    FileOutputStream os = new FileOutputStream(f);
//	    os.write("This is just an example\n".getBytes());
//	    os.flush();
//	    os.close();
		
	}	
	
	
	
	
	
}
