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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.HttpSession;

import org.apache.commons.collections.map.CaseInsensitiveMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.event.EventCartridge;
import org.apache.velocity.app.event.implement.IncludeRelativePath;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
// import org.apache.velocity.runtime.log.Log4JLogChute;
// import org.apache.velocity.runtime.log.LogChute;
import org.apache.velocity.tools.view.ServletUtils;
import org.apache.velocity.tools.view.VelocityLayoutServlet;
import org.apache.velocity.tools.view.VelocityViewServlet;

import io.forty11.j.J;

/**
 * 
 * @author Wells Burke
 * @see VelocityLayoutServlet
 */
public class FusionVtlServlet extends VelocityViewServlet
{

   protected static Set<String>           TEMPLATE_EXTENSIONS = new HashSet(Arrays.asList(new String[]{".vm"}));

   /**
    * The context key that will hold the content of the screen.
    *
    * This key ($screen_content) must be present in the layout
    * template for the current screen to be rendered.
    */
   public static String                   KEY_SCREEN_CONTENT  = "content";

   /**
    * The context/parameter key used to specify an alternate
    * layout to be used for a request instead of the default layout.
    */
   public static String                   KEY_LAYOUT          = "layout";

   /**
    * The context key used to define the absolute path to 
    * lookup the root of this web app
    */
   public static String                   KEY_HOME            = "home";
   public static String                   KEY_REAL_HOME       = "realHome";

   public static String                   KEY_COMPONENT       = "component";

   public static String                   KEY_ACTION          = "action";

   public static String                   KEY_SERVLET         = "vmservlet";
   public static String                   KEY_CONTEXT         = "vmcontext";

   /**
    * context parameters that will be restored to their origional value after a call to include()
    */
   protected static Set<String>           IN_PARAMS           = new HashSet(Arrays.asList(new String[]{KEY_SCREEN_CONTENT, KEY_LAYOUT, KEY_HOME, KEY_COMPONENT, KEY_ACTION, KEY_SERVLET, KEY_CONTEXT}));

   // -------
   // -------

   public static String                   DEFAULT_LAYOUT      = "layout.vm";
   public static String                   SETTINGS_FILE       = "settings.vm";
   public static String                   SERVER_FILE         = "WEB-INF/server.vm";

   static Collection                      IGNORED_DIRS        = Arrays.asList(new String[]{"temp", "web-inf", "meta-inf", "images", "javascripts", "css", "img", "js"});

   static List<String>                    SWITCH_FILES        = Arrays.asList("switch");
   static List<String>                    INDEX_FILES         = Arrays.asList("index", "home", "default");

   /**
    * A mapping of the last part of a dir name to the files 
    * contained in the directory.  Files are mapped without  
    * their file extensions. Directories with a path component
    * matching one of IGNORED_DIRS is not mapped.
    */
   Map<String, Map<String, File>>         dirs                = new CaseInsensitiveMap();

   /**
    * When devMode is true, the server.vm file will  
    * get reloaded on each request and the settings.vm and 
    * layout.vm files will be looked up new each request 
    */
   boolean                                devMode             = false;

   Map<String, Map<String, List<String>>> templatesCache      = new Hashtable();
   Map<String, String>                    contextParamMap     = new HashMap<String, String>();

   Context                                serverContext       = null;

   String                                 realHome            = null;

   Log                                    paramLog            = null;
   Log                                    log                 = null;

   //boolean                                isFilter           = Filter.class.isAssignableFrom(getClass());

   /**
    * This is here to switch the logging of "ResourceManager : unable to find resource" from ERROR to WARN
    */
   //   public static class PieVmLogChute extends Log4JLogChute
   //   {
   //      @Override
   //      public void log(int level, String message, Throwable t)
   //      {
   //         super.log(level, message, t);
   //      }
   //
   //      @Override
   //      public void log(int level, String message)
   //      {
   //         if (level == LogChute.ERROR_ID && message != null && message.contains("ResourceManager : unable to find resource"))
   //         {
   //            level = LogChute.WARN_ID;
   //         }
   //         super.log(level, message);
   //      }
   //   }
   //
   //   /**
   //    * This is here to override the LOG SYSTEM to use our custom log system
   //    */
   //   @Override
   //   protected ExtendedProperties loadConfiguration(ServletConfig config) throws IOException
   //   {
   //      ExtendedProperties props = super.loadConfiguration(config);
   //      props.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, PieVmLogChute.class.getName());
   //      props.setProperty(PieVmLogChute.RUNTIME_LOG_LOG4J_LOGGER, PieVm.class.getName() + ".velocity");
   //      return props;
   //   }

