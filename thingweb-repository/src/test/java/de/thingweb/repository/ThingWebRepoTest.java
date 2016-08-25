package de.thingweb.repository;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map.Entry;

import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.text.EntityDefinition;
import org.apache.jena.query.text.TextDatasetFactory;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.vocabulary.RDFS;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import de.thingweb.repository.coap.CoAPServer;
import de.thingweb.repository.handlers.TDLookUpEPHandler;
import de.thingweb.repository.handlers.TDLookUpHandler;
import de.thingweb.repository.handlers.TDLookUpSEMHandler;
import de.thingweb.repository.http.HTTPServer;
import de.thingweb.repository.rest.RESTException;
import de.thingweb.repository.rest.RESTHandler;
import de.thingweb.repository.rest.RESTResource;
import de.thingweb.repository.rest.RESTServerInstance;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;

public class ThingWebRepoTest {

	private static ThingDescriptionCollectionHandler tdch;
	
	private final static int portCoap = 5683;
	private final static int portHttp = 8080;
	private final static String dbPath  = "db";
	private final static String idxPath = "Lucene";
	private final static String baseUri = "http://www.example.com";

	@BeforeClass
	public static void oneTimeSetUp() {
		
		// Setup repository
		Repository.get().init(dbPath, baseUri, idxPath);
		
		List<RESTServerInstance> servers = new ArrayList<>();
		RESTHandler root = new WelcomePageHandler(servers);
		servers.add(new CoAPServer(portCoap, root));
        servers.add(new HTTPServer(portHttp, root));

        for (RESTServerInstance i : servers) {
            i.add("/td-lookup", new TDLookUpHandler(servers));
            i.add("/td-lookup/ep", new TDLookUpEPHandler(servers));
            i.add("/td-lookup/sem", new TDLookUpSEMHandler(servers));
            i.add("/td", new ThingDescriptionCollectionHandler(servers));
            i.start();
        }
        
        Repository.get();
		Repository.servers = servers;
		
		tdch = new ThingDescriptionCollectionHandler(servers);

	}

	@AfterClass
	public static void oneTimeTearDown() {
		
		// Close dataset
		Dataset ds = Repository.get().dataset;
		ds.close();
	}

	@Test
	public void testREST() throws IOException, URISyntaxException {
		
		RESTResource resource;
		byte[] content;
		String tdId, tdId2, td;
		
		Map<String,String> parameters = new HashMap<String,String>();
		parameters.put("ep", baseUri);
		
		// POST TD fan
		String tdUri = "coap:///www.example.com:5686/Fan";
		content = getThingDescription("./samples/fanTD.jsonld");
		resource = tdch.post(new URI(baseUri + "/td"), parameters, new ByteArrayInputStream(content));
		tdId = resource.path;
		
		td = ThingDescriptionUtils.getThingDescriptionIdFromUri(tdUri);
		Assert.assertEquals("TD fan not registered", baseUri + tdId, td);
		
		
		// POST TD temperatureSensor
		String tdUri2 = "coap:///www.example.com:5687/temp";
		content = getThingDescription("./samples/temperatureSensorTD.jsonld");
		resource = tdch.post(new URI(baseUri + "/td"), parameters, new ByteArrayInputStream(content));
		tdId2 = resource.path;
			
		td = ThingDescriptionUtils.getThingDescriptionIdFromUri(tdUri2);
		Assert.assertEquals("TD temperatureSensor not registered", baseUri + tdId2, td);
		
		
		// LOOKUP
		Set<String> tdIds;
		JsonObject fanQR;
		
		// GET by sparql query
		parameters.clear();
		parameters.put("query", "?s ?p ?o");
		resource = tdch.get(new URI(baseUri + "/td"), parameters);
		
		fanQR = JSON.parse(resource.content);
		tdIds = fanQR.keys();
		Assert.assertFalse("TD fan not found", tdIds.isEmpty());
		//Assert.assertEquals("Found more than one TD", 1, tdIds.size());
		Assert.assertTrue("TD fan not found", tdIds.contains(tdId));
		
		
		// GET by text query
		parameters.clear();
		parameters.put("text", "\"name AND fan\"");
		resource = tdch.get(new URI(baseUri + "/td"), parameters);
		
		fanQR = JSON.parse(resource.content);
		tdIds = fanQR.keys();
		Assert.assertFalse("TD fan not found", tdIds.isEmpty());
		Assert.assertTrue("TD fan not found", tdIds.contains(tdId));
		Assert.assertFalse("TD temperatureSensor found", tdIds.contains(tdId2));
		
		
		
		// GET TD by id
		ThingDescriptionHandler tdh = new ThingDescriptionHandler(tdId, Repository.get().servers);
		resource = tdh.get(new URI(baseUri + tdId), null);
		JsonObject o = JSON.parse(resource.content);
		JsonValue v = o.get("uris").getAsArray().get(0);
		Assert.assertEquals("TD fan not found", "\"" + tdUri + "\"", v.toString());
		
		
		// PUT TD change fan's name
		content = getThingDescription("./samples/fanTD_update.jsonld");
		tdh.put(new URI(baseUri + tdId), new HashMap<String,String>(), new ByteArrayInputStream(content));
			
		// GET TD by id and check change
		RESTResource resource2 = tdh.get(new URI(baseUri + tdId), null);
		JsonObject o2 = JSON.parse(resource2.content);
		JsonValue v2 = o2.get("name");
		Assert.assertEquals("TD fan not updated", "\"Fan2\"", v2.toString());

		
		// DELETE TDs
		tdh.delete(new URI(baseUri + tdId), null, null);
		td = ThingDescriptionUtils.getThingDescriptionIdFromUri(tdUri);
		Assert.assertEquals("TD fan not deleted", "NOT FOUND", td);
		
		tdh.delete(new URI(baseUri + tdId2), null, null);
		td = ThingDescriptionUtils.getThingDescriptionIdFromUri(tdUri2);
		Assert.assertEquals("TD temperatureSensor not deleted", "NOT FOUND", td);
		
	}
	
	
	// ***** EXTRAS *****
	
	
	/**
	 * Returns the content of a TD json-ld file.
	 * Mocks the behavior of doing a GET to the TD's uri.
	 * 
	 * @param fileName Name of the json-ld file.
	 * @return Content of the file in a String.
	 * @throws IOException
	 */
	public byte[] getThingDescription(String fileName) throws IOException {
		
		return Files.readAllBytes(Paths.get(fileName));
	}

}
