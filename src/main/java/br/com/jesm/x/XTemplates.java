package br.com.jesm.x;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptException;
import javax.servlet.ServletContext;

public class XTemplates {

	protected static Map<String, String> webTemplateIndex = new HashMap<String, String>();

	protected static Map<String, String> webTemplate = new HashMap<String, String>();

	protected static String applyTemplate(String html, String templateName, ServletContext ctx, boolean isIndex)
			throws IOException {
		if (templateName == null) {
			templateName = "XDEFAULT_TEMPLATE_NAME";
		}
		int indexTemplateInfo = html.indexOf("<template-info ");
		if (indexTemplateInfo >= 0) {
			String info = html.substring(indexTemplateInfo, html.indexOf(">", indexTemplateInfo));
			int indexPath = info.indexOf("path=") + 5;
			String template = info.substring(indexPath);
			if (template.startsWith("\"")) {
				template = template.substring(1, template.indexOf("\"", 1));
			} else if (template.startsWith("'")) {
				template = template.substring(1, template.indexOf("'", 1));
			} else {
				throw new RuntimeException("Invalid template tag");
			}
			templateName = template;

		}
		if ((!webTemplate.containsKey(templateName) && !isIndex)
				|| (!webTemplateIndex.containsKey(templateName) && isIndex)) {
			InputStream is;
			if (templateName == null) {
				is = XTemplates.class.getResourceAsStream("/default_template.html");
			} else {
				is = ctx.getResourceAsStream("/templates/" + templateName);
			}
			String webctx = ctx.getContextPath();
			String scripts = "<script type=\"text/javascript\" src=\"" + webctx + "/x/scripts/xparams.js\"></script>"
					+ "<script>window['_x_page_timestamp_'] = " + XServlet.applicationTimestamp + ";</script>"
					+ "<script type=\"text/javascript\" src=\"" + webctx + "/x/scripts/x.js\"></script></body>";
			String template = XStreamUtil.inputStreamToString(is);
			template = template.replaceFirst("</body[\\s]*>", scripts);
			if (isIndex) {
				webTemplateIndex.put(templateName, template);
			} else {
				webTemplate.put(templateName, template);
			}

		}
		String xbody = "{xbody}";
		String template = (isIndex ? webTemplateIndex : webTemplate).get(templateName);
		int index = template.indexOf(xbody);
		return template.substring(0, index) + html + template.substring(index + xbody.length());
	}

	protected static String applyScriptForPopup(String html) throws IOException {
		return html + "<script type=\"text/javascript\">" + "var scr = document.createElement('script');"
				+ "scr.src = window.location.pathname + '.js';"
				+ "scr.onload = function(){if(window.onInit){onInit();}};" + "document.body.appendChild(scr);"
				+ "</script></body>";

	}

	protected static String modalTemplate(String templateName, ServletContext ctx) throws IOException {
		InputStream is;
		if (templateName == null) {
			is = XTemplates.class.getResourceAsStream("/modal_template.html");
		} else {
			is = ctx.getResourceAsStream("/templates/" + templateName);
		}
		return XStreamUtil.inputStreamToString(is).replaceAll("\n", "").replaceAll("'", "\\\\'");
	}

	protected static String templateScripts(String templateName, ServletContext ctx)
			throws IOException, ScriptException {
		InputStream is;
		if (templateName == null) {
			is = XTemplates.class.getResourceAsStream("/templates.js");
		} else {
			is = ctx.getResourceAsStream("/templates/" + templateName);
		}
		String scr = XStreamUtil.inputStreamToString(is);
		return "\n(function(){" + scr + ";" + XJS.getFirstLevelFunctions(scr) + "})();";
	}

	public static byte[] loaderImg(String property, ServletContext ctx) throws IOException {
		InputStream is;
		if (property == null) {
			is = XTemplates.class.getResourceAsStream("/loader.gif");
		} else {
			is = ctx.getResourceAsStream(property);
		}
		return XStreamUtil.inputStreamToByteArray(is);
	}

	public static String replaceVars(String strResponse, ServletContext ctx) {
		return strResponse.replaceAll("\\{webctx\\}", ctx.getContextPath());
	}
}