   @Override
   public void init(ServletConfig config) throws ServletException
   {
      try
      {
         devMode = (config.getInitParameter("devMode") + "").equalsIgnoreCase("true");
         realHome = config.getServletContext().getRealPath("/");

         realHome = new File(realHome).getCanonicalPath();
         if (!realHome.endsWith("/"))
            realHome += "/";

         System.setProperty("PieVM.realHome", realHome);

         Enumeration paramNames = config.getInitParameterNames();
         while (paramNames.hasMoreElements())
         {
            String paramName = (String) paramNames.nextElement();
            if (paramName.startsWith("param"))
            {
               contextParamMap.put(paramName, config.getInitParameter(paramName));
            }
            else if (paramName.startsWith("SYSTEM-PROP-"))
            {
               String propName = paramName.substring("SYSTEM-PROP-".length(), paramName.length());
               System.setProperty(propName, config.getInitParameter(paramName));
            }
         }

         paramLog = LogFactory.getLog(FusionVtlServlet.class.getName() + ".paramLog");
         log = LogFactory.getLog(FusionVtlServlet.class.getName());

         super.init(config);

         //---------------------------------------
         //-- caches all dir names and the files in those
         //-- dirs to make it easier to look up the template
         //-- path for the requested /component/action

         List<File> dirStack = new ArrayList();
         File rootDir = new File(realHome);
         dirStack.add(rootDir);

         while (dirStack.size() > 0)
         {
            File dir = dirStack.remove(0);
            String dirName = dir.getName();
            if (!IGNORED_DIRS.contains(dirName.toLowerCase()) && !dirName.startsWith("."))
            {
               Map dirFiles = new CaseInsensitiveMap();

               File[] subfiles = dir.listFiles();
               for (int i = 0; subfiles != null && i < subfiles.length; i++)
               {
                  dirFiles.put(getSimpleName(subfiles[i].getName()), subfiles[i].getCanonicalFile());

                  if (subfiles[i].isDirectory())
                  {
                     dirStack.add(subfiles[i].getCanonicalFile());
                  }
               }

               try
               {
                  String fullPath = J.path(dir);
                  fullPath = trimSlashes(fullPath.substring(realHome.length(), fullPath.length()));
                  String[] parts = fullPath.split("/");
                  for (int i = 0; i < parts.length; i++)
                  {
                     StringBuffer path = new StringBuffer(parts[i]);
                     for (int j = i + 1; j < parts.length; j++)
                     {
                        path.append("/").append(parts[j]);
                     }
                     dirs.put(path.toString(), dirFiles);
                  }
               }
               catch (Exception ex)
               {
                  ex.printStackTrace();
               }
            }
         }

         if (log.isDebugEnabled())
         {
            List dirList = new ArrayList(dirs.keySet());
            Collections.sort(dirList);

            for (Object key : dirList)
            {
               System.out.println("-- " + key);
               Map<String, File> files = dirs.get(key);
               for (String file : files.keySet())
               {
                  System.out.println("  " + file + " - " + files.get(file));
               }
            }
            System.out.println("done");
         }

         if (log.isInfoEnabled())
         {
            log.info("PieVm initialized.");
         }
      }
      catch (Exception ex)
      {
         ex.printStackTrace();
      }
   }

   String getSimpleName(String fileName)
   {
      if (fileName.indexOf('.') < 0)
         return fileName;
      return fileName.substring(0, fileName.lastIndexOf('.'));
   }

   /**
    * Overridden to add #parse relative path support
    */
   @Override
   protected Context createContext(HttpServletRequest request, HttpServletResponse response)
   {
      Context viewToolContext = super.createContext(request, response);
      Context context = new VelocityContext(viewToolContext);
      //InternalEventContext iec = (InternalEventContext) context;
      //--
      //-- The EventCartridge is necessary to get
      //-- velocity to correctly #parse relative paths
      EventCartridge ec = new EventCartridge();
      ec.addEventHandler(new IncludeRelativePath());
      ec.attachToContext(context);

      return context;
   }

