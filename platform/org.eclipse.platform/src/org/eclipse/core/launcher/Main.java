/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.launcher;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.channels.FileLock;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * The framework to run.  This is used if the bootLocation (-boot) is not specified.
 * The value can be specified on the command line as -framework.
* Startup class for Eclipse. Creates a class loader using
* supplied URL of platform installation, loads and calls
* the Eclipse Boot Loader.  The startup arguments are as follows:
* <dl>
* <dd>
*    -application &lt;id&gt;: the identifier of the application to run
* </dd>
* <dd>
*    -arch &lt;architecture&gt;: sets the processor architecture value
* </dd>
* <dd>
*    -boot &lt;location&gt;: the location, expressed as a URL, of the platform's boot.jar.
* <i>Deprecated: replaced by -configuration</i>
* </dd>
* <dd>
*    -classloaderproperties [properties file]: activates platform class loader enhancements using 
* the class loader properties file at the given location, if specified. The (optional) file argument 
* can be either a file path or an absolute URL.
* </dd>
* <dd>
*    -configuration &lt;location&gt;: the location, expressed as a URL, for the Eclipse platform 
* configuration file. The configuration file determines the location of the Eclipse platform, the set 
* of available plug-ins, and the primary feature.
* </dd>
* <dd>
*    -consolelog : enables log to the console. Handy when combined with -debug
* </dd>
* <dd>
*    -data &lt;location&gt;: sets the workspace location and the default location for projects
* </dd>
* <dd>
*    -debug [options file]: turns on debug mode for the platform and optionally specifies a location
* for the .options file. This file indicates what debug points are available for a
* plug-in and whether or not they are enabled. If a location is not specified, the platform searches
* for the .options file under the install directory.
* </dd>
* <dd>
*    -dev [entries]: turns on dev mode and optionally specifies comma-separated class path entries
* which are added to the class path of each plug-in
* </dd>
* <dd>
*    -feature &lt;id&gt;: the identifier of the primary feature. The primary feature gives the launched 
* instance of Eclipse its product personality, and determines the product customization 
* information.
* </dd>
* <dd>
*    -keyring &lt;location&gt;: the location of the authorization database on disk. This argument
* has to be used together with the -password argument.
* </dd>
* <dd>
*    -nl &lt;locale&gt;: sets the name of the locale on which Eclipse platform will run
* </dd>
* <dd>
*    -nolazyregistrycacheloading : deactivates platform plug-in registry cache loading optimization. 
* By default, extensions' configuration elements will be loaded from the registry cache (when 
* available) only on demand, reducing memory footprint. This option will force the registry cache 
* to be fully loaded at startup.
* </dd>
*  <dd>
*    -nopackageprefixes: deactivates classloader package prefixes optimization
* </dd> 
*  <dd>
*    -noregistrycache: bypasses the reading and writing of an internal plug-in registry cache file
* </dd>
* <dd>
*    -os &lt;operating system&gt;: sets the operating system value
* </dd>
* <dd>
*    -password &lt;passwd&gt;: the password for the authorization database
* </dd>
* <dd>
*    -plugins &lt;location&gt;: the arg is a URL pointing to a file which specs the plugin 
* path for the platform.  The file is in property file format where the keys are user-defined 
* names and the values are comma separated lists of either explicit paths to plugin.xml 
* files or directories containing plugins (e.g., .../eclipse/plugins). 
* <i>Deprecated: replaced by -configuration</i>
* </dd>
* <dd>
*    -plugincustomization &lt;properties file&gt;: the location of a properties file containing default 
* settings for plug-in preferences. These default settings override default settings specified in the 
* primary feature. Relative paths are interpreted relative to the directory that eclipse was started 
* from.
* </dd> 
* <dd>
*    -ws &lt;window system&gt;: sets the window system value
* </dd>
* </dl>
*/
public class Main {
	/**
	 * Indicates whether this instance is running in debug mode.
	 */
	protected boolean debug = false;

	/**
	 * The location of the launcher to run.
	 */
	protected String bootLocation = null;

	/**
	 * The location of the install root
	 */
	protected String installLocation = null;

	/**
	 * The location of the configuration information for this instance
	 */
	protected String configurationLocation = null;

	/**
	 * The location of the configuration information in the install root
	 */
	protected String parentConfigurationLocation = null;

	/**
	 * The id of the bundle that will contain the framework to run.  Defaults to org.eclipse.osgi.
	 */
	protected String framework = OSGI;

	/**
	 * The extra development time class path entries.
	 */
	protected String devClassPath = null;

	/**
	 * Indicates whether this instance is running in development mode.
	 */
	protected boolean inDevelopmentMode = false;

	// splash handling
	private String showSplash = null;
	private String endSplash = null;
	private boolean cmdInitialize = false;
	private Process showProcess = null;
	private boolean splashDown = false;
	private final Runnable endSplashHandler = new Runnable() {
		public void run() {
			takeDownSplash();
		}
	};

	// command line args
	private static final String BOOT = "-boot"; //$NON-NLS-1$
	private static final String FRAMEWORK = "-framework"; //$NON-NLS-1$
	private static final String INSTALL = "-install"; //$NON-NLS-1$
	private static final String INITIALIZE = "-initialize"; //$NON-NLS-1$
	private static final String DEBUG = "-debug"; //$NON-NLS-1$
	private static final String DEV = "-dev"; //$NON-NLS-1$
	private static final String DATA = "-data"; //$NON-NLS-1$
	private static final String CONFIGURATION = "-configuration"; //$NON-NLS-1$
	private static final String SHOWSPLASH = "-showsplash"; //$NON-NLS-1$
	private static final String ENDSPLASH = "-endsplash"; //$NON-NLS-1$
	private static final String SPLASH_IMAGE = "splash.bmp"; //$NON-NLS-1$

	private static final String OSGI = "org.eclipse.osgi"; //$NON-NLS-1$
	private static final String STARTER = "org.eclipse.core.runtime.adaptor.EclipseStarter"; //$NON-NLS-1$
	private static final String PLATFORM_URL = "platform:/base/"; //$NON-NLS-1$

	// constants: configuration file location
	private static final String CONFIG_DIR = "configuration/"; //$NON-NLS-1$
	private static final String CONFIG_FILE = "config.ini"; //$NON-NLS-1$
	private static final String CONFIG_FILE_TEMP_SUFFIX = ".tmp"; //$NON-NLS-1$
	private static final String CONFIG_FILE_BAK_SUFFIX = ".bak"; //$NON-NLS-1$
	private static final String ECLIPSE = "eclipse"; //$NON-NLS-1$
	private static final String PRODUCT_SITE_MARKER = ".eclipseproduct"; //$NON-NLS-1$
	private static final String PRODUCT_SITE_ID = "id"; //$NON-NLS-1$
	private static final String PRODUCT_SITE_VERSION = "version"; //$NON-NLS-1$

