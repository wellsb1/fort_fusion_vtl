/*
 * Copyright (c) 2006 Wells Burke
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.forty11.fusionvtl.servlets;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 
 * @author Wells Burke
 *
 */
public class FusionVtlFilter extends FusionVtlServlet implements Filter
{
   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException
   {
      doRequest0((HttpServletRequest) request, (HttpServletResponse) response, chain);
   }

   @Override
   public void init(FilterConfig config) throws ServletException
   {
      init(new ServletConfigAdapter(config));
   }

   class ServletConfigAdapter implements ServletConfig
   {
      FilterConfig config = null;

      /**
       * @param config
       */
      public ServletConfigAdapter(FilterConfig config)
      {
         super();
         this.config = config;
      }

      public String getFilterName()
      {
         return config.getFilterName();
      }

      @Override
      public String getInitParameter(String arg0)
      {
         return config.getInitParameter(arg0);
      }

      @Override
      public Enumeration getInitParameterNames()
      {
         return config.getInitParameterNames();
      }

      @Override
      public ServletContext getServletContext()
      {
         return config.getServletContext();
      }

      @Override
      public String getServletName()
      {
         return getFilterName();
      }
   }

}
