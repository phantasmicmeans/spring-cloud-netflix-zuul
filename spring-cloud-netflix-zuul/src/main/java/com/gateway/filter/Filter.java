package com.gateway.filter;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

public class Filter extends ZuulFilter {

	//private static Logger log = LoggerFactory.getLogger(Filter.class);
	
	
	  @Override
	  public String filterType() {
	    return "pre";
	  }

	  @Override
	  public int filterOrder() {
	    return 1;
	  }

	  @Override
	  public boolean shouldFilter() {
	    return true;
	  }
	  
	  @Override
	  public Object run()
	  {
		  RequestContext ctx = RequestContext.getCurrentContext();
		  HttpServletRequest request = ctx.getRequest();
		  
		  return null;
		  
	  }
}