	// constants: System property keys and/or configuration file elements
	/** @deprecated remove this constant */
	private static final String PROP_INSTALL_LOCATION= "osgi.installLocation"; //$NON-NLS-1$

	private static final String PROP_USER_HOME = "user.home"; //$NON-NLS-1$
	private static final String PROP_USER_DIR = "user.dir"; //$NON-NLS-1$
	private static final String PROP_INSTALL_AREA = "osgi.install.area"; //$NON-NLS-1$
	private static final String PROP_CONFIG_AREA = "osgi.configuration.area"; //$NON-NLS-1$
	private static final String PROP_CONFIG_CASCADED = "osgi.configuration.cascaded"; //$NON-NLS-1$
	private static final String PROP_FRAMEWORK = "osgi.framework"; //$NON-NLS-1$
	private static final String PROP_SPLASHPATH = "osgi.splashPath"; //$NON-NLS-1$
	private static final String PROP_SPLASHLOCATION = "osgi.splashLocation"; //$NON-NLS-1$
	private static final String PROP_CLASSPATH = "osgi.frameworkClassPath"; //$NON-NLS-1$
	private static final String PROP_EOF = "eof"; //$NON-NLS-1$

	// Data mode constants for user, configuration and data locations.
	private static final String NONE = "<none>"; //$NON-NLS-1$
	private static final String NO_DEFAULT = "<noDefault>"; //$NON-NLS-1$
	private static final String USER_HOME = "<user.home>"; //$NON-NLS-1$
	private static final String USER_DIR = "<user.dir>"; //$NON-NLS-1$

	// log file handling
	protected static final String SESSION = "!SESSION"; //$NON-NLS-1$
	protected static final String ENTRY = "!ENTRY"; //$NON-NLS-1$
	protected static final String MESSAGE = "!MESSAGE"; //$NON-NLS-1$
	protected static final String STACK = "!STACK"; //$NON-NLS-1$
	protected static final int ERROR = 4;
	protected static final String PLUGIN_ID = "org.eclipse.core.launcher"; //$NON-NLS-1$
	protected File logFile = null;
	protected BufferedWriter log = null;
	protected boolean newSession = true;

	protected String[] arguments;

	static class Identifier {
		private static final String DELIM = ". "; //$NON-NLS-1$
		private int major, minor, service;
		Identifier(int major, int minor, int service) {
			super();
			this.major = major;
			this.minor = minor;
			this.service = service;
		}
		Identifier(String versionString) {
			super();
			StringTokenizer tokenizer = new StringTokenizer(versionString, DELIM);

			// major
			if (tokenizer.hasMoreTokens())
				major = Integer.parseInt(tokenizer.nextToken());

			// minor
			if (tokenizer.hasMoreTokens())
				minor = Integer.parseInt(tokenizer.nextToken());

			// service
			if (tokenizer.hasMoreTokens())
				service = Integer.parseInt(tokenizer.nextToken());
		}
		/**
		 * Returns true if this id is considered to be greater than or equal to the given baseline.
		 * e.g. 
		 * 1.2.9 >= 1.3.1 -> false
		 * 1.3.0 >= 1.3.1 -> false
		 * 1.3.1 >= 1.3.1 -> true
		 * 1.3.2 >= 1.3.1 -> true
		 * 2.0.0 >= 1.3.1 -> true
		 */
		boolean isGreaterEqualTo(Identifier minimum) {
			if (major < minimum.major)
				return false;
			if (major > minimum.major)
				return true;
			// major numbers are equivalent so check minor
			if (minor < minimum.minor)
				return false;
			if (minor > minimum.minor)
				return true;
			// minor numbers are equivalent so check service
			return service >= minimum.service;
		}
	}

	/**
	 * Executes the launch.
	 * 
	 * @return the result of performing the launch
	 * @param args command-line arguments
	 * @exception Exception thrown if a problem occurs during the launch
	 */
	protected Object basicRun(String[] args) throws Exception {
		System.getProperties().setProperty("eclipse.debug.startupTime", Long.toString(System.currentTimeMillis())); //$NON-NLS-1$
		String[] passThruArgs = processCommandLine(args);
		processConfiguration();
		// need to ensure that getInstallLocation is called at least once to initialize the value.
		// Do this AFTER processing the configuration to allow the configuration to set
		// the install location.  
		getInstallLocation();

		// locate boot plugin (may return -dev mode variations)
		URL[] bootPath = getBootPath(bootLocation);

		// splash handling is done here, because the default case needs to know
		// the location of the boot plugin we are going to use
		handleSplash(bootPath);

		// load the BootLoader and startup the platform
		URLClassLoader loader = new URLClassLoader(bootPath, null);
		Class clazz = loader.loadClass(STARTER);
		Method method = clazz.getDeclaredMethod("run", new Class[] { String[].class, Runnable.class }); //$NON-NLS-1$
		try {
			return method.invoke(clazz, new Object[] { passThruArgs, endSplashHandler });
		} catch (InvocationTargetException e) {
			if (e.getTargetException() instanceof Error)
				throw (Error) e.getTargetException();
			else if (e.getTargetException() instanceof Exception)
				throw (Exception) e.getTargetException();
			else //could be a subclass of Throwable!
				throw e;
		}
	}
	/**
	 * Returns a string representation of the given URL String.  This converts
	 * escaped sequences (%..) in the URL into the appropriate characters.
	 * NOTE: due to class visibility there is a copy of this method
	 *       in InternalBootLoader
	 */
	private String decode(String urlString) {
		//try to use Java 1.4 method if available
		try {
			Class clazz = URLDecoder.class;
			Method method = clazz.getDeclaredMethod("decode", new Class[] { String.class, String.class }); //$NON-NLS-1$
			//first encode '+' characters, because URLDecoder incorrectly converts 
			//them to spaces on certain class library implementations.
			if (urlString.indexOf('+') >= 0) {
				int len = urlString.length();
				StringBuffer buf = new StringBuffer(len);
				for (int i = 0; i < len; i++) {
					char c = urlString.charAt(i);
					if (c == '+')
						buf.append("%2B");
					//$NON-NLS-1$
					else
						buf.append(c);
				}
				urlString = buf.toString();
			}
			Object result = method.invoke(null, new Object[] { urlString, "UTF-8" }); //$NON-NLS-1$
			if (result != null)
				return (String) result;
		} catch (Exception e) {
			//JDK 1.4 method not found -- fall through and decode by hand
		}
		//decode URL by hand
		boolean replaced = false;
		byte[] encodedBytes = urlString.getBytes();
		int encodedLength = encodedBytes.length;
		byte[] decodedBytes = new byte[encodedLength];
		int decodedLength = 0;
		for (int i = 0; i < encodedLength; i++) {
			byte b = encodedBytes[i];
			if (b == '%') {
				byte enc1 = encodedBytes[++i];
				byte enc2 = encodedBytes[++i];
				b = (byte) ((hexToByte(enc1) << 4) + hexToByte(enc2));
				replaced = true;
			}
			decodedBytes[decodedLength++] = b;
		}
		if (!replaced)
			return urlString;
		try {
			return new String(decodedBytes, 0, decodedLength, "UTF-8"); //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
			//use default encoding
			return new String(decodedBytes, 0, decodedLength);
		}
	}
	/**
	 * Returns the result of converting a list of comma-separated tokens into an array
	 * 
	 * @return the array of string tokens
	 * @param prop the initial comma-separated string
	 */
	private String[] getArrayFromList(String prop) {
		if (prop == null || prop.trim().equals("")) //$NON-NLS-1$
			return new String[0];
		Vector list = new Vector();
		StringTokenizer tokens = new StringTokenizer(prop, ","); //$NON-NLS-1$
		while (tokens.hasMoreTokens()) {
			String token = tokens.nextToken().trim();
			if (!token.equals("")) //$NON-NLS-1$
				list.addElement(token);
		}
		return list.isEmpty() ? new String[0] : (String[]) list.toArray(new String[list.size()]);
	}
	/**
	 * Returns the <code>URL</code>-based class path describing where the boot classes
	 * are located when running in development mode.
	 * 
	 * @return the url-based class path
	 * @param base the base location
	 * @exception MalformedURLException if a problem occurs computing the class path
	 */
	private URL[] getDevPath(URL base) throws IOException {
		String devBase = base.toExternalForm();
		ArrayList result = new ArrayList(5);
		if (inDevelopmentMode)
			addDevEntries(devBase, result); //$NON-NLS-1$
		//The jars from the base always need to be added, even when running in dev mode (bug 46772)
		addBaseJars(devBase, result);
		return (URL[]) result.toArray(new URL[result.size()]);
	}

