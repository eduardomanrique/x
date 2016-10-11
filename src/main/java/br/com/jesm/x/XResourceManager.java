package br.com.jesm.x;

import br.com.jesm.x.parser.*;
import org.apache.commons.collections.functors.ExceptionClosure;
import org.apache.log4j.Logger;

import javax.script.ScriptException;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * Created by eduardo on 8/21/16.
 */
public enum XResourceManager {

    instance;

    private static final Logger logger = Logger.getLogger(XResourceManager.class);

    private Map<String, byte[]> pages = new HashMap<String, byte[]>();

    private ServletContext ctx;

    private Map<String, Resource> resourceInfoMap = new HashMap<String, Resource>();

    private Map<String, Boolean> dirs = new HashMap<String, Boolean>();

    private Map<String, HtmxInfo> htmxInfo = new HashMap<String, HtmxInfo>();

    private String defaultTemplateName;

    private Properties properties;

    private boolean isConfiguredToBeSpa;

    public void init(ServletContext ctx, Properties properties) throws IOException, XHTMLParsingException {
        this.ctx = ctx;
        this.properties = properties;
        //default is true
        isConfiguredToBeSpa = !("false".equalsIgnoreCase(properties.getProperty("spa")));
        if (logger.isDebugEnabled()) {
            logger.debug("SPA: " + isConfiguredToBeSpa);
        }

        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".htmx");
            }
        };
        defaultTemplateName = properties.getProperty("default.page.template");
        String basePath = ctx.getRealPath("/pages");
        List<File> htmxList = XFileUtil.instance.listFiles("/pages", filter, ctx);
        for (File htmxFile : htmxList) {
            if (htmxFile.getName().endsWith(".modal.htmx")) {
                continue;
            }
            HtmxInfo info = new HtmxInfo();
            String path = htmxFile.getAbsolutePath().substring(basePath.length(), htmxFile.getAbsolutePath().length() - ".htmx".length());
            boolean isIndex = false;
            info.setPath(path);
            XHTMLParser parser = new XHTMLParser();
            String content = XFileUtil.instance.readFile(htmxFile.getAbsolutePath());
            if (logger.isDebugEnabled()) {
                logger.debug("Getting info of htmx " + path);
            }
            boolean hasHtmlElement = false;
            try {
                hasHtmlElement = parser.hasHtmlElement(content);
            } catch (XHTMLParsingException e) {
                logger.warn("Path " + path + " has an invalid htmx file. ", e);
            } catch (Exception e) {
                logger.warn("Path " + path + " has an invalid htmx file. Error: " + e.getMessage());
            }
            if (!hasHtmlElement) {
                String templateName = XTemplates.getTemplateName(content, defaultTemplateName, ctx, isIndex);
                info.setTemplateName(templateName);
            }
            htmxInfo.put(info.getPath(), info);
        }
    }

    private Map<String, Set<String>> tempBoundVars = new HashMap<String, Set<String>>();
    private Map<String, Map<String, XModalBind>> tempBoundModals = new HashMap<String, Map<String, XModalBind>>();

    public byte[] getPageContents(Resource resInfo,
                                  boolean isModal, boolean isGlobal) throws IOException, XHTMLParsingException {

        byte[] page = pages.get(resInfo.getRealPath() + "|" + isGlobal);
        XHttp.setCachedResponseHeader(1296000);
        if (page == null) {
            synchronized (this) {
                // other thread may have filled it
                page = pages.get(resInfo.getPath());
                if (page == null) {
                    boolean isJs = resInfo instanceof XResourceManager.JsResource;
                    page = XFileUtil.instance.readFromDisk(resInfo.getRelativePath(), isJs ? "/empty_file" : null,
                            this.ctx);
                    if (page != null) {
                        String strResponse = new String(page);
                        String htmlStruct = null;
                        Set<String> boundVars = null;
                        Map<String, XModalBind> boundModals = null;
                        Map<String, List<Map<String, Object>>> components = new HashMap<String, List<Map<String, Object>>>();
                        if (!isJs || isModal) {
                            String html = strResponse;
                            if (isModal) {
                                //if is modal the page contains the script. So getting the html
                                html = new String(XFileUtil.instance.readFromDisk(((JsResource) resInfo).getHtmx().getRelativePath(),
                                        null, this.ctx));
                            }
                            XHTMLParser parser = new XHTMLParser();
                            XHTMLDocument doc = parser.parse(html);
                            //remove any xbody. It should be just in template
                            List<XElement> listXBody = doc.getElementsByName("xbody");
                            for (XElement e : listXBody) {
                                e.remove();
                            }
                            //get all the bound variables in the page
                            boundVars = parser.getBoundObjects();
                            //get all the bound modals in the page
                            boundModals = parser.getBoundModals();

                            boolean usesTemplate = false;

                            if (!isModal) {
                                //check the template
                                if (doc.getHtmlElement() == null) {
                                    usesTemplate = true;
                                    doc = checkTemplate(resInfo, isModal, boundVars, boundModals, html, doc);
                                }
                            }

                            String webctx = this.ctx.getContextPath();

                            //will put all the iterators here
                            List<List<Object>> iteratorList = new ArrayList<List<Object>>();

                            //place real html of components, prepare iterators and labels
                            XComponents.prepareHTML(doc, this.ctx.getContextPath(), properties,
                                    resInfo.getPath(), boundVars, boundModals, components, iteratorList, isModal);

                            if (!isModal) {
                                //prepare not in a modal like html, loader
                                prepareTopElements(doc, webctx);
                            }
                            //spa main window is the window tha loads just the template. It should not load any page previously
                            boolean isSpaMainWindow = isConfiguredToBeSpa && !isModal && usesTemplate;
                            // remove xbody element
                            prepareXBody(isModal, doc.getElementsByName("xbody"), isSpaMainWindow);

                            if (!isModal) {
                                Map<String, Object> jsonDynAtt = new HashMap<String, Object>();
                                Map<String, Map<String, Object>> jsonHiddenAtt = new HashMap<String, Map<String, Object>>();
                                Map<String, String> jsonComp = new HashMap<String, String>();

                                html = XTemplates.replaceVars(doc.getHTML(jsonDynAtt, jsonHiddenAtt, jsonComp),
                                        this.ctx);

                                StringBuilder postString = new StringBuilder();

                                postString.append("\n(function(){\n");
                                postString.append("\n		var X = new _XClass();");

                                //the main window should always register as it might have iterators, xscripts and dyn attribs
                                postString.append("\n		X._registerObjects(").append(XJson.toJson(jsonDynAtt))
                                        .append(",");
                                postString.append("\n			").append(XJson.toJson(jsonHiddenAtt)).append(",");
                                postString.append("\n			").append(XJson.toJson(iteratorList)).append(",");
                                postString.append("\n			").append(XJson.toJson(jsonComp)).append(",");
                                postString.append("\n			").append(XJson.toJson(components)).append(");");

                                if (!isSpaMainWindow) {
                                    postString.append("\n		X._getJS('" + resInfo.getPath() + ".js', null, function(){");
                                    postString.append("\n			console.log('X Loaded');");
                                    postString.append("\n		})");
                                } else {
                                    postString.append("\n         var xbody = document.getElementsByTagName('xbody')[0];");

                                    postString.append("\n         X$._xbodyNode = xbody;");
                                    postString.append("\n         X$._isSpa = true;");
                                    postString.append("\n         X$._xbodyNode.xsetModal = function(child){");
                                    postString.append("\n             X$._xbodyNode.appendChild(child);");
                                    postString.append("\n         };");

                                    postString.append("\n         var controller = new function(){var __xbinds__ = null; this._x_eval = function(f){return eval(f)};};");
                                    postString.append("\n         X._setEvalFn(controller._x_eval);");
                                    postString.append("\n         document.body.setAttribute('data-x_ctx', 'true');");
                                    postString.append("\n         X.setController(controller, function(){console.log('X started (spa)');});");
                                    postString.append("\n         X.setSpaModalNode(X$._xbodyNode);");
                                }
                                postString.append("\n})();");

                                html = html.replace("{xpostscript}", postString.toString());
                                tempBoundVars.put(resInfo.getPath() + ".js", boundVars);
                                tempBoundModals.put(resInfo.getPath() + ".js", boundModals);
                                strResponse = html;
                            } else {
                                htmlStruct = XTemplates.replaceVars(doc.toJson(), this.ctx);
                            }
                        }
                        if (isJs) {
                            if (!isModal) {
                                boundVars = tempBoundVars.remove(resInfo.getPath());
                                boundModals = tempBoundModals.remove(resInfo.getPath());
                            }
                            strResponse = XTemplates.replaceVars(strResponse, this.ctx);
                            try {
                                strResponse = XJS.instrumentController(strResponse, resInfo.getPath(),
                                        boundVars, boundModals, isModal, isGlobal, htmlStruct, XJson.toJson(components), (JsResource)resInfo, this.ctx);
                            } catch (ScriptException e) {
                                String msg = "Error in script: " + resInfo.getRealPath();
                                logger.error(msg, e);
                                throw new RuntimeException(msg, e);
                            }
                        }
                        page = strResponse.getBytes("UTF-8");
                        if (!XContext.isDevMode()) {
                            pages.put(resInfo.getRealPath(), page);
                        }
                    } else {
                        return null;
                    }
                }
            }
        }
        return page;
    }

    /**
     * Just main windows here. Modal shouldnt have xbody
     */
    private void prepareXBody(boolean isModal, List<XElement> listXBody, boolean isSpaMainWindow) {
        if (listXBody.isEmpty()) {
            //modal or no xbody
            return;
        }
        XElement xbody = listXBody.get(0);
        if (isSpaMainWindow) {
            xbody.removeAllChildren();
        } else {
            List<XNode> nodeList = xbody.getChildren();
            //get previous if exists or parent
            XNode prev = xbody.getPrevious();
            while (prev != null && prev instanceof XText && ((XText) prev).getText().trim().equals("")) {
                prev = prev.getPrevious();
            }
            XNode firstNode = nodeList.get(0);
            if (prev == null) {
                prev = xbody.getParent();
                ((XElement) prev).addChild(firstNode);
            } else {
                prev.addAfter(nodeList.get(0));
            }
            prev = firstNode;
            xbody.remove();
            if (!isSpaMainWindow) {
                for (int i = 1; i < nodeList.size(); i++) {
                    XNode n = nodeList.get(i);
                    prev.addAfter(n);
                    prev = n;
                }
            }
        }
    }

    private void prepareTopElements(XHTMLDocument doc, String webctx) {
        List<XElement> elementList = doc.findChildrenByName("html");
        if (elementList.isEmpty() || elementList.size() > 1) {
            throw new RuntimeException(
                    "Invalid page. There must be one (and only one) html element in a html page");
        }
        XElement htmlEl = elementList.get(0);
        prepareXScripts(doc, webctx, htmlEl);

        elementList = htmlEl.findChildrenByName("body");
        if (elementList.isEmpty() || elementList.size() > 1) {
            throw new RuntimeException(
                    "Invalid page. There must be one (and only one) body element in a html page");
        }
        XText newLine = new XText();
        newLine.setText("\n\n");
        elementList.get(0).addChild(newLine);

        XElement tempLoadDiv = new XElement("div", doc);
        tempLoadDiv.setAttribute("id", "_xtemploaddiv_");
        tempLoadDiv.setAttribute("style",
                "position:absolute;top:0px;left:0px;height: 100%;width:100%;z-index: 99999;background-color: white;");
        XElement imgLoad = new XElement("img", doc);
        imgLoad.setAttribute("style",
                "position:absolute;top:0;left:0;right:0;bottom:0;margin:auto;");
        imgLoad.setAttribute("height", "42");
        imgLoad.setAttribute("width", "42");
        imgLoad.setAttribute("src", webctx + "/x/loader.gif");
        tempLoadDiv.addChild(imgLoad);
        elementList.get(0).insertChild(tempLoadDiv, 0);

        // controller
        XElement script = new XElement("script", doc);
        script.setAttribute("type", "text/javascript");

        XText text = new XText();
        text.setText("{xpostscript}");
        script.addChild(text);
        elementList.get(0).addChild(script);
    }

    /**
     * Check if the page needs a template (if no html element is found). If needs gets the template and put into de doc
     */
    private XHTMLDocument checkTemplate(Resource resInfo, boolean isModal, Set<String> boundVars, Map<String, XModalBind> boundModals, String html, XHTMLDocument doc) throws IOException, XHTMLParsingException {
        // Page has no html element. Getting html
        // template...
        String templateName = XTemplates.getTemplateName(html, defaultTemplateName,
                this.ctx, resInfo.isImplicit());
        String htmlTemplatePage = XTemplates.getTemplate(templateName, resInfo.isImplicit());
        XHTMLParser templateParser = new XHTMLParser();
        XHTMLDocument docTemplate = templateParser.parse(htmlTemplatePage);
        List<XElement> xbody = docTemplate.getElementsByName("xbody");
        if (xbody.isEmpty()) {
            throw new RuntimeException(
                    "Invalid template " + templateName + ". There must be a {xbody}");
        } else if (xbody.size() > 1) {
            throw new RuntimeException(
                    "Invalid template " + templateName + ". There must be just one {xbody}");
        }
        for (XNode node : doc.getChildren()) {
            xbody.get(0).addChild(node);
        }
        docTemplate.getRequiredResourcesList().addAll(doc.getRequiredResourcesList());
        boundVars.addAll(templateParser.getBoundObjects());
        boundModals.putAll(templateParser.getBoundModals());
        doc = docTemplate;
        return doc;
    }

    private void prepareXScripts(XHTMLDocument doc, String webctx, XElement htmlEl) {
        List<XElement> elementList;
        elementList = htmlEl.findChildrenByName("head");
        XElement headEl;
        if (elementList.isEmpty()) {
            headEl = new XElement("head", doc);
            htmlEl.insertChild(headEl, 0);
        } else {
            headEl = elementList.get(0);
        }
        // params
        XElement script = new XElement("script", doc);
        script.setAttribute("type", "text/javascript");
        script.setAttribute("src", webctx + "/x/scripts/xparams.js");
        headEl.addChild(script);

        // cache timestamp
        script = new XElement("script", doc);
        headEl.addChild(script);

        // x.js
        script = new XElement("script", doc);
        script.setAttribute("type", "text/javascript");
        script.setAttribute("src", webctx + "/x/scripts/x.js");
        headEl.addChild(script);

        for (XElement e : doc.getRequiredResourcesList()) {
            String source = e.getAttribute("src").trim();
            source = source.startsWith("/") ? source : "/" + source;
            if (source.toLowerCase().endsWith(".js")) {
                script = new XElement("script", doc);
                script.setAttribute("type", "text/javascript");
                script.setAttribute("src", "{webctx}/res" + source);
                headEl.addChild(script);
            } else if (source.toLowerCase().endsWith("css") && headEl != null) {
                XElement linkEl = headEl.addElement("link");
                linkEl.setAttribute("href", "{webctx}/res" + source);
                if (e.getAttribute("rel") != null) {
                    linkEl.setAttribute("rel", e.getAttribute("rel"));
                }
                linkEl.setAttribute("rel", "stylesheet");
                if (e.getAttribute("media") != null) {
                    linkEl.setAttribute("media", e.getAttribute("media"));
                }
            }
        }
    }

    public Resource getResourceInfo(String path, String contextPath) throws IOException {
        Resource resInfo = resourceInfoMap.get(path);
        if (resInfo == null) {
            synchronized (this) {
                String context = contextPath != null && !contextPath.trim().equals("")
                        ? contextPath : "";

                String key = path;
                boolean isJS = path.endsWith(".js");
                String noExtensionPath = path.substring(context.length());
                if (isJS) {
                    noExtensionPath = path.substring(context.length(), path.lastIndexOf('.'));
                }
                boolean isDir = isDir(noExtensionPath);

                Resource result;
                if (isJS) {
                    result = new JsResource();
                    if (isDir) {
                        result.implicit = true;
                        result.loggedRedirect = noExtensionPath + "/index.js";
                        result.unloggedRedirect = noExtensionPath + "/_index.js";
                    } else {
                        result.relativePath = "/pages" + noExtensionPath + ".js";
                        HtmxResource htmx = (HtmxResource) getResourceInfo(path.substring(0, path.lastIndexOf('.')), contextPath);
                        ((JsResource) result).htmx = htmx;
                        result.realPath = this.ctx.getRealPath(result.relativePath);
                        boolean existsJs = exists(result);
                        if (!existsJs && htmx == null) {
                            return null;
                        }
                    }

                } else {
                    result = new HtmxResource();
                    if (isDir) {
                        result.implicit = true;
                        result.loggedRedirect = noExtensionPath + "/index";
                        result.unloggedRedirect = noExtensionPath + "/_index";
                    } else {
                        result.relativePath = "/pages" + noExtensionPath + ".htmx";
                        result.realPath = this.ctx.getRealPath(result.relativePath);
                        if (!exists(result)) {
                            result.relativePath = "/pages" + noExtensionPath + ".modal.htmx";
                            result.realPath = this.ctx.getRealPath("/pages" + noExtensionPath + ".modal.htmx");
                            if (!exists(result)) {
                                return null;
                            }
                            ((HtmxResource) result).modal = true;
                        }
                    }
                }
                result.path = path;

                if (!isDir) {
                    int lastIndex = path.charAt(path.length() - 1) == '/' ? path.lastIndexOf("/", 1)
                            : path.lastIndexOf("/");

                    result.authProperties = XAuthManager.instance.getAuthProperties(path);
                    if (result.authProperties != null && result.authProperties.containsKey("authentication")) {
                        result.needsLogin = (Boolean) result.authProperties.get("authentication");
                    } else {
                        result.needsLogin = path.length() <= 1 || path.charAt(lastIndex + 1) != '_';
                    }
                }
                resourceInfoMap.put(path, result);
            }
            resInfo = resourceInfoMap.get(path);
        }
        return resInfo;
    }

    private boolean exists(Resource res) {
        return new File(res.realPath).exists();
    }

    public abstract static class Resource {
        private boolean implicit;//the name is index
        private boolean needsLogin;
        private String path;
        private String loggedRedirect;
        private String unloggedRedirect;
        private String realPath;
        private String relativePath;
        private Map<String, Object> authProperties;

        public boolean isNeedsLogin() {
            return needsLogin;
        }

        public String getPath() {
            return path;
        }

        public String getLoggedRedirect() {
            return loggedRedirect;
        }

        public String getUnloggedRedirect() {
            return unloggedRedirect;
        }

        public String getRealPath() {
            return realPath;
        }

        public Map<String, Object> getAuthProperties() {
            return authProperties;
        }

        public boolean isImplicit() {
            return implicit;
        }

        public String getRelativePath() {
            return relativePath;
        }
    }

    public static class HtmxResource extends Resource {
        private boolean modal;

        public boolean isImplicit() {
            return super.isImplicit();
        }

        public boolean isModal() {
            return modal;
        }
    }

    public static class JsResource extends Resource {
        private HtmxResource htmx;

        public HtmxResource getHtmx() {
            return htmx;
        }
    }

    public static class HtmxInfo {
        private String path;
        private String templateName;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getTemplateName() {
            return templateName;
        }

        public void setTemplateName(String templateName) {
            this.templateName = templateName;
        }
    }

    private boolean isDir(String pathInfo) {
        Boolean isDir = dirs.get(pathInfo);
        if (isDir != null) {
            return isDir;
        } else {
            isDir = false;
            if (pathInfo.indexOf('.') < 0) {
                String diskPath = this.ctx.getRealPath("/pages" + pathInfo);
                if (diskPath != null) {
                    isDir = new File(diskPath).isDirectory();
                }
            }
            dirs.put(pathInfo, isDir);
            return isDir;
        }
    }

    public Map<String, HtmxInfo> getHtmxInfo() {
        return htmxInfo;
    }
}
