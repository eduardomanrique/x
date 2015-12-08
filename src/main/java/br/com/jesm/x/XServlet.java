package br.com.jesm.x;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.persistence.Entity;
import javax.script.ScriptException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.classic.Session;

import com.google.gson.Gson;

import br.com.jesm.x.dao.XDAO;
import br.com.jesm.x.model.XUser;
import br.com.jesm.x.model.internal.XSchedule;

public class XServlet extends HttpServlet {

	private static final long serialVersionUID = 6340280941961523359L;

	private static final Logger logger = Logger.getLogger(XServlet.class);

	protected static long applicationTimestamp;

	private Map<String, Object> instancesMap;

	private Map<String, String> metaMap;

	private Map<String, Map<String, Method>> GETMethodsMap;

	private Map<String, Map<String, Method>> POSTMethodsMap;

	private Map<String, Map<String, Object>> authPropertiesMap;

	private Map<String, ResInfo> resourceInfoMap = new HashMap<String, XServlet.ResInfo>();

	private List<Object[]> scheduledObjects = new ArrayList<Object[]>();;

	private Map<String, byte[]> pages = new HashMap<String, byte[]>();

	private Map<String, Boolean> dirs = new HashMap<String, Boolean>();

	private Map<String, Object[]> urlMethods;

	private String allMetaClasses;

	private String scripts;

	private int maxUploadSize = 10000000;

	private SessionFactory sessions;

	private String templateName;

	private String welcomePage;

	private byte[] customLoader;

	private String masterRoleName;

	private String esprima;

	private Properties properties;

	private String masterUser;

	private String masterPassword;

	private XAppLifecycle lifecicle;

	private XRobotThread robot;

	private String remoteScripts;

	@Override
	public void init(ServletConfig config) throws ServletException {
		try {

			logger.info("Initializing XServlet..");
			properties = new Properties();
			properties.load(config.getServletContext().getResourceAsStream("/WEB-INF/x.properties"));
			load(config.getServletContext());
			XLabels.start(config.getServletContext());

			if (properties.get("lifecycle.class") != null) {
				@SuppressWarnings("unchecked")
				Class<? extends XAppLifecycle> cl = (Class<? extends XAppLifecycle>) Class
						.forName((String) properties.get("lifecycle.class"));
				lifecicle = (XAppLifecycle) cl.newInstance();
				configFields(cl, lifecicle);
				lifecicle.onInit();
			}

			logger.debug("Initializing Hibernate Session, data source: " + properties.getProperty("data.source"));
			String jndiDataSource = properties.getProperty("data.source");
			if (jndiDataSource != null) {

				// InitialContext context = new InitialContext();
				// dataSource = (DataSource) context.lookup("java:comp/env"
				// + (jndiDataSource.charAt(0) == '/' ? jndiDataSource : ("/" +
				// jndiDataSource)));

				Configuration configuration = new Configuration();

				configuration.setProperty("hibernate.connection.datasource", jndiDataSource);
				configuration.setProperty("hibernate.hbm2ddl.auto", "update");
				if (XContext.isDevMode()) {
					configuration.setProperty("show_sql", "true");
				}
				configuration.addAnnotatedClass(XSchedule.class);
				String[] packages = properties.getProperty("entity.packages").split(",");
				for (String packageName : packages) {
					List<Class<?>> classes = XClassFinder.find(packageName, Entity.class);
					for (Class<?> clazz : classes) {
						configuration.addAnnotatedClass(clazz);
					}
				}
				configuration.configure();
				logger.debug("Building hibernate session...");
				sessions = configuration.buildSessionFactory();
			}
			robot = new XRobotThread(scheduledObjects, sessions);
			robot.start();
			super.init(config);
			if (masterUser != null) {
				XContext.setInUserContext(false);
				Session session = sessions.openSession();
				XContext.setPersistenceSession(session);
				XUserService userService = (XUserService) instancesMap.get("XUserService");
				if (!userService.exists(masterUser)) {
					Transaction tx = session.beginTransaction();
					XUser user = new XUser();
					user.setLogin(masterUser);
					user.setRole(masterRoleName);
					user.setPassword(masterPassword);
					userService.create(user);
					tx.commit();
				}
				session.close();
			}
			logger.info("XServlet Initialized");
		} catch (Exception e) {
			String msg = "Error x classes.";
			logger.error(msg, e);
			throw new RuntimeException(msg, e);
		}
	}