   @Override
   protected void fillContext(Context context, HttpServletRequest request)
   {
      //--
      //-- Evaluate the server.vm file if it exists
      try
      {
         if (isDevMode())
         {
            //always reevaluate when in devMode as 
            //the file may have been changed
            Template server = getTemplate(SERVER_FILE);
            server.merge(context, new StringWriter());
         }
         else
         {
            if (serverContext == null)
            {
               synchronized (this)
               {
                  if (serverContext == null)
                  {
                     serverContext = new VelocityContext();
                     serverContext.put("newline", "!!!\n");
                     Template server = getTemplate(SERVER_FILE);
                     server.merge(serverContext, new StringWriter());
                  }
               }
            }

            for (Object o : serverContext.getKeys())
            {
               context.put(o.toString(), serverContext.get(o.toString()));
            }
         }
      }
      catch (Exception ex)
      {
         ex.printStackTrace();
      }

      // Put servlet init params in the context
      for (String paramName : contextParamMap.keySet())
      {
         context.put(paramName, contextParamMap.get(paramName));
      }

      //--
      //-- put session values into the scope
      //-- override any previous values      
      HttpSession session = request.getSession(false);
      if (session != null)
      {
         Enumeration<String> enumer = session.getAttributeNames();
         while (enumer.hasMoreElements())
         {
            String key = enumer.nextElement();
            context.put(key, session.getAttribute(key));
         }
      }

      //--
      //-- put cookie values into the scope 
      //-- override any previous values
      Cookie[] cookies = request.getCookies();
      if (cookies != null)
      {
         for (Cookie cookie : cookies)
         {
            context.put(cookie.getName(), cookie.getValue());
         }
      }

      //--
      //-- standard CGI variables
      context.put("AUTH_TYPE", request.getAuthType());
      context.put("CONTENT_LENGTH", String.valueOf(request.getContentLength()));
      context.put("CONTENT_TYPE", request.getContentType());
      context.put("DOCUMENT_ROOT", realHome);
      context.put("PATH_INFO", request.getPathInfo());
      context.put("PATH_TRANSLATED", request.getPathTranslated());
      context.put("QUERY_STRING", request.getQueryString());
      context.put("REMOTE_ADDR", request.getRemoteAddr());
      context.put("REMOTE_HOST", request.getRemoteHost());
      context.put("REMOTE_USER", request.getRemoteUser());
      context.put("REQUEST_METHOD", request.getMethod());
      context.put("SCRIPT_NAME", request.getServletPath());
      context.put("SERVER_NAME", request.getServerName());
      context.put("SERVER_PORT", String.valueOf(request.getServerPort()));
      context.put("SERVER_PROTOCOL", request.getProtocol());
      context.put("SERVER_SOFTWARE", getServletContext().getServerInfo());

      //--
      //-- put request headers into the scope 
      //-- override any previous values
      Enumeration<String> headers = request.getHeaderNames();
      while (headers.hasMoreElements())
      {
         String key = headers.nextElement();
         context.put(key, request.getHeader(key));
      }

      //--
      //-- put request attributes into the scope 
      //-- override any previous values
      Enumeration<String> attribs = request.getAttributeNames();
      while (attribs.hasMoreElements())
      {
         String key = attribs.nextElement();
         context.put(key, request.getAttribute(key));
      }

      //--
      //-- put request parameters into the scope 
      //-- override any previous values      
      for (String key : (Collection<String>) request.getParameterMap().keySet())
      {
         context.put(key, request.getParameter(key));
      }

      //put remaining "servlet standard" variables into scope
      context.put("requestURI", request.getRequestURI());
      context.put("requestURL", request.getRequestURL().toString());
      context.put("queryString", request.getQueryString());

      //--
      //-- setup the "home" url variable
      String homeDir = request.getRequestURL().toString();
      int slash = homeDir.indexOf('/', homeDir.indexOf('/') + 2);
      if (slash > 0)
         homeDir = homeDir.substring(0, slash);

      int query = homeDir.indexOf("?");
      if (query > 0)
         homeDir = homeDir.substring(0, query);

      String contextPath = request.getContextPath();
      homeDir += contextPath;

      //if (!homeDir.endsWith("/"))
      //   homeDir += '/';
      if (homeDir.endsWith("/"))
         homeDir = homeDir.substring(0, homeDir.length() - 1);

      context.put(KEY_HOME, homeDir);

      //--
      //-- finally the most specific variables are
      //-- the ones that get added by convention
      //-- parsing of the path component of the url
      String path = getRequestPath(request);
      fillContextPathParams(path, context, request);

      //-- don't let any parameters override the 
      //-- servlet param
      context.put(KEY_SERVLET, this);
      context.put(KEY_CONTEXT, context);
      context.put(KEY_REAL_HOME, realHome);

      //if (paramLog.isDebugEnabled())
      {
         try
         {
            List<String> keys = new ArrayList();
            List<Context> contexts = new ArrayList();
            contexts.add(context);
            for (int i = 0; i < contexts.size(); i++)
            {
               Context ctx = contexts.get(i);

               for (Object o : ctx.getKeys())
               {
                  Object value = ctx.get(o.toString());
                  if (value instanceof Context)
                  {
                     if (!contexts.contains(value))
                        contexts.add((Context) value);
                  }
                  else
                  {
                     keys.add(o.toString());
                  }
               }
            }
            Collections.sort(keys);
            StringBuffer buff = new StringBuffer();
            buff.append("\r\n------------------\r\n");
            for (String key : keys)
            {
               buff.append(J.pad(key, 50)).append(" : ").append(context.get(key)).append("\r\n");
            }
            buff.append("------------------\r\n");
            paramLog.debug(buff.toString());
         }
         catch (Throwable ex)
         {
            ex.printStackTrace();
         }
      }
   }