	private void addBaseJars(String devBase, ArrayList result) throws IOException {
		String baseJarList = System.getProperty(PROP_CLASSPATH);
		if (baseJarList == null) {
			Properties defaults = loadProperties(devBase + "eclipse.properties");
			baseJarList = defaults.getProperty(PROP_CLASSPATH);
			if (baseJarList == null)
				throw new IOException("Unable to initialize " + PROP_CLASSPATH);
			System.getProperties().put(PROP_CLASSPATH, baseJarList);
		}
		String[] baseJars = getArrayFromList(baseJarList);
		for (int i = 0; i < baseJars.length; i++) {
			String string = baseJars[i];
			try {
				URL url = new URL(string);
				addEntry(url, result);
			} catch (MalformedURLException e) {
				addEntry(new URL(devBase + string), result);
			}
		}
	}

	private void addEntry(URL url, List result) {
		if (new File(url.getFile()).exists())
			result.add(url);
	}
	private void addDevEntries(String devBase, List result) throws MalformedURLException {
		String[] locations = getArrayFromList(devClassPath);
		for (int i = 0; i < locations.length; i++) {
			String spec = devBase + locations[i];
			char lastChar = spec.charAt(spec.length() - 1);
			URL url;
			if ((spec.endsWith(".jar") || (lastChar == '/' || lastChar == '\\'))) //$NON-NLS-1$
				url = new URL(spec);
			else
				url = new URL(spec + "/"); //$NON-NLS-1$
			addEntry(url, result);
		}
	}
	/**
	 * Returns the <code>URL</code>-based class path describing where the boot classes are located.
	 * 
	 * @return the url-based class path
	 * @param base the base location
	 * @exception MalformedURLException if a problem occurs computing the class path
	 */
	private URL[] getBootPath(String base) throws IOException {
		URL url = null;
		if (base != null) {
			url = new URL(base);
		} else {
			// search in the root location
			url = new URL(getInstallLocation());
			String path = url.getFile() + "plugins"; //$NON-NLS-1$
			path = searchFor(framework, path);
			if (path == null)
				throw new RuntimeException("Could not find framework"); //$NON-NLS-1$
			// add on any dev path elements
			url = new URL(url.getProtocol(), url.getHost(), url.getPort(), path);
		}
		if (System.getProperty(PROP_FRAMEWORK) == null)
			System.getProperties().put(PROP_FRAMEWORK, url.toExternalForm());
		if (debug) 
			System.out.println("Framework located:\n    " + url.toExternalForm());
		URL[] result = getDevPath(url);
		if (debug) {
			System.out.println("Framework classpath:"); //$NON-NLS-1$
			for (int i = 0; i < result.length; i++)
				System.out.println("    " + result[i].toExternalForm()); //$NON-NLS-1$
		}
		return result;
	}

	/**
	 * Searches for the given target directory starting in the "plugins" subdirectory
	 * of the given location.  If one is found then this location is returned; 
	 * otherwise an exception is thrown.
	 * 
	 * @return the location where target directory was found
	 * @param start the location to begin searching
	 */
	private String searchFor(final String target, String start) {
		FileFilter filter = new FileFilter() {
			public boolean accept(File candidate) {
				return candidate.isDirectory() && (candidate.getName().equals(target) || candidate.getName().startsWith(target + "_")); //$NON-NLS-1$
			}
		};
		File[] candidates = new File(start).listFiles(filter); //$NON-NLS-1$
		if (candidates == null)
			return null;
		String result = null;
		Object maxVersion = null;
		for (int i = 0; i < candidates.length; i++) {
			String name = candidates[i].getName();
			String version = ""; //$NON-NLS-1$ // Note: directory with version suffix is always > than directory without version suffix
			int index = name.indexOf('_');
			if (index != -1)
				version = name.substring(index + 1);
			Object currentVersion = getVersionElements(version);
			if (maxVersion == null) {
				result = candidates[i].getAbsolutePath();
				maxVersion = currentVersion;
			} else {
				if (compareVersion((Object[]) maxVersion, (Object[]) currentVersion) < 0) {
					result = candidates[i].getAbsolutePath();
					maxVersion = currentVersion;
				}
			}
		}
		if (result == null)
			return null;
		return result.replace(File.separatorChar, '/') + "/"; //$NON-NLS-1$
	}
	/**
	* Compares version strings. 
	* @return result of comparison, as integer;
	* <code><0</code> if left < right;
	* <code>0</code> if left == right;
	* <code>>0</code> if left > right;
	*/
	private int compareVersion(Object[] left, Object[] right) {

		int result = ((Integer) left[0]).compareTo((Integer) right[0]); // compare major
		if (result != 0)
			return result;

		result = ((Integer) left[1]).compareTo((Integer) right[1]); // compare minor
		if (result != 0)
			return result;

		result = ((Integer) left[2]).compareTo((Integer) right[2]); // compare service
		if (result != 0)
			return result;

		return ((String) left[3]).compareTo((String) right[3]); // compare qualifier
	}