	private void load(ServletContext ctx)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
		instancesMap = new HashMap<String, Object>();
		metaMap = new HashMap<String, String>();
		GETMethodsMap = new HashMap<String, Map<String, Method>>();
		POSTMethodsMap = new HashMap<String, Map<String, Method>>();
		authPropertiesMap = new HashMap<String, Map<String, Object>>();
		synchronized (scheduledObjects) {
			scheduledObjects.clear();
			urlMethods = new HashMap<String, Object[]>();
			String maxUploadSizeStr = properties.getProperty("max.upload.size");
			if (maxUploadSizeStr != null) {
				maxUploadSize = Integer.parseInt(maxUploadSizeStr);
			}
			logger.debug("max.upload.size=" + maxUploadSizeStr);
			Set<Class<? extends Object>> allClasses = new HashSet<Class<? extends Object>>();
			if (properties.getProperty("service.packages") != null) {
				logger.debug("service.packages " + properties.getProperty("service.packages"));
				String[] scanPackages = properties.getProperty("service.packages").split(",");
				for (String packageName : scanPackages) {
					logger.debug("Scanning service package " + scanPackages);
					allClasses.addAll(XClassFinder.find(packageName, XObject.class));
				}
			}
			if (properties.getProperty("objects") != null) {
				String[] objects = properties.getProperty("objects").split(",");
				for (String obj : objects) {
					allClasses.add(Class.forName(obj));
				}
			}
			StringBuilder allMeta = new StringBuilder("{");
			logger.debug("Initializing XObjects...");
			instantiateXObject(allMeta, XUserService.class);
			for (Class<? extends Object> cl : allClasses) {
				logger.debug("Initializing XObject " + cl.getName());
				instantiateXObject(allMeta, cl);
			}
			logger.debug("Initializing XObject's DAO variables...");
			for (Object xobj : instancesMap.values()) {
				checkXRef(xobj);
			}
			allMeta.append("}");
			allMetaClasses = allMeta.toString();

			String devmode = System.getenv("XDEVMODE");
			if (devmode == null) {
				devmode = properties.getProperty("devmode");
			}

			XContext.setDevMode(devmode != null && devmode.equalsIgnoreCase("true"));

			startScript(ctx);
			esprima = getResource("/esprima.js");

			logger.debug("Initializing Loader..");
			customLoader = XTemplates.loaderImg(properties.getProperty("loader.img.path"), ctx);

			templateName = properties.getProperty("page.template");

			welcomePage = properties.getProperty("welcome.page");

			masterRoleName = properties.getProperty("master.role");

			masterUser = properties.getProperty("master.user");

			masterPassword = properties.getProperty("master.password");
		}

	}

	private String getScriptModule(String name) throws IOException {
		return "var " + name
				+ " = addModule(function(){ \n\t\t\tvar thisModule = this; \n\t\t\tfunction _expose(fn, name){xexpose(thisModule, fn, false, name);};"
				+ "\n\t\t\tfunction _external(fn, name){xexpose(thisModule, fn, true, name);};\n"
				+ getResource("/x/" + name + ".js") + "\n\t\t});";
	}

	private String replaceString(String str, String patternReplace, String newStr) {
		int index = str.indexOf(patternReplace);
		return str.substring(0, index) + newStr + str.substring(index + patternReplace.length());
	}

	private String replaceAllStrings(String str, String patternReplace, String newStr) {
		while (str.indexOf(patternReplace) >= 0) {
			str = replaceString(str, patternReplace, newStr);
		}
		return str;
	}

	private void startScript(ServletContext ctx) throws IOException {
		String mainScript = getResource("/x/x.js");

		StringBuilder modules = new StringBuilder();
		modules.append(getScriptModule("xcomponents"));
		modules.append(getScriptModule("xdefaultservices"));
		modules.append(getScriptModule("xdom"));
		modules.append(getScriptModule("xevents"));
		modules.append(getScriptModule("xinputs"));
		modules.append(getScriptModule("xlog"));
		modules.append(getScriptModule("xmask"));
		modules.append(getScriptModule("xobj"));
		modules.append(getScriptModule("xremote"));
		modules.append(getScriptModule("xutil"));
		modules.append(getScriptModule("xvisual"));

		applicationTimestamp = System.currentTimeMillis();

		mainScript = replaceString(mainScript, "%xmodulescripts%", modules.toString());

		String defaultDateFormat = properties.getProperty("defaultdateformat");
		defaultDateFormat = defaultDateFormat == null ? "" : defaultDateFormat;
		mainScript = replaceAllStrings(mainScript, "%defaultdateformat%", defaultDateFormat);

		logger.debug("Initializing js scripts...");
		mainScript = mainScript.replaceAll("%meta%", allMetaClasses).replaceAll("%ctx%", ctx.getContextPath());

		String currencyFormatter = "";
		if (properties.getProperty("currency.decimal.separator") != null) {
			currencyFormatter += "\n_default_decimal_separator = '"
					+ properties.getProperty("currency.decimal.separator") + "';\n";
		}
		if (properties.getProperty("currency.thousand.separator") != null) {
			currencyFormatter += "\n_default_thousand_separator = '"
					+ properties.getProperty("currency.thousand.separator") + "';\n";
		}
		mainScript = replaceString(mainScript, "%currency_formatter%", currencyFormatter);

		mainScript = replaceString(mainScript, "%xdevmode%", String.valueOf(XContext.isDevMode()));

		logger.debug("Applying templates...");
		mainScript = replaceString(mainScript, "%modaltemplate%",
				XTemplates.modalTemplate(properties.getProperty("modal.template"), ctx));
		String jsComponent = XComponents.js(ctx);

		logger.debug("Initializing XComponents...");
		mainScript = replaceString(mainScript, "%xcomponents%", jsComponent);
		String debugFlags = properties.getProperty("js.debug.flags");
		if (debugFlags != null) {
			String[] splitDebugFlags = debugFlags.split(",");
			debugFlags = "";
			for (String debug : splitDebugFlags) {
				debugFlags += "'" + debug.trim() + "',";
			}
			mainScript = replaceString(mainScript, "%debug_flags%", debugFlags);
		} else {
			mainScript = replaceString(mainScript, "%debug_flags%", "");
		}

		logger.debug("Initializing js scripts template...");
		try {
			mainScript += XTemplates.templateScripts(properties.getProperty("golbal.script"), ctx);
		} catch (ScriptException e) {
			logger.fatal("Error reading global script", e);
			System.exit(1);
		}

		// TODO ver https
		this.remoteScripts = mainScript.replaceAll("%sitedomain%", "http://" + properties.getProperty("sitedomain"))
				.replaceAll("%is_remote%", "true");

		mainScript = mainScript.replaceAll("%sitedomain%", "").replaceAll("%is_remote%", "false");

		if (properties.getProperty("load.jquery") != null
				&& properties.getProperty("load.jquery").equalsIgnoreCase("true")) {
			scripts = getResource("/thirdparty/jquery-1.6.1.min.js")
					+ getResource("/thirdparty/jquery.maskedinput.min.js")
					+ getResource("/thirdparty/jquery.priceformat.1.7.min.js") + mainScript;
		} else {
			scripts = mainScript;
		}
	}

	private void checkXRef(Object xobj) {
		for (Field f : xobj.getClass().getFields()) {
			checkXField(xobj, f);
		}
		for (Field f : xobj.getClass().getDeclaredFields()) {
			checkXField(xobj, f);
		}
	}

	private void checkXField(Object xobj, Field f) {
		XObject annot = f.getType().getAnnotation(XObject.class);
		if (annot != null) {
			try {
				f.setAccessible(true);
				f.set(xobj, instancesMap.get(annot.alias()));
			} catch (Exception e) {
				String msg = "Error initiating service refs in instance.";
				logger.error(msg, e);
				throw new RuntimeException(msg, e);
			}
		}
	}

	private void instantiateXObject(StringBuilder allMeta, Class<? extends Object> cl)
			throws InstantiationException, IllegalAccessException {
		XObject annot = cl.getAnnotation(XObject.class);
		String alias = annot.alias();
		configMeta(alias, cl, allMeta);
		Object instance = cl.newInstance();
		configUrls(instance);
		configFields(cl, instance);
		checkRobot(instance, cl);
		instancesMap.put(alias, instance);
	}

	private void configFields(Class<? extends Object> cl, Object instance) {
		for (Field field : cl.getFields()) {
			checkField(field, instance);
		}
		for (Field field : cl.getDeclaredFields()) {
			checkField(field, instance);
		}
	}

	private void configUrls(Object instance) {
		for (Method m : instance.getClass().getMethods()) {
			XMethod annot = m.getAnnotation(XMethod.class);
			if (annot != null && !annot.url().trim().equals("")) {
				if (m.getParameterTypes().length > 0) {
					throw new RuntimeException(
							"WEB Methods cannot have arguments. URL: " + annot.url() + ", Method: " + m);
				} else if (!m.getReturnType().equals(void.class)) {
					throw new RuntimeException("WEB Methods must return void. URL: " + annot.url() + ", Method: " + m);
				}
				urlMethods.put(annot.url(), new Object[] { instance, m });
			}
		}
	}

	private void checkField(Field field, Object instance) {
		if (XDAO.class.isAssignableFrom(field.getType())) {
			Type c = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
			@SuppressWarnings("all")
			XDAO<?> xdao = new XDAO((Class<?>) c);
			try {
				field.setAccessible(true);
				field.set(instance, xdao);
			} catch (Exception e) {
				String msg = "Error instantiating class XDAO<" + c + ">";
				logger.error(msg, e);
				throw new RuntimeException(msg, e);
			}
		}
	}

	private String getResource(String path) throws IOException {
		return XStreamUtil.inputStreamToString(XServlet.class.getResourceAsStream(path));
	}

	private void configMeta(String alias, Class<? extends Object> cl, StringBuilder allMeta) {
		Map<String, Object> meta = new HashMap<String, Object>();
		List<Map<String, String>> methodsList = new ArrayList<Map<String, String>>();
		Map<String, Method> GETMethods = new HashMap<String, Method>();
		Map<String, Method> POSTMethods = new HashMap<String, Method>();
		GETMethodsMap.put(alias, GETMethods);
		POSTMethodsMap.put(alias, POSTMethods);
		meta.put("methods", methodsList);
		for (Method m : cl.getMethods()) {
			XMethod annot = m.getAnnotation(XMethod.class);
			if (annot != null) {
				if (annot.url().trim().equals("")) {
					int cache = annot.cacheExpires();
					boolean isGET = (cache > 0 || annot.forceMethod().equals(XMethod.WEBMethod.GET)) && !annot.upload()
							&& !annot.transacted();
					Map<String, String> info = new HashMap<String, String>();
					methodsList.add(info);
					info.put("name", m.getName());
					if (isGET) {
						info.put("type", "GET");
						GETMethods.put(m.getName(), m);
					} else {
						info.put("type", "POST");
						POSTMethods.put(m.getName(), m);
					}
					info.put("nocache", Boolean.toString(cache == 0));
				}
			}
		}
		String json = new Gson().toJson(meta);
		metaMap.put(alias, json);
		allMeta.append(alias).append(":").append(json).append(",");
	}

	private void checkRobot(Object instance, Class<? extends Object> cl) {
		for (Method m : cl.getMethods()) {
			XRobot robot = m.getAnnotation(XRobot.class);
			if (robot != null) {
				if (m.getParameterTypes().length > 0) {
					throw new RuntimeException("XRobot methods must have no parameters! Method: " + m);
				}
				scheduledObjects.add(new Object[] { instance, m });
			}
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		updateContext(req, resp);
		String serv_path = req.getServletPath() + req.getPathInfo();
		String[] path = (serv_path).split("/");
		if (path.length > 1 && path[1].equals("xurl")) {
			try {
				executeWebMethod(serv_path.substring("/xurl".length()), req, resp);
			} catch (Exception e) {
				String msg = "Error invoking web method.";
				logger.error(msg, e);
				throw new ServletException(msg, e);
			}
		} else if (path.length > 1 && path[1].equals("x")) {
			if (path[2].equals("_reload")) {
				if (XContext.isDevMode()) {
					try {
						load(req.getSession().getServletContext());
					} catch (Exception e) {
						throw new ServletException("Error reloading context", e);
					}
				}
			} else if (path[2].equals("_status")) {
				printStatus(resp);
			} else if (path[2].equals("_x_ping")) {
				logger.debug("ping");
				resp.getOutputStream().write("''".getBytes());
			} else if (path[2].equals("no_authentication")) {
				error(resp, resp.getOutputStream(), HttpServletResponse.SC_FORBIDDEN);
			} else if (path[2].equals("loader.gif")) {
				XHttp.setCachedResponseHeader(604800);
				resp.setContentType("image/gif");
				resp.getOutputStream().write(customLoader);
			} else if (path[2].equals("__meta")) {
				String alias = path[3];
				printResponse(resp, metaMap.get(alias));
			} else if (path[2].equals("scripts") && path[3].equals("x.js")) {
				XHttp.setCachedResponseHeader(1296000);
				resp.setContentType("text/javascript");
				resp.setCharacterEncoding("utf-8");
				printResponse(resp, scripts);
			} else if (path[2].equals("scripts") && path[3].equals("x.remote.js")) {
				XHttp.setCachedResponseHeader(1296000);
				resp.setContentType("text/javascript");
				resp.setCharacterEncoding("utf-8");
				printResponse(resp, remoteScripts);
			} else if (path[2].equals("scripts") && path[3].equals("esprima.js")) {
				XHttp.setCachedResponseHeader(31296000);
				resp.setContentType("text/javascript");
				resp.setCharacterEncoding("utf-8");
				printResponse(resp, esprima);

			} else if (path[2].equals("scripts") && path[3].equals("xparams.js")) {
				xparameters(req, resp);
				resp.setContentType("text/javascript");
				resp.setCharacterEncoding("utf-8");
			} else {
				try {
					Object instance = instancesMap.get(path[2]);
					Method method = GETMethodsMap.get(path[2]).get(path[3]);
					if (method == null || instance == null) {
						throw new RuntimeException("Invalid method " + path[3] + " or alias " + path[2]);
					}
					invoke(req, resp, instance, method, false, false);
				} catch (Exception e) {

					if (e.getCause() instanceof XNotAuthException) {
						resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					} else {
						throw new ServletException(e);
					}
				}
			}
		} else {
			String pathInfo = req.getPathInfo();
			if ((pathInfo.equals("/") || pathInfo.equals("")) && !welcomePage.equals("") && !welcomePage.equals("/")) {
				resp.sendRedirect(req.getContextPath() + welcomePage);
				return;
			}
			OutputStream os = resp.getOutputStream();
			if (path.length > 1 && path[1].equals("res")) {
				setContentType(resp, pathInfo);
				XHttp.setCachedResponseHeader(1296000);
				byte[] page = getPage(pathInfo, null);
				if (page != null) {
					os.write(page);
				} else {
					error(resp, os, HttpServletResponse.SC_NOT_FOUND);
				}
			} else {
				sendToPage(req, resp, pathInfo, os);
			}
			os.flush();
		}
		clearContext();
	}

	private void sendToPage(HttpServletRequest req, HttpServletResponse resp, String path, OutputStream os)
			throws IOException {
		XUser user = (XUser) req.getSession().getAttribute("__x_user");
		ResInfo resInfo = getResourceInfo(path, req);
		if (resInfo.isDir) {
			if (user == null) {
				String realPath = resInfo.redirectRealPath;
				if (realPath != null && new File(realPath).exists()) {
					resp.sendRedirect(resInfo.redirect);
				} else {
					error(resp, os, HttpServletResponse.SC_FORBIDDEN);
				}
				return;
			} else {
				String realPath = resInfo.realPath;
				if (realPath == null || !new File(realPath).exists()) {
					resp.sendRedirect(resInfo.redirect);
					return;
				}
			}
		}
		if (resInfo.isController) {
			resp.setContentType("text/javascript");
			resp.setCharacterEncoding("utf-8");
		}
		if (!resInfo.needsLogin || user != null) {
			if (resInfo.needsLogin && !checkAuthorization(resp, user, resInfo)) {
				error(resp, os, HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}
			boolean popup = "true".equals(req.getParameter("_xpopup"));
			boolean importScript = "true".equals(req.getParameter("_x_import"));
			if (popup || importScript) {
				resp.setContentType("text/plain");
				resp.setCharacterEncoding("utf-8");
			}
			String keyPathInfo = resInfo.pathInfo + (popup ? "_xpopup" : "");
			byte[] page = pages.get(keyPathInfo);
			XHttp.setCachedResponseHeader(1296000);
			if (page == null) {
				boolean x = resInfo.pathInfo.indexOf(".") < 0;
				String realPath = resInfo.pathInfo;
				if (x) {
					resp.setContentType("text/html");
					resp.setCharacterEncoding("utf-8");
					if (popup) {
						realPath = resInfo.pathInfo + ".modal.htmx";
					} else {
						realPath = resInfo.pathInfo + ".htmx";
					}
				}
				page = getPage("/pages" + realPath, resInfo.isController ? "/empty_file" : null);
				if (page != null) {
					String strResponse = new String(page);
					if (x) {
						strResponse = XTemplates.applyTemplate(strResponse, templateName, this.getServletContext(),
								resInfo.isDir);
						strResponse = XComponents.prepareComponents(strResponse,
								this.getServletContext().getContextPath(), properties, popup, resInfo.pathInfo);

					}
					strResponse = XTemplates.replaceVars(strResponse, this.getServletContext());
					if (resInfo.isController) {
						try {
							strResponse = XJS.instrumentController(strResponse, resInfo.jsName, resInfo.resource);
						} catch (ScriptException e) {
							String msg = "Error in script: " + realPath;
							logger.error(msg, e);
							throw new RuntimeException(msg, e);
						}
					} else {
						strResponse = buildXObjects(strResponse);
					}
					page = strResponse.getBytes();
					if (!XContext.isDevMode()) {
						pages.put(keyPathInfo, page);
					}
				} else {
					error(resp, os, HttpServletResponse.SC_NOT_FOUND);
				}
			}
			if (page != null) {
				os.write(page);
			}

		} else {
			if (resInfo.pathInfo.endsWith("/index")) {
				String newPath = resInfo.pathInfo.substring(0, resInfo.pathInfo.lastIndexOf("/index")) + "/_index";
				String realPath = this.getServletContext().getRealPath("/pages" + newPath + ".htmx");
				if (realPath != null && new File(realPath).exists()) {
					resp.sendRedirect(newPath);
					return;
				}
			}
			error(resp, os, HttpServletResponse.SC_FORBIDDEN);
		}
	}

	private boolean checkAuthorization(HttpServletResponse resp, XUser user, ResInfo resInfo) throws IOException {
		Map<String, Object> authProperties = resInfo.authProperties;
		boolean allowed = true;
		if (authProperties != null) {
			allowed = false;
			String[] roles = (String[]) authProperties.get("roles");
			if (roles != null && user.getRole() != null && Arrays.binarySearch(roles, user.getRole()) >= 0) {
				allowed = true;
			}
			if (!allowed) {
				List<String> functions = user.getAvailableFunctions();
				if (functions != null && authProperties.get("function") != null
						&& functions.contains(authProperties.get("function"))) {
					allowed = true;
				}
			}
		}
		return allowed;
	}

	private ResInfo getResourceInfo(String pathInfo, HttpServletRequest req) throws IOException {
		ResInfo result = resourceInfoMap.get(pathInfo);
		if (result == null) {
			String key = pathInfo;
			result = new ResInfo();
			result.isDir = isDir(pathInfo);
			String context = req.getContextPath() != null && !req.getContextPath().trim().equals("")
					? req.getContextPath() : "";

			if (result.isDir) {
				if (!pathInfo.endsWith("/")) {
					pathInfo += "/";
				}
				result.redirect = context + pathInfo + "_index";
				result.redirectRealPath = this.getServletContext().getRealPath("/pages" + pathInfo + "_index.htmx");
				pathInfo += "index";
			}
			result.isController = "true".equals(req.getParameter("_xcontroller"));
			if (result.isController) {
				result.jsName = pathInfo;
				result.resource = key.substring(0, key.length() - 3);
				String noExt = pathInfo.substring(0, pathInfo.length() - 3);
				if (result.isController && isDir(noExt)) {
					pathInfo = noExt + "/index.js";
				}
				result.realPath = this.getServletContext().getRealPath("/pages" + pathInfo + ".js");
			} else {
				result.realPath = this.getServletContext().getRealPath("/pages" + pathInfo + ".htmx");
			}
			result.pathInfo = pathInfo;
			int lastIndex = pathInfo.charAt(pathInfo.length() - 1) == '/' ? pathInfo.lastIndexOf("/", 1)
					: pathInfo.lastIndexOf("/");
			result.authProperties = getAuthProperties(result.pathInfo);
			if (result.authProperties != null && result.authProperties.containsKey("authentication")) {
				result.needsLogin = (Boolean) result.authProperties.get("authentication");
			} else {
				result.needsLogin = pathInfo.length() <= 1 || pathInfo.charAt(lastIndex + 1) != '_';
			}
			resourceInfoMap.put(key, result);
		}
		return result;
	}

	private static class ResInfo {
		private boolean isDir;
		private boolean needsLogin;
		private String pathInfo;
		private boolean isController;
		private String jsName;
		private String resource;
		private String redirect;
		private String redirectRealPath;
		private String realPath;
		private Map<String, Object> authProperties;
	}

	private boolean isDir(String pathInfo) {
		Boolean isDir = dirs.get(pathInfo);
		if (isDir != null) {
			return isDir;
		} else {
			isDir = false;
			if (pathInfo.indexOf('.') < 0) {
				String diskPath = this.getServletContext().getRealPath("/pages" + pathInfo);
				if (diskPath != null) {
					isDir = new File(diskPath).isDirectory();
				}
			}
			dirs.put(pathInfo, isDir);
			return isDir;
		}
	}

	private void xparameters(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		XHttp.setAvoidCache();
		String referer = req.getHeader("Referer");
		Map<String, String> parameters = getParametersFromReferer(referer);
		resp.setContentType("text/javascript");
		resp.setCharacterEncoding("utf-8");

		StringBuilder scripts = new StringBuilder(
				"window['_x_application_timestamp_'] = " + XServlet.applicationTimestamp)
						.append(";window['xuser'] = null;");
		if (req.getSession().getAttribute("__x_user") != null) {
			scripts.append("window['xuser'] = ").append(XJson.toJson(req.getSession().getAttribute("__x_user")))
					.append(";");
		} else {
			String ref = parameters.get("_xref");
			if (ref != null) {
				ResInfo resInfo = getResourceInfo(ref, req);
				if (ref != null && resInfo.jsName.charAt(resInfo.jsName.lastIndexOf("/") + 1) != '_') {
					scripts.append("location.reload();");
				}
			}
		}

		String xparameter = parameters.get("_xjsonxparam");
		scripts.append("window['_x_parameters'] = ");
		if (xparameter == null) {
			scripts.append("{}");
		} else {
			scripts.append(xparameter);
		}
		scripts.append(";");

		for (Map.Entry<String, String> entry : parameters.entrySet()) {
			if (!entry.getKey().equals("_xjsonxparam") && !entry.getKey().equals("_xref")) {
				scripts.append("window['_x_parameters']['").append(entry.getKey()).append("'] = '")
						.append(entry.getValue()).append("';");
			}
		}
		printResponse(resp, scripts.toString());
	}

	private Map<String, String> getParametersFromReferer(String referer) throws UnsupportedEncodingException {
		int index = referer.indexOf('?');
		Map<String, String> result = new HashMap<String, String>();
		if (index >= 0) {
			String[] parameters = referer.substring(index + 1).split("&");
			for (String param : parameters) {
				String[] paramSplitted = param.split("=");
				if (!paramSplitted[0].trim().equals("")) {
					result.put(paramSplitted[0],
							paramSplitted.length > 1 ? URLDecoder.decode(paramSplitted[1], "utf8") : "");
				}
			}
		}
		return result;
	}

	private void executeWebMethod(String url, HttpServletRequest req, HttpServletResponse resp) throws Exception {
		Object[] objects = urlMethods.get(url);
		String tempUrl = url;
		while (objects == null && tempUrl.length() > 0 && tempUrl.indexOf('/') >= 0) {
			tempUrl = tempUrl.substring(tempUrl.lastIndexOf('/')) + "/*";
			objects = urlMethods.get(tempUrl);
			if (objects != null) {
				urlMethods.put(url, objects);
			}
		}
		if (objects == null) {
			resp.sendError(HttpServletResponse.SC_NOT_FOUND);
		} else {
			invoke(req, resp, objects[0], (Method) objects[1], false, true);
		}
	}

	private void printStatus(HttpServletResponse resp) throws IOException {
		PrintWriter w = resp.getWriter();
		w.print("<html><head><title>status</title></head><body><h1>Status</h1><br>Qtd Instances: ");
		w.print(instancesMap != null ? instancesMap.size() : "0");
		w.print("<br>DevMode: ");
		w.print(XContext.isDevMode());
		w.print("<br></body></html>");
	}

	private String buildXObjects(String strResponse) {
		int from = 0;
		while (true) {
			int indi = strResponse.indexOf("{{", from);
			if (indi < 0)
				break;
			int indf = strResponse.indexOf("}}", indi);
			String exp = strResponse.substring(indi + 2, indf);
			if (exp.indexOf("\n") < 0) {
				String xobj = "<xobject xvar=\"" + exp + "\"></xobject>";
				strResponse = strResponse.substring(0, indi) + xobj + strResponse.substring(indf + 2);
				indf = indi + xobj.length();
			}
			from = indf;
		}
		return strResponse;
	}

	private Map<String, Object> getAuthProperties(String pathInfo) throws IOException {
		Map<String, Object> result = authPropertiesMap.get(pathInfo);
		if (result == null) {
			int dotIndex = pathInfo.lastIndexOf('.');
			int barIndex = pathInfo.lastIndexOf('/');
			String propPath;
			if (dotIndex > barIndex) {
				propPath = pathInfo.substring(0, dotIndex);
			} else {
				propPath = pathInfo;
			}
			byte[] bytes = getPage("/pages" + propPath + (propPath.lastIndexOf('/') == 0 ? "/index" : "") + ".auth",
					null);
			if (bytes == null) {
				bytes = getPage("/pages" + propPath.substring(0, propPath.lastIndexOf('/')) + "/auth", null);
			}
			if (bytes != null) {
				result = new HashMap<String, Object>();
				StringReader reader = new StringReader(new String(bytes));
				Properties p = new Properties();
				p.load(reader);
				String roles = (String) p.get("roles");
				if (roles != null) {
					String[] rolesArray = roles.split(",");
					for (int i = 0; i < rolesArray.length; i++) {
						rolesArray[i] = rolesArray[i].trim();
					}
					result.put("roles", rolesArray);
				}
				String function = (String) p.get("function");
				if (function != null) {
					result.put("function", function);
				}
				String authentication = (String) p.get("authentication");
				result.put("authentication", authentication != null && authentication.equalsIgnoreCase("TRUE"));
				authPropertiesMap.put(pathInfo, result.size() > 0 ? result : null);
			}
		}
		return result;
	}

	private void error(HttpServletResponse resp, OutputStream os, int errorCode) throws IOException {
		resp.setStatus(errorCode);
		os.write(getPage("error-pages/" + errorCode + ".html", "/errorpages/" + errorCode + ".html"));
	}

	private byte[] getPage(String path, String defaultPath) throws IOException {
		path = path.replaceAll("//", "/");
		String diskPath = this.getServletContext().getRealPath(path);
		if (diskPath != null) {
			InputStream is;
			File file = new File(diskPath);
			if (file.exists()) {
				is = new FileInputStream(file);
			} else {
				is = this.getServletContext().getResourceAsStream(path);
				if (is == null && defaultPath != null) {
					is = XServlet.class.getResourceAsStream(defaultPath);
				}
			}
			return is != null ? XStreamUtil.inputStreamToByteArray(is) : null;
		}
		return null;
	}

	private void setContentType(HttpServletResponse resp, String pathInfo) {
		if (pathInfo.endsWith(".js")) {
			resp.setContentType("text/javascript");
			resp.setCharacterEncoding("utf-8");
		} else if (pathInfo.endsWith(".html")) {
			resp.setContentType("text/html");
			resp.setCharacterEncoding("utf-8");
		} else if (pathInfo.endsWith(".css")) {
			resp.setContentType("text/css");
			resp.setCharacterEncoding("utf-8");
		} else if (pathInfo.endsWith(".jpg")) {
			resp.setContentType("text/JPEG");
		} else if (pathInfo.endsWith(".jpeg")) {
			resp.setContentType("text/JPEG");
		} else if (pathInfo.endsWith(".gif")) {
			resp.setContentType("text/gif");
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		updateContext(req, resp);
		boolean isUpload = ServletFileUpload.isMultipartContent(req);
		if (isUpload) {
			ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
			upload.setSizeMax(maxUploadSize);

			List<FileItem> fileItems;
			try {
				fileItems = upload.parseRequest(req);
			} catch (FileUploadException e) {
				String msg = "Upload error";
				logger.error(msg, e);
				throw new ServletException(msg, e);
			}
			for (FileItem fi : fileItems) {
				if (!fi.isFormField()) {
					XFile file = new XFile();
					file.setFieldName(fi.getFieldName());
					String fileName = fi.getName();
					if (fileName.lastIndexOf("\\") >= 0) {
						file.setFileName(fileName.substring(fileName.lastIndexOf("\\")));
					} else {
						file.setFileName(fileName.substring(fileName.lastIndexOf("\\") + 1));
					}
					file.setContentType(fi.getContentType());
					file.setInMemory(fi.isInMemory());
					file.setSizeInBytes(fi.getSize());

					file.setData(XStreamUtil.inputStreamToByteArray(fi.getInputStream()));
					XContext.setFileUpload(file);
				}
			}
		}
		String[] path = (req.getServletPath() + req.getPathInfo()).split("/");
		if (path[1].equals("x")) {
			try {
				Object instance = instancesMap.get(path[2]);
				Method method = POSTMethodsMap.get(path[2]).get(path[3]);
				if (method == null || instance == null) {
					throw new RuntimeException("Invalid method " + path[3] + " or alias " + path[2]);
				}
				invoke(req, resp, instance, method, isUpload, false);
			} catch (Exception e) {
				throw new ServletException(e);
			}
		} else {
			req.getRequestDispatcher(req.getPathInfo());
		}
		clearContext();
	}

	private void updateContext(HttpServletRequest req, HttpServletResponse resp) {
		XContext.setXRequest(req);
		XContext.setXResponse(resp);
		XContext.setXSession(req.getSession());
	}

	private void clearContext() {
		XContext.setFileUpload(null);
		XContext.setUseWebObjects(false);
		XContext.setXRequest(null);
		XContext.setXResponse(null);
		XContext.setXSession(null);
	}

	private void invoke(HttpServletRequest req, HttpServletResponse resp, Object instance, Method method,
			boolean isUpload, boolean isWebMethod) throws Exception, IOException {
		Session session = null;
		XContext.setUseWebObjects(
				method.getAnnotation(XMethod.class).useWebObjects() || method.getAnnotation(XMethod.class).upload()
						|| !method.getAnnotation(XMethod.class).url().trim().equals(""));
		Transaction tx = null;
		boolean commited = false;
		try {
			boolean loginReq = method.getAnnotation(XMethod.class).loginRequired();
			XUser user = XContext.getUser();
			if (user == null && loginReq) {
				throw new XNotAuthenticatedException("The method " + method.getName() + " requires a logged user");
			}
			boolean auth = !method.getAnnotation(XMethod.class).functionAllowed().trim().equals("")
					|| method.getAnnotation(XMethod.class).rolesAllowed().length > 0;
			if (loginReq && auth) {
				boolean allowed = false;
				String userRole = user.getRole();
				if (userRole != null && !userRole.trim().equals("")) {
					if (userRole.equals(this.masterRoleName)
							|| (method.getAnnotation(XMethod.class).rolesAllowed() != null
									&& method.getAnnotation(XMethod.class).rolesAllowed().length > 0
									&& Arrays.binarySearch(method.getAnnotation(XMethod.class).rolesAllowed(),
											userRole) >= 0)) {
						allowed = true;
					}
				}
				if (!allowed) {
					String functionAllowed = method.getAnnotation(XMethod.class).functionAllowed();
					if (functionAllowed != null && !functionAllowed.trim().equals("")
							&& user.getAvailableFunctions() != null && user.getAvailableFunctions().size() > 0
							&& user.getAvailableFunctions().contains(functionAllowed)) {
						allowed = true;
					}
				}
				if (!allowed) {
					throw new XNotAuthException("User not authorized to execute the operation");
				}
			}
			if (sessions != null) {
				try {
					session = sessions.openSession();
					XContext.setPersistenceSession(session);
					if (method.getAnnotation(XMethod.class).transacted()) {
						XContext.setInTransaction(true);
						tx = session.beginTransaction();
					}
				} catch (NullPointerException e) {
					throw new RuntimeException("Data source not configured", e);
				}
			}

			int cacheExpiers = method.getAnnotation(XMethod.class).cacheExpires();
			if (cacheExpiers > 0) {
				XHttp.setCachedResponseHeader(cacheExpiers);
			} else {
				XHttp.setAvoidCache();
			}
			int count = 0;
			String param;
			Object[] parameters = new Object[method.getParameterTypes().length];
			while ((param = req.getParameter("_param" + count)) != null) {
				Class<?> cl;
				cl = method.getParameterTypes()[count];
				parameters[count] = XJson.parse(param, cl);
				count++;
			}
			try {
				Object result = method.invoke(instance, parameters);

				if (!isWebMethod) {
					String resultStr = "{__response:true, result: " + XJson.toJson(result) + "}";
					if (isUpload) {
						resultStr = "<html><script>parent.X._uploadResponse(\"" + resultStr.replaceAll("\"", "\\\\\"")
								+ "\");</script></html>";
						resp.setContentType("text/html");
					} else {
						resp.setContentType("text/html");
					}
					resp.setCharacterEncoding("utf-8");
					printResponse(resp, resultStr);
				}
				if (tx != null) {
					tx.commit();
					commited = true;
				}
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			} catch (IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (InvocationTargetException e) {
				Throwable exc = e.getTargetException();
				logger.error("Error invoking method.", exc);
				String exceptionName = exc.getClass().getName();
				String message = exc.getMessage();
				String resultStr = "{__error:true, exceptionName:'" + exceptionName + "', message:'" + message + "'}";
				if (isUpload) {
					resultStr = "<html><script>parent.X._uploadResponse(\"" + resultStr.replaceAll("\"", "\\\\\"")
							+ "\");</script></html>";
				}
				resp.setContentType("text/html");
				resp.setCharacterEncoding("utf-8");
				printResponse(resp, resultStr);

			}
		} catch (XNotAuthenticatedException e) {
			String exceptionName = e.getClass().getName();
			String message = e.getMessage();
			String resultStr = "{__error:true, __not_authenticated: true, exceptionName:'" + exceptionName
					+ "', message:'" + message + "'}";
			if (isUpload) {
				resp.setContentType("application/json");
				resp.setCharacterEncoding("utf-8");
				resultStr = "<html><script>parent.X._uploadResponse(\"" + resultStr.replaceAll("\"", "\\\\\"")
						+ "\");</script></html>";
			} else {
				resp.setContentType("text/html");
				resp.setCharacterEncoding("utf-8");
			}
			printResponse(resp, resultStr);
		} catch (Throwable t) {
			throw new Exception("Unexpected error", t);
		} finally {
			if (session != null) {
				if (!commited && tx != null) {
					tx.rollback();
				}
				session.close();
			}
		}

	}

	private void printResponse(HttpServletResponse response, String str) throws IOException {
		try {
			PrintWriter writer = response.getWriter();
			writer.print(str);
		} catch (EOFException e) {
		}
	}
}
