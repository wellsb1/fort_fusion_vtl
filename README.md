#FusionVTL
	
Ancient but still kinda awesome, FusionVTL is a simple templating framework for the JVM based Apache Velocity Templating 
Language (VTL) inspired by the Fusebox 3 framework.

I originally built this framework circa 2005 when I needed a JSP alternative to allow end users to safely script 
web UI customizations.  VTL was clean, simple and fit my purpose but I thought it needed a few "organizing principles" to make
it a little more scalable from a development perspective.

Throwing it back to the early 2000's I met a bunch of guys involved in the "Fusebox" ColdFusion framework development community.  
They had created what I considered to be a simple but useful convention for organizing template centric web apps.  
I loosely adopted their high level patterns with a few tweaks and FusionVTL is the result.  

I have used this framework often over the years when I needed super simple web templating.  It turned out to 
really shine at creating complex SQL from user supplied values for custom report web UIs.  

Of course, service side MVC frameworks killed, or at least significantly wounded, the page centric model years ago.  
Now client side MVC JavaScript frameworks are doing much of the heavy lifting directly connecting to JSON REST APIs
and I often find the need for simple templates again.

Just the other day, I noticed that Amazon's API gateway uses VTL to allow API developers to transform request inputs/outputs
and I had to smile since what was old seemed to be new again.


- Wells, December 2018

	
	
##What it is FusionVTL