	/**
	 * Do a quick parse of version identifier so its elements can be correctly compared.
	 * If we are unable to parse the full version, remaining elements are initialized
	 * with suitable defaults.
	 * @return an array of size 4; first three elements are of type Integer (representing
	 * major, minor and service) and the fourth element is of type String (representing
	 * qualifier). Note, that returning anything else will cause exceptions in the caller.
	 */
	private Object[] getVersionElements(String version) {
		Object[] result = { new Integer(0), new Integer(0), new Integer(0), "" }; //$NON-NLS-1$
		StringTokenizer t = new StringTokenizer(version, "."); //$NON-NLS-1$
		String token;
		int i = 0;
		while (t.hasMoreTokens() && i < 4) {
			token = t.nextToken();
			if (i < 3) {
				// major, minor or service ... numeric values
				try {
					result[i++] = new Integer(token);
				} catch (Exception e) {
					// invalid number format - use default numbers (0) for the rest
					break;
				}
			} else {
				// qualifier ... string value
				result[i++] = token;
			}
		}
		return result;
	}

	private URL buildURL(String spec) {
		if (spec == null)
			return null;
		// if the spec is a file: url then see if it is absolute.  If not, break it up
		// and make it absolute.
		if (spec.startsWith("file:")) {
			File file = new File(spec.substring(5));
			if (!file.isAbsolute())
				spec = file.getAbsolutePath();
		}
		try {
			return new URL(spec);
		} catch (MalformedURLException e) {
			if (spec.startsWith("file:"))
				return null;
			return buildURL("file:" + spec);
		}
	}

	private URL buildLocation(String property, URL defaultLocation, String userDefaultAppendage) {
		URL result = null;
		String location = System.getProperty(property);
		System.getProperties().remove(property);
		// if the instance location is not set, predict where the workspace will be and 
		// put the instance area inside the workspace meta area.
		if (location == null) 
			result = defaultLocation;
		else if (location.equalsIgnoreCase(NONE))
			return null;
		else if (location.equalsIgnoreCase(NO_DEFAULT))
			result = buildURL(location);
		else {
			if (location.equalsIgnoreCase(USER_HOME)) 
				location = computeDefaultUserAreaLocation(userDefaultAppendage);
			if (location.equalsIgnoreCase(USER_DIR)) 
				location = new File(System.getProperty(PROP_USER_DIR), userDefaultAppendage).getAbsolutePath();
			result = buildURL(location);
		}
		if (result != null)
			System.getProperties().put(property, result.toExternalForm());
		return result;
	}

	/** 
	 * Retuns the default file system path for the configuration location.
	 * By default the configuration information is in the installation directory
	 * if this is writeable.  Otherwise it is located somewhere in the user.home
	 * area relative to the current product. 
	 * @return the default file system path for the configuration information
	 */
	private String computeDefaultConfigurationLocation() {
		// 1) We store the config state relative to the 'eclipse' directory if possible
		// 2) If this directory is read-only 
		//    we store the state in <user.home>/.eclipse/<application-id>_<version> where <user.home> 
		//    is unique for each local user, and <application-id> is the one 
		//    defined in .eclipseproduct marker file. If .eclipseproduct does not
		//    exist, use "eclipse" as the application-id.

		String install = getInstallLocation();
		// TODO a little dangerous here.  Basically we have to assume that it is a file URL.
		File installDir = new File(install.substring(5));
		if (install.startsWith("file:")) {
			if (installDir.canWrite()) 
				return installDir.getAbsolutePath() + File.separator + CONFIG_DIR;
		}
		// We can't write in the eclipse install dir so try for some place in the user's home dir
		return computeDefaultUserAreaLocation(CONFIG_DIR);
	}

	/**
	 * Returns a files system path for an area in the user.home region related to the
	 * current product.  The given appendage is added to this base location
	 * @param pathAppendage the path segments to add to computed base
	 * @return a file system location in the user.home area related the the current
	 *   product and the given appendage
	 */
	private String computeDefaultUserAreaLocation(String pathAppendage) {
		//    we store the state in <user.home>/.eclipse/<application-id>_<version> where <user.home> 
		//    is unique for each local user, and <application-id> is the one 
		//    defined in .eclipseproduct marker file. If .eclipseproduct does not
		//    exist, use "eclipse" as the application-id.
		URL installURL = buildURL(getInstallLocation());
		if (installURL == null)
			return null;
		File installDir = new File(installURL.getPath());
		String appName = "." + ECLIPSE; //$NON-NLS-1$
		File eclipseProduct = new File(installDir, PRODUCT_SITE_MARKER );
		if (eclipseProduct.exists()) {
			Properties props = new Properties();
			try {
				props.load(new FileInputStream(eclipseProduct));
				String appId = props.getProperty(PRODUCT_SITE_ID);
				if (appId == null || appId.trim().length() == 0)
					appId = ECLIPSE;
				String appVersion = props.getProperty(PRODUCT_SITE_VERSION);
				if (appVersion == null || appVersion.trim().length() == 0)
					appVersion = ""; //$NON-NLS-1$
				appName += File.separator + appId + "_" + appVersion; //$NON-NLS-1$
			} catch (IOException e) {
				// Do nothing if we get an exception.  We will default to a standard location 
				// in the user's home dir.
			}
		}
		String userHome = System.getProperty(PROP_USER_HOME);
		return new File(userHome, appName + "/" + pathAppendage).getAbsolutePath(); //$NON-NLS-1$
	}
	/**
	 * Runs this launcher with the arguments specified in the given string.
	 * 
	 * @param argString the arguments string
	 * @exception Exception thrown if a problem occurs during launching
	 */
	public static void main(String argString) {
		Vector list = new Vector(5);
		for (StringTokenizer tokens = new StringTokenizer(argString, " "); tokens.hasMoreElements();) //$NON-NLS-1$
			list.addElement(tokens.nextElement());
		main((String[]) list.toArray(new String[list.size()]));
	}

