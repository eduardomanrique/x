package br.com.jesm.x;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;

import com.google.gson.Gson;

import br.com.jesm.x.parser.XAttribute;
import br.com.jesm.x.parser.XElement;
import br.com.jesm.x.parser.XHTMLDocument;
import br.com.jesm.x.parser.XHTMLParser;
import br.com.jesm.x.parser.XHTMLParsingException;
import br.com.jesm.x.parser.XNode;
import br.com.jesm.x.parser.XText;

public class XComponents {

	protected static List<String[]> components = new ArrayList<String[]>();

	protected static String serverJSComponents;

	private static final Gson gson = new Gson();

	private static int countExec = 0;

	protected static String js(ServletContext ctx) throws IOException {
		Map<String, String> map = new HashMap<String, String>();
		@SuppressWarnings("unchecked")
		Set<String> resources = ctx.getResourcePaths("/components");
		for (String resource : resources) {
			map.put(resource, XStreamUtil.inputStreamToString(ctx.getResourceAsStream(resource)));
		}
		return js(map, ctx.getContextPath());
	}

	protected static String js(Map<String, String> map, String ctxPath) throws IOException {
		serverJSComponents = "function generateId() {return java.lang.System.currentTimeMillis() + parseInt(Math.random() * 999999);}";
		StringBuilder sb = new StringBuilder("var components = {};");

		for (String path : map.keySet()) {
			String[] parts = path.split("/");
			if (parts[parts.length - 1].startsWith(".")) {
				System.out.println("path " + path);
				continue;
			}
			String last = "components";
			for (int i = 2; i < parts.length - 1; i++) {
				String newInst = last + "['" + parts[i] + "']";
				sb.append(newInst).append("={}");
				last = newInst;
			}
			String resName = parts[parts.length - 1].split("\\.")[0];
			String create = "new" + resName.substring(0, 1).toUpperCase() + resName.substring(1);
			last = last + "['" + resName + "']";
			String compJS = map.get(path);
			String js = last
					+ "= new function(){ var toBind = {};function get(id){ var h={id:id};X.merge(toBind, h);return h;}; this.get = get;function bindToHandle(obj) {X.merge(obj, toBind);}; function expose(name, fn){"
					+ last + "[name] = fn;};" + compJS
					+ ";this.getHtml = getHtml;try{this.getBindingMethods = getBindingMethods;}catch(e){};var generateId = X.generateId;try{this.getChildElementsInfo = getChildElementsInfo;}catch(e){this.getChildElementsInfo = function(){return {}}}"
					+ ";try{X._addExecuteWhenReady(load);}catch(e){};try{this.onReady = onReady;}catch(e){};};";

			serverJSComponents += last + "= new function(){ " + compJS
					+ ";this.getHtml = getHtml;try{this.getChildElementsInfo = getChildElementsInfo;}catch(e){this.getChildElementsInfo = function(){return {}}}};";
			sb.append(js);
			if (!resName.startsWith("_")) {
				components.add(new String[] { resName, last, create });
			}
		}
		String array = "var _comps = [";
		for (String[] cname : components) {
			array += "[\"" + cname[0] + "\",\"" + cname[1] + "\"],";
		}
		array += "];";
		sb.insert(0, array);
		String result = sb.toString().replaceAll("\\{webctx\\}", ctxPath);
		XJS.prepareComponents("var components = {};" + serverJSComponents);
		return result;
	}