FusionVTL is a servlet framework that extends the excellent [VelocityViewServlet](http://velocity.apache.org/tools/devel/view.servlet.html)
with a few standard conventions and extra directives.  	
		
You may want to brush up on the following before trying to understand what FusionVTL does vs. what Velocity offers natively and what 
[VelocityView](http://velocity.apache.org/tools/devel/view.html) offers on top of that.

 * [Velocity Engine Home](http://velocity.apache.org/engine/2.0/)
 * [Velocity Langauge Users Guide]
 * [Velocity User Guide](http://velocity.apache.org/engine/2.0/user-guide.html)
 * [VTL Reference](http://velocity.apache.org/engine/2.0/vtl-reference.html)
 * [Velocity Tools Project](http://velocity.apache.org/tools/devel/)
 * [Velocit View Servlet](http://velocity.apache.org/tools/3.0/view-servlet.html)


##What it does

At its most basic level FusionVTL does three things:
 1. Uses a simple convention to intelligently maps URLs to one or more Velocity macro files that will be run to service a request.
 1. Runs standard setup 'settings.vm' files at server initialization and before each request
 1. Runs standard 'layout.vm' files after each request that let you decorate the output 


##Important VelocityView Servlet Configuration Files

 * WEB-INF/velocity.properties - Velocity Engine configuration parameters. Only mess with this if you REALLY know what you are doing.
 * WEB-INF/tools.xml - OPTIONAL - file that configures java bean helper classes that will be placed in Velocity page context on each
   request. This is where you put the the beans that will allow you to connect to your application business logic as well 
   as misc UI layer helpers.
 * WEB-INF/VM_global_library.vm - VM file loaded once by the framework that contains helpful macro definitions. Define useful
   velocity "subroutines" here and they will be in scope on each request. See [Velocimacro doco]http://velocity.apache.org/engine/2.0/vtl-reference.html) 
   for more info.

##FusionVTL Optional Framework Files
 * WEB-INF/server.vm - OPTIONAL - this file is run once on servlet container initialization unless "devMode" is set. In which 
   case it will execute on every request
 * WEB-INF/settings.vm - OPTIONAL - a file that will be run at the very beginning of each request
 *
 * /404.vm - Optional file used to customize 404 error messages
 * /500.vm - Optional file used to customize 500 error messages


##URL Mapping

FusionVTL attempts to prevent spaghetti coding by creating URL to file mapping standard to the use of Velocity. 
Left to its own device, VelocityView renders VM files wherever they are found on disk by the reference URL path. 
This can cause chaos without simple patters to follow. FusionVTL interprets a URL as the following:


```{http://example.org/[servlet_context]}/${component}/[${action}]/[{[name]/[value]/...[name]/[value]}]?[standard query string]```

 * {http://example.org/[servlet_context]} - The first grouping is the host, port, and servlet context. This URL is stored in the 
   variable "${home}" for use in any .vm macro
 
 * ${component} - maps the URL to the first web root directory (by DFS search) with the corresponding name. This essentially allows
   you to "shortcut" or "alias" a single URL part to an arbitrarily nested directory in the web root. The component variable is stored as
   "${component}" for use in any .vm macro
   
 * ${action} - The optional action is used to select a specific .vm file with the directory referenced by the ${component} part of the
   URL. Additionally, if ${action}.vm does not exist but a file with the specific name of 'switch.vm' does exist in the ${component} directory,
   the "switch.vm" will be called by convention. If ${action} is not supplied but switch.vm exists, 'switch.vm' will still be called. \
   If 'switch.vm' does not exist but an 'index.vm' file does, then 'index.vm' will be called.
   
 * Any optional URL path parts after the ${action}, will be considered name/value pairs and make available in scope as ${nameN},
   ${nameN+1} etc.
   
 * If a VM file can not be found by the above component/action mapping scheme outlines above but there is a direct URL path to
   file path match, then the file match template will be run.
   

So, at this point, all FusionVTL has done is give you a simple way to index directly to a specific .vm file with a couple of conventions.

Examples:

http://www.example.com/book/ - Might map to:
 * wwwroot/book/index.vm OR
 * wwwroot/book/switch.vm OR
 * wwwroot/library/random_sub_dir/book/switch.vm OR


http://www.example.com/book/list - Might map to:
 * wwwroot/book/list.vm OR
 * wwwroot/library/book/list.vm OR
 * wwwroot/library/book/switch.vm OR
 * wwwroot/book/index.vm


##Request Lifecycle

###Pre Page Setup - settings.vm

One each request the framework looks for a set of know setup files that should be invoked before the file referenced by the
URL. These files are specifically named "settings.vm". Any "settings.vm" file found along the file path from the web root to the
specifically ${component} directory will be executed in that order. From the examples above that could mean that the following "settings.vm"
files are executed in listed order before any actual "work" of request is executed:

 1. wwwroot/WEB-INF/settings.vm (special case considered the "root" directory)
 1. wwwroot/settings.vm
 1. wwwroot/library/settings.vm
 1. wwwroot/library/book/settings.vm


These "settings.vm" files are not necessary but can be useful for variable setup for common functionality
Any text output generated by the settings.vm files is ignored and not included in the output of request.

###Post page Layout - layout.vm

The purpose of the "layout.vm" file is to enable nested layouts. Think old school html tables before the uber css days. Just as
"settings.vm" files are run before the requested page, "layout.vm" files are run after the requested page.  In the above example the
following "layout.vm" files would be run in this order if they existed:

 1. wwwroot/library/book/layout.vm
 1. wwwroot/library/layout.vm
 1. wwwroot/layout.vm
 1. wwwroot/WEB-INF/layout.vm


An important difference in settings.vm and layout.vm invocations is that all of the text content generated by the .vms prior to the
invocation of a layout.vm files (remember settings.vm file text output is discarded) is passed into the executing layout.vm file in the
specially named variable "$content". A layout.vm file could discard all content previously generated or wrap it in a
header/footer/table etc. You could even run a regex on the $content or really do whatever you wanted to with it. If the
layout file exists but does not reference $content, you may not see the expected output in your browser.

A layout file like below may be used to wrap the output of a specific component/action in a header/footer.  In this example
#parse is a built in VTL function. 

```velocity
#parse('header.vm')

$content

#parse('footer.vm')
```


###Variables

FusionVTL does its darndest to put every possible variable directly in scope for the .vm programmer. Name/Values paires
are put into scope in the following order (values added second override values added first)

 * Session params
 * Cookie params
 * Standard CGI vars including:
   * AUTH_TYPE
   * CONTENT_LENGTH
   * CONTENT_TYPE
   * DOCUMENT_ROOT
   * PATH_INFO
   * PATH_TRANSLATED
   * QUERY_STRING
   * REMOTE_ADDR
   * REMOTE_HOST
   * REMOTE_USER
   * REQUEST_METHOD
   * SCRIPT_NAME
   * SERVER_NAME
   * SERVER_PORT
   * SERVER_PROTOCOL
   * SERVER_SOFTWARE
 * Request Headers
 * Request Attributes
 * Request Parameters


See the [JavaServlet spec](http://download.oracle.com/javaee/1.3/api/javax/servlet/http/HttpServletRequest.html) for more information on these if needed.

Additionally, FusionVTL puts the following variables in scope:

 * ${requestURI}
 * ${requestURL}
 * ${queryString}
 * ${home}
 * ${component}
 * ${action}
 * name/value pairs from the URL path
 * ${content}


##Filter vs Servlet
FusionVTL can be run as a servlet or as a filter. It can be run as a filter so that you don't have to have a servlet mapping in your URLs.
In filter mode, if FusionVTL can locate a .vm file as expected, it will execute the FusionVTL lifecycle (settings files, then the requested .vm,
then layouts) and then exit. It will not forward the filter chain on. If a .vm file is not found, then filter chain is allowed to run
normally. Using this technique you may have something like the following:

 * http://www.example.com/FusionVTL/book/list - "FusionVTL" is the servlet mapping trigger. In this came "$home" would be "http://www.example.com/FusionVTL" OR
 * http://www.example.com/book/list - is picked up and handled by FusionVTL in filter mode WHILE
 * http://www.example.com/soap/ - is mapped to a Axis web service servlet


###FusionVTL Built in Directives

FusionVTL adds to the Velocity language with a handful of useful directives. (See Velocity documentation for extending Velocity Engine
with custom directives)

####Switch
The "switch" tag is used to create a switch statement off of any variable. Typically it would be used to run a switch statement off of
the $action parameter inside of a "switch.vm" file.

```velocity
#switch($action)
	
	##==========================================================
	#case("action1")
		## do business logic
                #parse('view_for_action1.vm')
	
	##==========================================================
	#case("action2")
	#case("action1")
		## do business logic
        #parse('view_for_action2.vm')
	
	##==========================================================
	#default
		#parse("some_default_view.vm")
#end
```

####Save
The "save" directive is used for saving the output of any Velocity code to a variable instead of sending it as output. Just about
any Velocity code is valid inside of the tag.  This is useful for constructing complex SQL.

```
\#save('repeat')
  Hello World
#end
```
\$repeat
\$repeat
\$repeat
```

\#save('sql')
 SELECT * FROM 'customer' where fname = $first_name
\#end
\#set($rows = $dao.execute($sql))

\#save('saveVar')
   \#parse("../widgits/selectList.vm")
\#end


####Layout
The "layout" directive takes the content inside the directive and passes that generated output to the referenced layout file in the
$content variable. This is useful for sub layout routines. 

```
#layout('some_sub_layout_file.vm')
 this content will be passed to 'some_sub_layout_file.vm' as $content
 and the rendering of 'some_sub_layout_file.vm' will included inline.
#

```

###Dev Mode
"devMode" is a parameter that can be set on the FusionVTL servlet in the web.xml file. Doing so changes the behavior of the request lifecycle as follows:

 * Additional sysout logging
 * /WEB-INF/server.vm is reevaluated every request
 * The file system is rescanned every request for new files that may have been added. In production mode the file system is scanned
   only once so the URL mapping process is very fast. It is assumed in production mode you are not adding new .vm files that need to be
   mapped to URL paths. If that were the case, they would not be mapped until the server was restarted.

