package de.thingweb.directory.http;

import java.net.URL;
import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.resource.Resource;

import de.thingweb.directory.rest.CollectionItemServlet;
import de.thingweb.directory.rest.CollectionServlet;
import de.thingweb.directory.rest.RESTServlet;
import de.thingweb.directory.rest.RESTServletContainer;

public class HTTPServer extends RESTServletContainer {

	protected Server server;
	protected ServletContextHandler ctx;

	public HTTPServer(int port) {
		server = new Server(port);
		
		ctx = new ServletContextHandler();
		ctx.setContextPath("/");
		ctx.setWelcomeFiles(new String[] { "index.html" });
		
		ServletHolder h = new ServletHolder("default", DefaultServlet.class);
		h.setInitParameter("resourceBase", Resource.newClassPathResource("public").toString());
		h.setInitParameter("dirAllowed", "true");
		ctx.addServlet(h, "/");

		FilterHolder holder = new FilterHolder(new CrossOriginFilter());
		holder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*"); // TODO - restrict this
		holder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,PUT,DELETE,HEAD,OPTIONS");
		holder.setInitParameter(CrossOriginFilter.ALLOW_CREDENTIALS_PARAM, "true");
		ctx.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST));
		
		HandlerList handlers = new HandlerList();
		handlers.setHandlers(new Handler[] {
			ctx, // uses mapped servlets & serves files in '/public'
			new DefaultHandler() // returns 404
		});
		
		server.setHandler(handlers);
	}
	
	@Override
	public void addServletWithMapping(String path, RESTServlet servlet) {
		ServletHolder holder = new ServletHolder(servlet);
		ctx.addServlet(holder, path);
		
		super.addServletWithMapping(path, servlet);
	}

	@Override
	public void start() {
		try {
			server.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void stop() {
		try {
			server.stop();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void join() {
		try {
			server.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