	/**
	 * @param htmlIn
	 * @param context
	 * @param isPopup
	 * @param realPath
	 * @return
	 */
	public static String prepareComponents(String htmlIn, String context, Properties properties, boolean isPopup,
			String pathInfo) {
		try {
			StringBuffer scripts = new StringBuffer();
			XHTMLParser parser = new XHTMLParser();
			XHTMLDocument doc = parser.parse(htmlIn);
			for (String[] comp : components) {
				String tagName = comp[0];
				String componentName = comp[1];

				buildComponent(tagName, componentName, doc, scripts);
			}
			buildIterators(doc, 0, scripts);

			XElement body = doc.getElementsByName("body").get(0);
			XElement postScript = new XElement("xscript", doc);
			postScript.setAttribute("id", "xpostscript");
			postScript.setAttribute("style", "display: none");
			body.addChild(postScript);
			XText text = new XText();
			postScript.addChild(text);
			text.setText("function __post_xscript__(){" + scripts + "};");

			XElement bodyDiv = new XElement("div", doc);
			bodyDiv.setAttribute("id", "_xbodydiv_" + (isPopup ? pathInfo : ""));
			bodyDiv.setAttribute("style", "display: none");
			List<XNode> children = new ArrayList<XNode>(body.getChildren());
			for (XNode node : children) {
				node.remove();
				bodyDiv.addChild(node);
			}
			body.insertChildren(bodyDiv, 0);

			XElement loaderDiv = new XElement("div", doc);
			loaderDiv.setAttribute("id", "_xpreloader_");
			loaderDiv.setAttribute("style",
					"background:" + properties.getProperty("loader.background")
							+ ";width: 100%;margin: 0;position: fixed;height: 100%;left: 0;top: 0;border: 0;-webkit-border-radius: 0;"
							+ "-moz-border-radius: 0;-o-border-radius: 0;border-radius: 0;z-index: 3333;opacity:0.5;");

			XElement loader = new XElement("img", doc);
			loaderDiv.addChild(loader);
			loader.setAttribute("src", "{webctx}/x/loader.gif");
			loader.setAttribute("style", "position: relative;width: 40px; height: 40px; left: 48%; top: 48%;");

			body.insertChildren(loaderDiv, 0);

			String html = doc.toString().replaceAll("\\{webctx\\}", context);
			html = prepareLabels(html);
			return html;
		} catch (Exception e) {
			throw new RuntimeException("Error preparing html file", e);
		}
	}

	static Pattern pattern = Pattern.compile("\\(%(.*?%)\\)");

	private static String prepareLabels(String html) throws XLabelException {
		Matcher matcher = pattern.matcher(html);
		while (matcher.find()) {
			String val = matcher.group();
			val = val.substring(2, val.length() - 2);
			String newVal = XLabels.getLabel(val);
			html = matcher.replaceAll(newVal);
			matcher = pattern.matcher(html);
		}
		return html;
	}

	private static void buildIterators(XHTMLDocument doc, int level, StringBuffer scripts)
			throws XHTMLParsingException {
		XElement element;
		while ((element = findDeepestChild(doc, "xiterator")) != null) {
			if (element.getParent().getName().equals("body") || element.getParent().getChildrenElement().size() > 1) {
				XElement newParent = new XElement("xparentiterator", doc);
				element.replaceWith(newParent);
				newParent.addChild(element);
			}
			String id = "_xiterator_" + ((int) (Math.random() * 99999999));
			String listName = element.getAttribute("xlist");
			String varName = element.getAttribute("xvaritem");
			String indexVarName = element.getAttribute("xvarindex");
			String innerHTML = element.innerHTML();
			XNode n = element.getPrevious();
			boolean isSibling = true;
			while (n != null && !(n instanceof XElement)) {
				n = n.getPrevious();
			}
			if (n == null) {
				n = element.getParent();
				isSibling = false;
			}
			XElement ne = (XElement) n;
			ne.addClass("__xiterator__0");
			ne.addClass("__xiterator__");
			ne.setAttribute("xiteratortype", isSibling ? "sibling" : "parent");
			ne.setAttribute("xiteratorid", id);
			ne.setAttribute("xiteratortempvar", "");
			element.remove();
			XHTMLDocument innerDoc = new XHTMLParser().parse(innerHTML);
			for (XElement e : innerDoc.getChildrenElement()) {
				if (!e.getName().equals("xobject")) {
					e.addClass("__xiteratorprint__");
					e.setAttribute("xiteratortempvar", "");
				}
			}
			String htmlDoc = innerDoc.toString().replaceAll("\n", "").replaceAll("\\\\\"", "%###%###%")
					.replaceAll("\"", "\\\\\"").replaceAll("%###%###%", "\\\\\\\"").replaceAll("<", "{:");
			scripts.append("X.__registerIterator(\"" + id + "\",\"" + listName + "\", \"" + varName + "\",\""
					+ indexVarName + "\",\"" + htmlDoc + "\");");
		}
	}

