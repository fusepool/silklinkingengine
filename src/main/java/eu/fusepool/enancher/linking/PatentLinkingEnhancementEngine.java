/**
 * 
 */
package eu.fusepool.enancher.linking;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Map;

import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.serializedform.SupportedFormat;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
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

import eu.fusepool.enancher.linking.silkjob.SilkJob;


/**
 * @author giorgio
 *
 */
@Component(immediate=true, metatype=true)
@Service
@Properties( value={
	@Property(name=EnhancementEngine.PROPERTY_NAME, value=PatentLinkingEnhancementEngine.DEFAULT_ENGINE_NAME),
	@Property(name=Constants.SERVICE_RANKING,intValue=PatentLinkingEnhancementEngine.DEFAULT_SERVICE_RANKING),
	@Property(name="SPARQL_ENDPOINT_01", value="", description="SPARQL endpoint"),
	@Property(name="STICK2RDF_XML", boolValue=false, description="Checks content item mime type")
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

	
	protected ComponentContext componentContext ;
	protected BundleContext    bundleContext ;
	
	String sparqlEndpoint = "http://cabernet:3030/dataset/query" ;
	boolean stick2rdfxml = true ;
	
//	@Reference
//	protected Parser parser ;
	
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
		if(stick2rdfxml==false) 
			return ENHANCE_SYNCHRONOUS ;
		if(SupportedFormat.RDF_XML.equals(ci.getMimeType())) 
				return ENHANCE_SYNCHRONOUS ;
		MGraph rdfGraph = ci.getMetadata() ;
		if(rdfGraph.isEmpty())
			return CANNOT_ENHANCE ;
		return ENHANCE_SYNCHRONOUS;
	}

	/* (non-Javadoc)
	 * @see org.apache.stanbol.enhancer.servicesapi.EnhancementEngine#computeEnhancements(org.apache.stanbol.enhancer.servicesapi.ContentItem)
	 */
	public void computeEnhancements(ContentItem ci) throws EngineException {
		SilkJob job = new SilkJob(bundleContext, sparqlEndpoint, stick2rdfxml) ;
		try {
			job.exceuteJob(ci) ;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new EngineException(e) ;
		}
	}

	@Activate
	protected void activate(ComponentContext ce) throws IOException, ConfigurationException {
		super.activate(ce);
		this.componentContext = ce ;
		this.bundleContext = ce.getBundleContext() ;
		
		
		Dictionary dict = ce.getProperties() ;
		Object o = dict.get("SPARQL_ENDPOINT_01") ;
		if(o!=null && !"".equals(o.toString()))  {
			sparqlEndpoint = (String) o ;
		}
		
		 o = dict.get("STICK2RDF_XML") ;
			if(o!=null)  {
				stick2rdfxml = (Boolean) o ;
			}
		
//	    File f = bundleContext.getDataFile("vaffanculo.txt");
//	    FileOutputStream os = new FileOutputStream(f);
//	    os.write("This is just an example\n".getBytes());
//	    os.flush();
//	    os.close();
		
	}	
	
	
	
	
	
}