	/**
	* Runs the platform with the given arguments.  The arguments must identify
	* an application to run (e.g., <code>-application com.example.application</code>).
	* After running the application <code>System.exit(N)</code> is executed.
	* The value of N is derived from the value returned from running the application.
	* If the application's return value is an <code>Integer</code>, N is this value.
	* In all other cases, N = 0.
	* <p>
	* Clients wishing to run the platform without a following <code>System.exit</code>
	* call should use <code>run()</code>.
	* </p>
	* 
	* @param args the command line arguments
	* @see #run
	*/
	public static void main(String[] args) {
		int result = new Main().run(args);
		System.exit(result);
	}
	
	/**
	* Runs the platform with the given arguments.  The arguments must identify
	* an application to run (e.g., <code>-application com.example.application</code>).
	* Returns the value returned from running the application.
	* If the application's return value is an <code>Integer</code>, N is this value.
	* In all other cases, N = 0.
	*
	* @param args the command line arguments
	*/	
	public int run(String[] args) {
		Object result = null;
		arguments = args;
		// Check to see if we are running with a compatible VM.
		// If not, then return exit code "14" which will be recognized
		// by the executable and an appropriate message will be displayed
		// to the user.
		if (!isCompatible())
			return 14;
		// Check to see if there is already a platform running in
		// this workspace. If there is, then return an exit code of "15" which
		// will be recognized by the executable and an appropriate message
		// will be displayed to the user.
		if (isAlreadyRunning())
			return 15;
		try {
			result = basicRun(args);
		} catch (Throwable e) {
			// try and take down the splash screen.
			takeDownSplash();
			log("Exception launching the Eclipse Platform:"); //$NON-NLS-1$
			log(e);
			// Return "unlucky" 13 as the exit code. The executable will recognize
			// this constant and display a message to the user telling them that
			// there is information in their log file.
			return 13;
		}
		return result instanceof Integer ? ((Integer) result).intValue() : 0;
	}
	/**
	 * Processes the command line arguments.  The general principle is to NOT
	 * consume the arguments and leave them to be processed by Eclipse proper.
	 * There are a few args which are directed towards main() and a few others which
	 * we need to know about.  Very few should actually be consumed here.
	 * 
	 * @return the arguments to pass through to the launched application
	 * @param args the command line arguments
	 */
	protected String[] processCommandLine(String[] args) throws Exception {
		int[] configArgs = new int[100];
		configArgs[0] = -1; // need to initialize the first element to something that could not be an index.
		int configArgIndex = 0;
		for (int i = 0; i < args.length; i++) {
			boolean found = false;
			// check for args without parameters (i.e., a flag arg)
			// check if debug should be enabled for the entire platform
			if (args[i].equalsIgnoreCase(DEBUG)) {
				debug = true;
				// passed thru this arg (i.e., do not set found = true)
				continue;
			}

			// TODO confirm with Update that this arg can be removed.  We should be able to ignore this
			// case (only used for some splash logic) and consume the arg
			// check if this is initialization pass
			if (args[i].equalsIgnoreCase(INITIALIZE)) {
				cmdInitialize = true;
				// passed thru this arg (i.e., do not set found = true)
				continue;
			}

			// check if development mode should be enabled for the entire platform
			// If this is the last arg or there is a following arg (i.e., arg+1 has a leading -), 
			// simply enable development mode.  Otherwise, assume that that the following arg is
			// actually some additional development time class path entries.  This will be processed below.
			if (args[i].equalsIgnoreCase(DEV) && ((i + 1 == args.length) || ((i + 1 < args.length) && (args[i + 1].startsWith("-"))))) { //$NON-NLS-1$
				inDevelopmentMode = true;
				// do not mark the arg as found so it will be passed through
				continue;
			}

			// done checking for args.  Remember where an arg was found 
			if (found) {
				configArgs[configArgIndex++] = i;
				continue;
			}
			// check for args with parameters. If we are at the last argument or if the next one
			// has a '-' as the first character, then we can't have an arg with a parm so continue.
			if (i == args.length - 1 || args[i + 1].startsWith("-")) //$NON-NLS-1$
				continue;
			String arg = args[++i];

			// look for the development mode and class path entries.  
			if (args[i - 1].equalsIgnoreCase(DEV)) {
				inDevelopmentMode = true;
				devClassPath = arg;
				continue;
			}

			// look for the framework to run
			if (args[i - 1].equalsIgnoreCase(FRAMEWORK)) {
				framework = arg;
				found = true;
			}

			// look for explicitly set install root
			// Consume the arg here to ensure that the launcher and Eclipse get the 
			// same value as each other.  
			if (args[i - 1].equalsIgnoreCase(INSTALL)) {
				found = true;
				URL url = buildURL(arg);
				if (url == null)
					continue;
				System.getProperties().put(PROP_INSTALL_AREA, url.toExternalForm()); 
			}

			// look for the configuration to use.  
			// Consume the arg here to ensure that the launcher and Eclipse get the 
			// same value as each other.  
			if (args[i - 1].equalsIgnoreCase(CONFIGURATION)) {
				System.getProperties().put(PROP_CONFIG_AREA, arg);
				found = true;
			}

			// look for token to use to show the splash screen
			if (args[i - 1].equalsIgnoreCase(SHOWSPLASH)) {
				showSplash = arg;
				found = true;
			}

			// look for token to use to end the splash screen
			if (args[i - 1].equalsIgnoreCase(ENDSPLASH)) {
				endSplash = arg;
				found = true;
			}

			// consume the old -boot option
			if (args[i - 1].equalsIgnoreCase(BOOT)) 
				found = true;

			// done checking for args.  Remember where an arg was found 
			if (found) {
				configArgs[configArgIndex++] = i - 1;
				configArgs[configArgIndex++] = i;
			}
		}
		// remove all the arguments consumed by this argument parsing
		if (configArgIndex == 0)
			return args;
		String[] passThruArgs = new String[args.length - configArgIndex];
		configArgIndex = 0;
		int j = 0;
		for (int i = 0; i < args.length; i++) {
			if (i == configArgs[configArgIndex])
				configArgIndex++;
			else
				passThruArgs[j++] = args[i];
		}
		return passThruArgs;
	}