   protected void fillContextPathParams(String path, Context context, HttpServletRequest request)
   {
      ActionInfo info = new ActionInfo(path, request);

      context.put(KEY_COMPONENT, info.component);
      context.put(KEY_ACTION, info.action);

      String[] args = info.args;

      if (args != null && args.length > 0)
      {
         for (int i = 0; i < args.length - 1; i += 2)
         {
            context.put(args[i], args[i + 1]);
         }
         for (int i = 0; i < args.length; i++)
         {
            context.put("arg" + (i), args[i]);
         }
      }
   }

   /**
    *  Handles with both GET and POST requests
    *
    *  @param request  HttpServletRequest object containing client request
    *  @param response HttpServletResponse object for the response
    */
   @Override
   protected void doRequest(HttpServletRequest request, HttpServletResponse response) throws IOException
   {
      doRequest0(request, response, null);
   }

   protected void doRequest0(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException
   {
      boolean validUri = true;
      String uri = request.getRequestURI();

      //bail early if this uri has a non valid file extension.
      //this will happen often when running this class as a Filter
      //instead of as a servlet.  For instance, ".html", ".jsp", or ".jpg"
      //request need to be ditched early
      if (uri != null && uri.lastIndexOf(".") > 0)
      {
         String ext = uri.substring(uri.lastIndexOf("."), uri.length());
         if (!TEMPLATE_EXTENSIONS.contains(ext))
         {
            validUri = false;
         }
      }

      if (chain != null && !validUri)
      {
         try
         {
            chain.doFilter(request, response);
         }
         catch (Exception ex)
         {
            ex.printStackTrace();
         }
         return;
      }
      else
      {
         boolean notFound = false;
         Context context = null;
         try
         {
            // first, get a context
            context = createContext(request, response);

            fillContext(context, request);

            // set the content type
            setContentType(request, response);

            // get the template
            Template template = !validUri ? null : getTemplate(null, request, response);

            if (template != null)
            {
               runSettings(context);
               runTemplate(context, template, true);
               runLayouts(context);
            }
            else
            {
               notFound = true;
               if (chain == null)
               {
                  //the settings are run here becuase the action template
                  //may not have been found but the context may have been found
                  //and its settings file (and the base settings)
                  //can could contain helpful 404 code 
                  runSettings(context);
                  run404(context);
               }
               else
               {
                  chain.doFilter(request, response);
                  return;
               }
            }
         }
         catch (Exception e)
         {
            try
            {
               e.printStackTrace();

               Object ru = context.get("requestURL");
               String reqUrl = "[unknown]";
               if (ru != null)
               {
                  reqUrl = ru.toString();
               }

               log.error("Unknown Error for request url: '" + reqUrl + "' - sending 500 response code.", e);
               context.put("exception", e);
               context.put("ex", e);
               //String st = Utils.getShortCause(Utils.getCause(e));

               context.put("stackTrace", J.getShortCause(e));

               run500(context);
            }
            catch (Exception ex)
            {
               log.error("Unknown Error, trying to run500().", ex);
               ex.printStackTrace();
            }
         }
         finally
         {
            //--
            //-- Write final result to the client
            if (!response.isCommitted() && !notFound)
            {
               String screenContent = (String) context.get(KEY_SCREEN_CONTENT);
               if (!J.empty(screenContent))
               {
                  screenContent = screenContent.trim();
                  //Writer writer = getResponseWriter(response);
                  Writer writer = response.getWriter();
                  try
                  {
                     writer.write(screenContent);
                  }
                  finally
                  {
                     writer.flush();
                     writer.close();
                  }
               }
            }
         }
      }
   }

   protected void runSettings(Context context) throws ResourceNotFoundException, ParseErrorException, Exception
   {
      //-- evaluate all settings files along the path
      //-- from the root/WEB-INF into the request target directory
      runTemplates(context, "settings.vm", true, -1, false);
   }

   protected void runLayouts(Context context) throws ResourceNotFoundException, ParseErrorException, Exception
   {
      //-- evaluate all layout files along the path
      //-- from the target directory out to the root/WEB-INF
      runTemplates(context, "layout.vm", false, -1, true);
   }

   protected void run404(Context context) throws ResourceNotFoundException, ParseErrorException, Exception
   {
      runTemplates(context, "404.vm", false, 1, true);
   }

   protected void run500(Context context) throws ResourceNotFoundException, ParseErrorException, Exception
   {
      runTemplates(context, "500.vm", false, 1, true);
   }

   protected void runTemplates(Context context, String fileName, boolean forward, int maxTemplates, boolean saveOutput) throws Exception
   {
      //-- evaluate all settings files along the path
      //-- from the web root to the request target directory
      String dir = (String) context.get(KEY_COMPONENT);
      List<String> files = findTemplates(dir, fileName, forward);
      if (files != null && !forward)
      {
         Collections.reverse(files);
      }

      for (int i = 0; files != null && i < files.size(); i++)
      {
         if (maxTemplates > 0 && i > maxTemplates)
            break;

         String file = files.get(i);
         Template template = getVelocityView().getVelocityEngine().getTemplate(file);
         if (template != null)
         {
            runTemplate(context, template, saveOutput);
         }
      }
   }

   public void runTemplate(Context context, Template template, boolean saveOutput) throws Exception
   {
      //--
      //-- Render the main template

      if (isDevMode())
      {
         System.out.println("Running Template: " + template.getName());
      }
      else if (log.isDebugEnabled())
      {
         log.debug("Running Template: " + template.getName());
      }

      
//      EventCartridge contextCartridge = null;//context.getEventCartridge();
//      contextCartridge.attachToContext(context)
//      if (contextCartridge != null)
//      {
//          contextCartridge.setRuntimeServices(rsvc);
//          includeResourcePath = contextCartridge.includeEvent(context, includeResourcePath, currentResourcePath, directiveName);
//      }
//      return includeResourcePath;
      
      
      StringWriter sw = new StringWriter();
      template.merge(context, sw);

      if (saveOutput)
      {
         context.put(KEY_SCREEN_CONTENT, sw.toString());
      }
   }

   protected List<String> findTemplates(String dirName, String fileName, boolean forward)
   {
      List<String> templates = null;

      if (!isDevMode())
      {
         //check for cached templates if not in devMode
         Map<String, List<String>> dir = templatesCache.get(dirName);
         if (dir != null)
         {
            templates = dir.get(fileName);
         }

         if (!forward && templates != null)
         {
            // reverse this here because it will get reversed back again in runTemplates()
            // without this non-dev-mode was causing a weird condition where the layouts were getting confused every other click
            // this was because the order of the templates was being reversed on each request
            Collections.reverse(templates);
         }
      }

      if (templates != null || dirName == null)
         return templates;

      templates = new ArrayList();

      int rootPathLength = realHome.split("/").length;
      Map<String, File> dirFiles = dirs.get(dirName);
      if (dirFiles != null && dirFiles.size() > 0)
      {

         File anyTemplate = dirFiles.values().iterator().next();
         List<String> dirs = new ArrayList(Arrays.asList(J.path(anyTemplate).split("/")));
         do
         {
            dirs.remove(dirs.size() - 1);
            String path = fileName;

            for (int i = dirs.size() - 1; i >= 0; i--)
            {
               path = dirs.get(i) + "/" + path;
            }

            try
            {
               //find layout files
               path = path.substring(realHome.length() - 1, path.length());
               Template template = getVelocityView().getVelocityEngine().getTemplate(path);
               if (template != null)
               {
                  templates.add(path);
               }
            }
            catch (Exception ex)
            {
               //ok, not error
            }
         }
         while (dirs.size() > 0 && dirs.size() > rootPathLength);
      }

      try
      {
         Template last = getTemplate("WEB-INF/" + fileName);
         if (last != null)
            templates.add("WEB-INF/" + fileName);
      }
      catch (Exception ex)
      {

      }

      Collections.reverse(templates);

      if (!isDevMode())
      {
         Map<String, List<String>> dir = templatesCache.get(dirName);
         if (dir == null)
         {
            dir = new HashMap();
            templatesCache.put(dirName, dir);
         }
         dir.put(fileName, templates);
      }

      return templates;
   }

   @Override
   protected Template getTemplate(HttpServletRequest request, HttpServletResponse response) throws ResourceNotFoundException, ParseErrorException
   {
      return getTemplate(null, request, response);
   }

   protected Template getTemplate(String requestPath, HttpServletRequest request, HttpServletResponse response) throws ResourceNotFoundException, ParseErrorException
   {
      String templatePath = getTemplateFile(requestPath, request);
      if (templatePath != null)
      {
         templatePath = templatePath.substring(realHome.length(), templatePath.length());
      }

      templatePath = templatePath != null ? templatePath : ServletUtils.getPath(request);

      Template template = null;
      if (response == null)
      {
         try
         {
            template = getTemplate(templatePath);
         }
         catch (Exception ex)
         {

         }
      }
      else
      {
         try
         {
            //template = getTemplate(templatePath, response.getCharacterEncoding());
            template = getTemplate(templatePath);
         }
         catch (Exception ex)
         {

         }
      }

      if (template != null && !template.getName().endsWith(".vm"))
         template = null;

      return template;
   }

   protected String getTemplateFile(String requestPath, HttpServletRequest request)
   {
      ActionInfo info = new ActionInfo(requestPath, request);
      if (info.templateFile != null)
      {
         try
         {
            return info.templateFile.getCanonicalPath();
         }
         catch (Exception ex)
         {
            ex.printStackTrace();
         }
      }

      return null;

   }

   protected String getRequestPath(HttpServletRequest request)
   {
      String path = ServletUtils.getPath(request);
      String servletPath = request.getServletPath();
      String actionPath = path;//
      if (path.length() > servletPath.length())
         actionPath = path.substring(servletPath.length(), path.length());

      actionPath = trimSlashes(actionPath);

      return actionPath;
   }

   /**
    * Gets a parameter from http request parameters, and if it does not exist, falls back to attributes.
    * @param parameterName parameter name to look for
    * @param request the HttpServletRequest
    * @return the value of the parameter if it exists, or the attribute if the parameter does not exist and the attribute does exist AND is a string, or null.
    */
   protected static String getParameter(String parameterName, HttpServletRequest request)
   {
      if (request == null)
         return null;

      String param = request.getParameter(parameterName);
      if (param != null)
         return param;

      Object o = request.getAttribute(parameterName);
      if (o instanceof String)
         return (String) o;

      return null;
   }

   public class ActionInfo
   {
      String   path         = null;
      String   component    = null;
      String   action       = null;
      String[] args         = new String[0];

      File     templateFile = null;

      /**
      * Finds the "deepest" matching velocity file.
      * 
      * @param requestPath
      * @param request
      */
      public ActionInfo(String requestPath, HttpServletRequest request)
      {
         Map<String, File> dirFiles = null;

         if (requestPath == null)
            requestPath = getRequestPath(request);

         path = requestPath;
         component = getParameter("component", request);
         action = getParameter("action", request);

         if (action != null && component == null)
         {
            if (action.indexOf('.') > 0 && action.indexOf('.') < action.length() - 1)
            {
               component = action.substring(0, action.indexOf('.'));
               action = action.substring(action.indexOf('.') + 1, action.length());
            }
         }

         if (component == null && action == null)
         {
            String[] parts = requestPath.split("/");

            String tempPath = null;
            int lastDir = -1;
            for (int i = -1; i < parts.length; i++)
            {
               if (i == -1)
               {
                  tempPath = "";
               }
               else
               {
                  tempPath = i == 0 ? parts[0] : tempPath + "/" + parts[i];
               }

               if (dirs.containsKey(tempPath))
               {
                  dirFiles = dirs.get(tempPath);
                  path = tempPath;
                  lastDir = i;
               }
               else
               {
                  break;
               }
            }

            component = lastDir >= 0 ? parts[lastDir] : "";
            if (lastDir < parts.length - 1)
            {
               action = parts[lastDir + 1];
            }

            if (lastDir < parts.length - 2)
            {
               args = new String[parts.length - (lastDir + 2)];
               System.arraycopy(parts, lastDir + 2, args, 0, args.length);
            }
         }
         else
         {
            dirFiles = dirs.get(component);
         }

         if (dirFiles != null)
         {
            for (String switchFile : SWITCH_FILES)
            {
               templateFile = dirFiles.get(switchFile);
               if (templateFile != null)
                  break;
            }

            if (templateFile == null && action != null)
            {
               templateFile = dirFiles.get(action);
            }

            if (templateFile == null && action == null)
            {
               for (String indexFile : INDEX_FILES)
               {
                  templateFile = dirFiles.get(indexFile);
                  if (templateFile != null)
                     break;
               }
            }
         }
      }
   }

   /* 
   +------------------------------------------------------------------------------+
   | Public API for VM Macros
   +------------------------------------------------------------------------------+
   */

   public String render(String path, HttpServletRequest request, HttpServletResponse response, Context context) throws ResourceNotFoundException, ParseErrorException, Exception
   {
      if (path.startsWith("/"))
      {
         try
         {
            ImportResponseWrapper wrapper = new ImportResponseWrapper(response);
            request.getRequestDispatcher(path).include(request, wrapper);
            String output = wrapper.getString();
            return output;
         }
         catch (Exception ex)
         {
            return ex.getMessage();
         }
      }
      else if (path.startsWith("http://") || path.startsWith("https://"))
      {
         try
         {
            try
            {
               byte[] content = read(path);
               if (content == null || content.length == 0)
                  throw new IOException("Requested stream was null or empty: " + path);

               return new String(content);
            }
            catch (Exception ex)
            {
               return ex.getMessage();
            }
         }
         catch (Throwable ex)
         {
            return ex.getMessage();
         }
      }
      else
      {
         //save known params to restore them after this call
         Map params = new HashMap();
         for (String key : IN_PARAMS)
         {
            params.put(key, context.get(key));
         }

         fillContextPathParams(path, context, request);

         Template template = getTemplate(path, request, response);

         if (template != null)
         {
            runSettings(context);
            runTemplate(context, template, true);
            runLayouts(context);
         }

         String screenContent = (String) context.get(KEY_SCREEN_CONTENT);

         //restore params
         for (String key : IN_PARAMS)
         {
            context.put(key, params.get(key));

         }

         return screenContent;
      }
   }

   /* 
   +------------------------------------------------------------------------------+
   | Bean Properties
   +------------------------------------------------------------------------------+
   */

   public boolean isDevMode()
   {
      return devMode;
   }

   public void setDevMode(boolean devMode)
   {
      this.devMode = devMode;
   }

   /* 
   +------------------------------------------------------------------------------+
   | Helper Classes
   +------------------------------------------------------------------------------+
   */

   private class ImportResponseWrapper extends HttpServletResponseWrapper
   {

      //************************************************************
      // Overview

      /*
       * We provide either a Writer or an OutputStream as requested.
       * We actually have a true Writer and an OutputStream backing
       * both, since we don't want to use a character encoding both
       * ways (Writer -> OutputStream -> Writer).  So we use no
       * encoding at all (as none is relevant) when the target resource
       * uses a Writer.  And we decode the OutputStream's bytes
       * using OUR tag's 'charEncoding' attribute, or ISO-8859-1
       * as the default.  We thus ignore setLocale() and setContentType()
       * in this wrapper.
       *
       * In other words, the target's asserted encoding is used
       * to convert from a Writer to an OutputStream, which is typically
       * the medium through with the target will communicate its
       * ultimate response.  Since we short-circuit that mechanism
       * and read the target's characters directly if they're offered
       * as such, we simply ignore the target's encoding assertion.
       */

      //************************************************************
      // Data
      public static final String    DEFAULT_ENCODING = "ISO-8859-1";

      /** The Writer we convey. */
      private StringWriter          sw               = new StringWriter();

      /** A buffer, alternatively, to accumulate bytes. */
      private ByteArrayOutputStream bos              = new ByteArrayOutputStream();

      /** A ServletOutputStream we convey, tied to this Writer. */
      private ServletOutputStream   sos              = new ServletOutputStream()
                                                        {
                                                           @Override
                                                           public void write(int b) throws IOException
                                                           {
                                                              bos.write(b);
                                                           }

                                                           @Override
                                                           public boolean isReady()
                                                           {
                                                              return true;
                                                           }

                                                           @Override
                                                           public void setWriteListener(WriteListener writeListener)
                                                           {

                                                           }

                                                        };

      /** 'True' if getWriter() was called; false otherwise. */
      private boolean               isWriterUsed;

      /** 'True if getOutputStream() was called; false otherwise. */
      private boolean               isStreamUsed;

      /** The HTTP status set by the target. */
      private int                   status           = 200;

      //************************************************************
      // Constructor and methods

      /** Constructs a new ImportResponseWrapper. */
      public ImportResponseWrapper(HttpServletResponse response)
      {
         super(response);
      }

      /** Returns a Writer designed to buffer the output. */
      @Override
      public PrintWriter getWriter()
      {
         if (isStreamUsed)
            //throw new IllegalStateException(Resources.getMessage("IMPORT_ILLEGAL_STREAM"));
            throw new IllegalStateException("IMPORT_ILLEGAL_STREAM");
         isWriterUsed = true;
         return new PrintWriter(sw);
      }

      /** Returns a ServletOutputStream designed to buffer the output. */
      @Override
      public ServletOutputStream getOutputStream()
      {
         if (isWriterUsed)
            //throw new IllegalStateException(Resources.getMessage("IMPORT_ILLEGAL_WRITER"));
            throw new IllegalStateException("IMPORT_ILLEGAL_WRITER");
         isStreamUsed = true;
         return sos;
      }

      /** Has no effect. */
      @Override
      public void setContentType(String x)
      {
         // ignore
      }

      /** Has no effect. */
      @Override
      public void setLocale(Locale x)
      {
         // ignore
      }

      @Override
      public void setStatus(int status)
      {
         this.status = status;
      }

      public int getStatus()
      {
         return status;
      }

      /** 
       * Retrieves the buffered output, using the containing tag's 
       * 'charEncoding' attribute, or the tag's default encoding,
       * <b>if necessary</b>.
       */
      // not simply toString() because we need to throw
      // UnsupportedEncodingException
      public String getString() throws UnsupportedEncodingException
      {
         if (isWriterUsed)
            return sw.toString();
         else if (isStreamUsed)
         {
            return bos.toString(DEFAULT_ENCODING);
         }
         else
            return ""; // target didn't write anything
      }
   }

   public static byte[] read(String url) throws IOException
   {
      final int MEGA_BYTE = 1048576;
      return read(url, MEGA_BYTE);
   }

   public static byte[] read(String url, long max) throws IOException
   {
      InputStream in = null;
      ByteArrayOutputStream out = null;

      try
      {
         in = new URL(url).openStream();
         out = new ByteArrayOutputStream();

         int buffSize = (int) Math.min(1024, max);
         int total = 0;

         byte[] buffer = new byte[buffSize];
         int len;
         while ((len = in.read(buffer)) != -1)
         {
            total += len;
            if (total > max)
               throw new IOException("The returned stream is longer than the max " + max + " bytes");

            out.write(buffer, 0, len);
         }

         return out.toByteArray();
      }
      finally
      {
         try
         {
            in.close();
            out.flush();
         }
         catch (Exception ex)
         {

         }
      }
   }

   public static String trimSlashes(String str)
   {
      while (str.startsWith("/"))
      {
         str = str.substring(1, str.length());
      }
      while (str.endsWith("/"))
      {
         str = str.substring(0, str.length() - 1);
      }

      return str;
   }
}
