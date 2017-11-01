package de.thingweb.directory.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.thingweb.directory.ThingDirectory;

public class CollectionResource extends RESTResource {

	protected final RESTResourceFactory factory;
	protected final CollectionFilterFactory filterFactory;
	protected final Set<RESTResource> children = new HashSet<>();
	
	protected static class KeepAllFilter implements CollectionFilter {
		
		public KeepAllFilter() {
			// public constructor
		}
		
		@Override
		public boolean keep(RESTResource child) {
			return true;
		}
		
	}

	public CollectionResource(String path, RESTResourceFactory f) {
		this(path, f, new CollectionFilterFactory() {			
			@Override
			public CollectionFilter create(Map<String, String> parameters) {
				return new KeepAllFilter();
			}
		});
	}

	public CollectionResource(String path, RESTResourceFactory f, CollectionFilterFactory ff) {
		super(path);
		factory = f;
		filterFactory = ff;
		contentType = "application/json";
	}

	@Override
	public void get(Map<String, String> parameters, OutputStream out) throws RESTException {
		CollectionFilter f = filterFactory.create(parameters);
		
		Set<RESTResource> filtered = new HashSet<>();
		for (RESTResource child : children) {
			if (f.keep(child)) {
				filtered.add(child);
			}
		}
		
		try {
			out.write('[');
			
			Iterator<RESTResource> it = filtered.iterator();
			while (it.hasNext()) {
				RESTResource res = it.next();
				out.write('"');
				out.write(res.getName().getBytes());
				out.write('"');
				if (it.hasNext()) {
					out.write(',');
				}
			}
			
			out.write(']');
		} catch (IOException e) {
			ThingDirectory.LOG.error("Cannot write byte array", e);
			throw new RESTException();
		}
	}

	@Override
	public RESTResource post(Map<String, String> parameters, InputStream payload) throws RESTException {
		RESTResource child = factory.create(path + "/" + generateChildID(), parameters, payload);
		
		children.add(child);
		for (RESTResourceListener l : listeners) {
			l.onCreate(child);
		}
		
		return child;
	}

	@Override
	public void addListener(RESTResourceListener listener) {
		super.addListener(listener);
		
		for (RESTResource child : children) {
			listener.onCreate(child);
		}
	}
	
	public Set<RESTResource> getChildren() {
		return children;
	}
	
	protected void repost(String id) {
		RESTResource child = factory.create(path + "/" + id);
		
		children.add(child);
		for (RESTResourceListener l : listeners) {
			l.onCreate(child);
		}
	}
	
	protected String generateChildID() {
		String id = UUID.randomUUID().toString();
		return id.substring(0, id.indexOf('-'));
	}

}