	private String getConfigurationLocation() {
		if (configurationLocation != null)
			return configurationLocation;
		URL result = buildLocation(PROP_CONFIG_AREA, null, CONFIG_DIR);
		if (result == null)
			result = buildURL(computeDefaultConfigurationLocation());
		if (result == null)
			return null;
		// for backward compatibility, remove the filename if the location is a .cfg file.  config
		// locations are directories not files now.
		configurationLocation = result.toExternalForm();
		if (configurationLocation.endsWith(".cfg")) {
			int index = configurationLocation.lastIndexOf('/');
			configurationLocation = configurationLocation.substring(0, index + 1);
		}
		if (!configurationLocation.endsWith("/"))
			configurationLocation += "/";
		System.getProperties().put(PROP_CONFIG_AREA, configurationLocation);
		if (debug)
			System.out.println("Configuration location:\n    " + configurationLocation);
		return configurationLocation;
	}

	private void processConfiguration() {
		loadConfiguration(getConfigurationLocation());
		if (!"false".equalsIgnoreCase(System.getProperty(PROP_CONFIG_CASCADED))) {
			URL parentConfigurationLocation = buildURL(getInstallLocation() + CONFIG_DIR);
			if (parentConfigurationLocation != null)
				loadConfiguration(parentConfigurationLocation.toExternalForm());
		}
		// setup the 
		String urlString = System.getProperty(PROP_FRAMEWORK, null);
		if (urlString != null) {
			if (!urlString.endsWith("/")) {
				urlString += "/";
				System.getProperties().put(PROP_FRAMEWORK, urlString);
			}
			bootLocation = resolve(urlString);
		}
	}

	/**
	 * Returns url of the location this class was loaded from
	 */
	private String getInstallLocation() {
		// TODO make the install location property end in / if it is a dir
		if (installLocation != null)
			return installLocation;

		// value is not set so compute the default and set the value
		installLocation = System.getProperty(PROP_INSTALL_AREA);
		// TODO remove this if when we get off the old property value
		if (installLocation == null)
			installLocation = System.getProperty(PROP_INSTALL_LOCATION);			
		if (installLocation != null) {
			System.getProperties().put(PROP_INSTALL_AREA, installLocation); 
			// TODO remove this set when we get off the old property
			System.getProperties().put(PROP_INSTALL_LOCATION, installLocation); 
			if (debug)
				System.out.println("Install location:\n    " + installLocation);
			return installLocation;
		}

		URL result = Main.class.getProtectionDomain().getCodeSource().getLocation();
		String path = decode(result.getFile());
		path = new File(path).getAbsolutePath().replace(File.separatorChar, '/');
		// TODO need a better test for windows
		// If on Windows then canonicalize the drive letter to be lowercase.
		if (File.separatorChar == '\\')
			if (Character.isUpperCase(path.charAt(0))) {
				char[] chars = path.toCharArray();
				chars[0] = Character.toLowerCase(chars[0]);
				path = new String(chars);
			}
		if (path.endsWith(".jar")) //$NON-NLS-1$
			path = path.substring(0, path.lastIndexOf("/") + 1); //$NON-NLS-1$
		try {
			installLocation = new URL(result.getProtocol(), result.getHost(), result.getPort(), path).toExternalForm();
			System.getProperties().put(PROP_INSTALL_AREA, installLocation); 
			// TODO remove this set when we get off the old property
			System.getProperties().put(PROP_INSTALL_LOCATION, installLocation); 
		} catch (MalformedURLException e) {
			// TODO Very unlikely case.  log here.  
		}
		if (debug)
			System.out.println("Install location:\n    " + installLocation);
		return installLocation;
	}

	/*
	 * Load the given configuration file
	 */
	private void loadConfiguration(String url) {
		Properties result = null;
		url += CONFIG_FILE;
		try {
			if (debug)
				System.out.print("Configuration file:\n    " + url.toString()); //$NON-NLS-1$
			result = loadProperties(url);
			if (debug)
				System.out.println("  loaded"); //$NON-NLS-1$
		} catch (IOException e) {
			if (debug)
				System.out.println("  not found or not read"); //$NON-NLS-1$
		}
		mergeProperties(System.getProperties(), result);
	}

	private Properties loadProperties(String location) throws IOException {
		// try to load saved configuration file (watch for failed prior save())
		URL url = buildURL(location);
		if (url == null)
			return null;
		Properties result = null;
		IOException originalException = null;
		try {
			result = load(url, null); // try to load config file
		} catch (IOException e1) {
			originalException = e1;
			try {
				result = load(url, CONFIG_FILE_TEMP_SUFFIX); // check for failures on save
			} catch (IOException e2) {
				try {
					result = load(url, CONFIG_FILE_BAK_SUFFIX); // check for failures on save
				} catch (IOException e3) {
					throw originalException; // we tried, but no config here ...
				}
			}
		}
		return result;
	}

	/*
	 * Load the configuration  
	 */
	private Properties load(URL url, String suffix) throws IOException {
		// figure out what we will be loading
		if (suffix != null && !suffix.equals("")) //$NON-NLS-1$
			url = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile() + suffix);