	private static void buildComponent(String tagName, String componentName, XHTMLDocument doc, StringBuffer scripts)
			throws XHTMLParsingException {
		XElement element;
		while ((element = findDeepestChild(doc, tagName.toLowerCase())) != null) {
			Map<String, Object> infoProperties = new HashMap<String, Object>();
			Map<String, Map<String, String>> childInfo = XJS.getChildElementsInfo(componentName);
			for (Map.Entry<String, Map<String, String>> entry : childInfo.entrySet()) {
				List<Map<String, Object>> childInfoProperties = new ArrayList<Map<String, Object>>();
				List<XElement> childElements = findAllChildren(element, (String) entry.getValue().get("from"));
				for (XElement child : childElements) {
					Map<String, Object> childInfoMap = new HashMap<String, Object>();
					childInfoMap.put("innerHTML", child.innerHTML());
					for (XAttribute a : child.getAttributes()) {
						childInfoMap.put(a.getName(), a.getValue());
					}
					child.remove();
					childInfoProperties.add(childInfoMap);
				}
				infoProperties.put(entry.getKey(), childInfoProperties);
			}
			for (XAttribute a : element.getAttributes()) {
				infoProperties.put(a.getName(), a.getValue());
			}
			String html = element.innerHTML();
			String newHTML = XJS.getHtml(componentName, infoProperties);
			if (infoProperties.containsKey("xid")) {
				newHTML = "<div _s_xid_='" + infoProperties.get("xid") + "'></div>" + newHTML + "<div _e_xid_='"
						+ infoProperties.get("xid") + "'></div>";
			}
			String fixGroupHtml = html.replaceAll("\\$", "%%##%%");
			newHTML = XStringUtil.replaceFirst(newHTML, "{xbody}", fixGroupHtml).replaceAll("%%##%%", "\\$");
			XHTMLDocument newDoc = new XHTMLParser().parse(newHTML);
			List<XNode> list = newDoc.getChildren();
			XNode newNode = list.get(0);
			element.replaceWith(newNode);
			for (int i = 1; i < list.size(); i++) {
				XNode auxNode = list.get(i);
				newNode.addAfter(auxNode);
				newNode = auxNode;
			}
			String json = gson.toJson(infoProperties);
			scripts.append("X._registerPostCreateComponent(\"" + tagName + "\","
					+ (infoProperties.containsKey("xid") ? "'" + infoProperties.get("xid") + "'" : "null") + ", " + json
					+ ");");
		}
	}

	private static List<XElement> findAllChildren(XElement element, String tagName) {
		List<XElement> list = new ArrayList<XElement>();
		for (XNode child : element.getChildren()) {
			if (child instanceof XElement) {
				XElement e = (XElement) child;
				List<XElement> children = findAllChildren(e, tagName);
				list.addAll(children);
				if (e.getName().equals(tagName.toLowerCase())) {
					list.add(e);
				}
			}
		}
		return list;
	}

	private static XElement findDeepestChild(XElement mainElement, String tagName) {
		List<XElement> list = mainElement.getElementsByName(tagName);
		if (list != null && !list.isEmpty()) {
			XElement e = list.get(0);
			XElement deep = findDeepestChild(e, tagName);
			if (deep == null) {
				return e;
			} else {
				return deep;
			}
		}
		return null;
	}

	public static void main(String[] args) {
		try {
			XJS.prepareComponents(XStreamUtil.inputStreamToString(
					new FileInputStream("/Users/eduardo/work/eclipseworkspaces/xloja/Testes/teste.js")));
			String htmlIn = XStreamUtil.inputStreamToString(
					new FileInputStream("/Users/eduardo/work/eclipseworkspaces/xloja/Testes/teste.html"));
			XHTMLParser parser = new XHTMLParser();
			XHTMLDocument doc = parser.parse(htmlIn);
			// buildComponent("texto", "components['texto']", doc, new
			// StringBuffer());
			System.out.println(doc);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