		// try to load saved configuration file
		Properties props = new Properties();
		InputStream is = null;
		try {
			is = url.openStream();
			props.load(is);
			// check to see if we have complete config file
			if (!PROP_EOF.equals(props.getProperty(PROP_EOF)))
				throw new IOException("Incomplete configuration file: " + url.toExternalForm()); //$NON-NLS-1$
		} finally {
			if (is != null)
				try {
					is.close();
				} catch (IOException e) {
					//ignore failure to close
				}
		}
		return props;
	}

	/*
	 * Handle splash screen.
	 * We support 2 startup scenarios:
	 * 
	 * (1) the executable launcher put up the splash screen. In that
	 *     scenario we are invoked with -endsplash command which is
	 *     fully formed to take down the splash screen
	 * 
	 * (2) the executable launcher did not put up the splash screen,
	 *     but invokes Eclipse with partially formed -showsplash command.
	 *     In this scenario we determine which splash to display (based on 
	 *     feature information) and then call -showsplash command. 
	 * 
	 * In both scenarios we pass a handler (Runnable) to the platform.
	 * The handler is called as a result of the launched application calling
	 * Platform.endSplash(). In the first scenario this results in the
	 * -endsplash command being executed. In the second scenarios this
	 * results in the process created as a result of the -showsplash command
	 * being destroyed.
	 * 
	 * @param bootPath search path for the boot plugin
	 */
	private void handleSplash(URL[] defaultPath) {

		// run without splash if we are initializing
		if (cmdInitialize) {
			showSplash = null;
			endSplash = null;
			return;
		}

		// if -endsplash is specified, use it and ignore any -showsplash command
		if (endSplash != null) {
			showSplash = null;
			return;
		}

		// check if we are running without a splash screen
		if (showSplash == null)
			return;

		// determine the splash path
		String location = getSplashLocation(defaultPath);
		if (debug)
			System.out.println("Splash path:\n    " + location); //$NON-NLS-1$
		if (location == null)
			return;

		// Parse the showsplash command into its separate arguments.
		// The command format is: 
		//     <executable> -show <magicArg> [<splashPath>]
		// If either the <executable> or the <splashPath> arguments contain a
		// space, Runtime.getRuntime().exec( String ) will not work, even
		// if both arguments are enclosed in double-quotes. The solution is to
		// use the Runtime.getRuntime().exec( String[] ) method.
		String[] cmd = new String[(location != null ? 4 : 3)];
		int sIndex = 0;
		int eIndex = showSplash.indexOf(" -show"); //$NON-NLS-1$
		if (eIndex == -1)
			return; // invalid -showsplash command
		cmd[0] = showSplash.substring(sIndex, eIndex);
		sIndex = eIndex + 1;
		eIndex = showSplash.indexOf(" ", sIndex); //$NON-NLS-1$
		if (eIndex == -1)
			return; // invalid -showsplash command
		cmd[1] = showSplash.substring(sIndex, eIndex);
		cmd[2] = showSplash.substring(eIndex + 1);
		if (location != null)
			cmd[3] = location;
		try {
			showProcess = Runtime.getRuntime().exec(cmd);
		} catch (Exception e) {
			// continue without splash ...
			log("Exception showing splash screen."); //$NON-NLS-1$
			log(e);
		}
		return;
	}

	/*
	 * take down the splash screen. Try both take-down methods just in case
	 * (only one should ever be set)
	 */
	protected void takeDownSplash() {
		if (splashDown) // splash is already down
			return;

		// check if -endsplash was specified
		if (endSplash != null) {
			try {
				Runtime.getRuntime().exec(endSplash);
			} catch (Exception e) {
				//ignore failure to end splash
			}
		}

		// check if -showsplash was specified and executed
		if (showProcess != null) {
			showProcess.destroy();
			showProcess = null;
		}

		splashDown = true;
	}

	/*
	 * Return path of the splash image to use.  First search the defined splash path.
	 * If that does not work, look for a default splash.  Currently the splash must be in the file system
	 * so the return value here is the file system path.
	 */
	private String getSplashLocation(URL[] bootPath) {
		String result = System.getProperty(PROP_SPLASHLOCATION);
		if (result != null)
			return result;
		String splashPath = System.getProperty(PROP_SPLASHPATH);
		if (splashPath != null) {
			String[] entries = getArrayFromList(splashPath);
			ArrayList path = new ArrayList(entries.length);
			for (int i = 0; i < entries.length; i++) {
				String entry = resolve(entries[i]);
				if (entry == null || entry.startsWith("file:")) {	 
					File entryFile = new File(entry.substring(5).replace('/', File.separatorChar));
					entry = searchFor(entryFile.getName(), entryFile.getParent());
					if (entry != null)
						path.add(entry);
				} else
					log("Invalid splash path entry: " + entries[i]);
			}
			// see if we can get a splash given the splash path
			result = searchForSplash((String[]) path.toArray(new String[path.size()]));
			if (result != null) {
				System.getProperties().put(PROP_SPLASHLOCATION, result);
				return result;
			}
		}

		// can't find it on the splashPath so look for a default splash
		String temp = bootPath[0].getFile(); // take the first path element
		temp = temp.replace('/', File.separatorChar);
		int ix = temp.lastIndexOf("plugins" + File.separator); //$NON-NLS-1$
		if (ix != -1) {
			int pix = temp.indexOf(File.separator, ix + 8);
			if (pix != -1) {
				temp = temp.substring(0, pix);
				result = searchForSplash(new String[] { temp });
				if (result != null) 
					System.getProperties().put(PROP_SPLASHLOCATION, result);
			}
		}
		return result;
	}

	/*
	 * Do a locale-sensitive lookup of splash image
	 */
	private String searchForSplash(String[] searchPath) {
		if (searchPath == null)
			return null;

		// get current locale information
		String localePath = Locale.getDefault().toString().replace('_', File.separatorChar);

		// search the specified path
		while (localePath != null) {
			String suffix;
			if (localePath.equals("")) { //$NON-NLS-1$
				// look for nl'ed splash image
				suffix = SPLASH_IMAGE;
			} else {
				// look for default splash image
				suffix = "nl" + File.separator + localePath + File.separator + SPLASH_IMAGE; //$NON-NLS-1$
			}

			// check for file in searchPath
			for (int i = 0; i < searchPath.length; i++) {
				String path = searchPath[i];
				if (!path.endsWith(File.separator))
					path += File.separator;
				path += suffix;
				File result = new File(path);
				if (result.exists())
					return result.getAbsolutePath(); // return the first match found [20063]
			}

			// try the next variant
			if (localePath.equals("")) //$NON-NLS-1$
				localePath = null;
			else {
				int ix = localePath.lastIndexOf(File.separator);
				if (ix == -1)
					localePath = ""; //$NON-NLS-1$
				else
					localePath = localePath.substring(0, ix);
			}
		}

		// sorry, could not find splash image
		return null;
	}
	/*
	 * resolve platform:/base/ URLs
	 */
	private String resolve(String urlString) {
		// handle the case where people mistakenly spec a refererence: url.
		if (urlString.startsWith("reference:")) {
			urlString = urlString.substring(10);
			System.getProperties().put(PROP_FRAMEWORK, urlString);
		}
		if (urlString.startsWith(PLATFORM_URL)) {
			String path = urlString.substring(PLATFORM_URL.length());
			return getInstallLocation() + path;
		} else
			return urlString;
	}

	/*
	 * Entry point for logging.
	 */
	private synchronized void log(Object obj) {
		if (obj == null)
			return;
		try {
			openLogFile();
			try {
				if (newSession) {
					log.write(SESSION);
					log.write(' ');
					for (int i = SESSION.length(); i < 78; i++)
						log.write('-');
					log.newLine();
					newSession = false;
				}
				write(obj);
			} finally {
				if (logFile == null) {
					if (log != null)
						log.flush();
				} else
					closeLogFile();
			}
		} catch (Exception e) {
			System.err.println("An exception occurred while writing to the platform log:"); //$NON-NLS-1$
			e.printStackTrace(System.err);
			System.err.println("Logging to the console instead."); //$NON-NLS-1$
			//we failed to write, so dump log entry to console instead
			try {
				log = logForStream(System.err);
				write(obj);
				log.flush();
			} catch (Exception e2) {
				System.err.println("An exception occurred while logging to the console:"); //$NON-NLS-1$
				e2.printStackTrace(System.err);
			}
		} finally {
			log = null;
		}
	}
	/*
	 * This should only be called from #log()
	 */
	private void write(Object obj) throws IOException {
		if (obj == null)
			return;
		if (obj instanceof Throwable) {
			log.write(STACK);
			log.newLine();
			((Throwable) obj).printStackTrace(new PrintWriter(log));
		} else {
			log.write(ENTRY);
			log.write(' ');
			log.write(PLUGIN_ID);
			log.write(' ');
			log.write(String.valueOf(ERROR));
			log.write(' ');
			log.write(String.valueOf(0));
			log.write(' ');
			try {
				DateFormat formatter = new SimpleDateFormat("MMM dd, yyyy kk:mm:ss.SS"); //$NON-NLS-1$
				log.write(formatter.format(new Date()));
			} catch (Exception e) {
				// continue if we can't write out the date
				log.write(Long.toString(System.currentTimeMillis()));
			}
			log.newLine();
			log.write(MESSAGE);
			log.write(' ');
			log.write(String.valueOf(obj));
		}
		log.newLine();
	}
	private void computeLogFileLocation() {
		if (logFile != null)
			return;
		// compute the base location and then append the name of the log file
		URL base = buildURL(System.getProperty(PROP_CONFIG_AREA));
		if (base == null)
			return;
		logFile = new File(base.getPath(), ".log"); //$NON-NLS-1$
		logFile.getParentFile().mkdirs();
	}
	
	/**
	 * Converts an ASCII character representing a hexadecimal
	 * value into its integer equivalent.
	 */
	private int hexToByte(byte b) {
		switch (b) {
			case '0' :
				return 0;
			case '1' :
				return 1;
			case '2' :
				return 2;
			case '3' :
				return 3;
			case '4' :
				return 4;
			case '5' :
				return 5;
			case '6' :
				return 6;
			case '7' :
				return 7;
			case '8' :
				return 8;
			case '9' :
				return 9;
			case 'A' :
			case 'a' :
				return 10;
			case 'B' :
			case 'b' :
				return 11;
			case 'C' :
			case 'c' :
				return 12;
			case 'D' :
			case 'd' :
				return 13;
			case 'E' :
			case 'e' :
				return 14;
			case 'F' :
			case 'f' :
				return 15;
			default :
				throw new IllegalArgumentException("Switch error decoding URL"); //$NON-NLS-1$
		}
	}

	private void openLogFile() throws IOException {
		computeLogFileLocation();
		try {
			log = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile.getAbsolutePath(), true), "UTF-8")); //$NON-NLS-1$
		} catch (IOException e) {
			logFile = null;
			throw e;
		}
	}
	private BufferedWriter logForStream(OutputStream output) {
		try {
			return new BufferedWriter(new OutputStreamWriter(output, "UTF-8")); //$NON-NLS-1$
		} catch (UnsupportedEncodingException e) {
			return new BufferedWriter(new OutputStreamWriter(output));
		}
	}
	private void closeLogFile() throws IOException {
		try {
			if (log != null) {
				log.flush();
				log.close();
			}
		} finally {
			log = null;
		}
	}
	
	private void mergeProperties(Properties destination, Properties source) {
		if (destination == null || source == null)
			return;
		for (Enumeration e = source.keys(); e.hasMoreElements();) {
			String key = (String) e.nextElement();
			if (!key.equals(PROP_EOF)) {
				String value = source.getProperty(key);
				if (destination.getProperty(key) == null)
					destination.put(key, value);
			}
		}
	}

	
	

	// TODO the following methods will be deleted before 3.0 ships
	/**
	 * Return a boolean value indicating whether or not the version of the JVM is
	 * deemed to be compatible with Eclipse.
	 * @deprecated move this check to the application
	 */
	private boolean isCompatible() {
		try {
			String vmVersionString = System.getProperty("java.version"); //$NON-NLS-1$
			Identifier minimum = new Identifier(1, 3, 0);
			Identifier version = new Identifier(vmVersionString);
			return version.isGreaterEqualTo(minimum);
		} catch (SecurityException e) {
			// If the security manager won't allow us to get the system property, continue for
			// now and let things fail later on their own if necessary.
			return true;
		} catch (NumberFormatException e) {
			// If the version string was in a format that we don't understand, continue and
			// let things fail later on their own if necessary.
			return true;
		}
	}

	private File computeLockFileLocation() {
		// compute the base location and then append the name of the lock file
		File base = computeMetadataLocation();
		File result = new File(base, ".lock"); //$NON-NLS-1$
		result.getParentFile().mkdirs();
		return result;
	}
	/**
	 * Return a boolean value indicating whether or not the platform is already
	 * running in this workspace.
	 * @deprecated  The application is now responsible for checking the lock
	 */
	private boolean isAlreadyRunning() {
		if (System.getProperty("org.eclipse.core.runtime.ignoreLockFile") != null) //$NON-NLS-1$
			return false;
		// Calculate the location of the lock file
		File lockFile = computeLockFileLocation();
		FileOutputStream stream = null;
		try {
			stream = new FileOutputStream(lockFile, true);
			FileLock lock = stream.getChannel().tryLock();
			return lock == null;
		} catch (IOException e) {
			return false;
		} finally {
			if (stream != null)
				try {
					stream.close();
				} catch (IOException e) {
					// ignore
				}
		}
	}

	/*
	 * Returns the location of the metadata.  This is a bit of a hack since it can be called 
	 * very early in the case of an error (to get the log location).  So just iterate over the 
	 * command line and look for -data and compute if none is found.
	 */
	private File computeMetadataLocation() {
		File result = null;
		// check to see if the user specified a workspace location in the command-line args
		for (int i = 0; arguments != null && result == null && i < arguments.length; i++) {
			if (arguments[i].equalsIgnoreCase(DATA)) {
				// found the -data command line argument so the next argument should be the
				// workspace location. Ensure that we have another arg to check
				if (i + 1 < arguments.length)
					result = new File(arguments[i + 1]);
			}
		}
		// otherwise use the default location
		if (result == null)
			result = new File(System.getProperty("user.dir"), "workspace"); //$NON-NLS-1$ //$NON-NLS-2$

		// append the .metadata directory to the path
		return new File(result, ".metadata"); //$NON-NLS-1$
	}

}
