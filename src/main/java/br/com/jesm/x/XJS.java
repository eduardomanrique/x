package br.com.jesm.x;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.log4j.Logger;

public class XJS {

	static final Logger logger = Logger.getLogger(XJS.class);

	static ScriptEngineManager factory = new ScriptEngineManager();
	static ScriptEngine engine;
	static ScriptEngine compEngine;
	static String components;

	static {
		StringBuilder sb = new StringBuilder("[");
		for (String[] comp : XComponents.components) {
			sb.append("['X', 'comp', '").append(comp[2]).append("'],");
		}
		sb.append("]");
		components = sb.toString();
	}

	static {
		factory = new ScriptEngineManager();
		engine = factory.getEngineByName("JavaScript");
		String esprimaJS;
		try {
			esprimaJS = XStreamUtil.loadResource("/esprima.js");
			engine.eval(esprimaJS);
		} catch (Exception e) {
			String msg = "Error starting JSParser.";
			logger.error(msg, e);
			throw new RuntimeException(msg, e);
		}
	}

	public static synchronized void prepareComponents(String js) {

		compEngine = factory.getEngineByName("JavaScript");
		try {
			compEngine.eval(js);
		} catch (Exception e) {
			String msg = "Error starting Components on server.";
			logger.error(msg, e);
			throw new RuntimeException(msg, e);
		}

	}

	@SuppressWarnings("unchecked")
	public static synchronized Map<String, Map<String, String>> getChildElementsInfo(String componentName) {
		if (componentName == null) {
			return new HashMap<String, Map<String, String>>();
		}
		try {
			compEngine.eval("var __temp_info__ = " + componentName + ".getChildElementsInfo();");
			return (Map<String, Map<String, String>>) compEngine.get("__temp_info__");
		} catch (ScriptException e) {
			String msg = "Error getting child info on server.";
			logger.error(msg, e);
			throw new RuntimeException(msg, e);
		}
	}

	public static synchronized String getHtml(String componentName, Map<String, Object> compObj) {
		try {
			compEngine.eval("var __temp_comp__ = " + XJson.toJson(compObj) + ";");
			compEngine.eval("var __temp_html__ = " + componentName + ".getHtml(__temp_comp__);");
			Object html = compEngine.get("__temp_html__");
			@SuppressWarnings("unchecked")
			Map<String, Object> compObjChanged = (Map<String, Object>) compEngine.get("__temp_comp__");
			for (Map.Entry<String, Object> e : compObjChanged.entrySet()) {
				compObj.put(e.getKey(), e.getValue());
			}
			return html != null ? html.toString() : "";
		} catch (ScriptException e) {
			String msg = "Error getHtml on server for component: " + componentName;
			logger.error(msg, e);
			throw new RuntimeException(msg, e);
		}
	}

	public static synchronized String getFirstLevelFunctions(String js) throws IOException, ScriptException {
		int from = 0;
		while (js.indexOf('\\', from) >= 0) {
			int index = js.indexOf('\\', from);
			js = js.substring(0, index) + "\\\\" + js.substring(index + 1);
			from = index + 2;
		}
		js = js.replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\n").replaceAll("\\'", "\\\\'");

		String s = XStreamUtil.loadResource("/esprimaIterator.js");
		String esprima = "var parsed = esprima.parse(\"" + js + "\");";
		synchronized (engine) {
			engine.eval("var append_xmeta = " + components + ";");

			engine.eval(esprima);

			engine.eval(s);
			engine.eval("var newScript = iterate(parsed);");
			engine.eval("var thisFunctions = getFirstLevelFunctions(parsed);");
			return thisFunctions("X", engine.eval("thisFunctions").toString());
		}
	}

	public static synchronized String instrumentController(String js, String jsName, String resource)
			throws IOException, ScriptException {
		js = prepareXVars(js);
		int from = 0;
		while (js.indexOf('\\', from) >= 0) {
			int index = js.indexOf('\\', from);
			js = js.substring(0, index) + "\\\\" + js.substring(index + 1);
			from = index + 2;
		}
		js = js.replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\n").replaceAll("\\'", "\\\\'");

		String s = XStreamUtil.loadResource("/esprimaIterator.js");
		String esprima = "var parsed = esprima.parse(\"" + js + "\");";
		synchronized (engine) {
			engine.eval("var append_xmeta = " + components + ";");

			engine.eval(esprima);

			engine.eval(s);
			engine.eval("var newScript = iterate(parsed);");
			engine.eval("var thisFunctions = getFirstLevelFunctions(parsed);");
			return "X._addController(new function(){var _x_meta = {id:'c" + System.currentTimeMillis() + "'};\n\n"
					+ engine.eval("newScript").toString() + "\n\n\n\n\n"
					+ thisFunctions("this", engine.eval("thisFunctions").toString())
					+ "if(!document.body.getAttribute('_x_ctx')){document.body.setAttribute('_x_ctx', _x_meta.id);}"
					+ "this._jsName = '" + (jsName != null ? jsName : "") + "';this._xresource = '"
					+ (resource != null ? resource : "") + "';"
					+ "this._x_eval = function(s){return eval(s);};this._x_getControllerId = function(){return _x_meta.id};});";
		}
	}

	private static String prepareXVars(String js) {
		StringBuilder result = new StringBuilder();
		StringBuilder xvars = new StringBuilder();
		String[] jsAux = js.split("\n");
		int i = 0;
		boolean getNext = true;
		String line = null;
		int countVars = 0;
		int countImports = 0;
		while (i < jsAux.length) {
			String nextLine = "";
			if (getNext) {
				line = jsAux[i++];
				if (jsAux.length > i) {
					nextLine = jsAux[i].trim();
				}
			} else {
				getNext = true;
				if (jsAux.length >= i) {
					nextLine = jsAux[i + 1].trim();
				}
			}
			if (line.trim().startsWith("//service:") && nextLine.startsWith("var ")) {
				i++;
				String[] valores = parse("//service:", line, nextLine);
				xvars.append(valores[0]).append(" = X.bindService('").append(valores[1]).append("');\n");
				if (valores[2] != null) {
					getNext = false;
					line = valores[2];
				}
				result.append("var ").append(valores[0]).append(";\n");
				countVars++;
			} else if (line.trim().startsWith("//import:") && nextLine.startsWith("var ")) {
				String[] valores = parse("//import:", line, nextLine);
				xvars.append("X.import('").append(valores[1])
						.append(".js', function(o){" + valores[0] + "=o;__chord.notify();});\n");
				if (valores[2] != null) {
					getNext = false;
					line = valores[2];
				}
				result.append("var ").append(valores[0]).append(";\n");
				countVars++;
				countImports++;
			} else {
				result.append(line).append("\n");
			}
		}
		if (countVars > 0) {
			return "var __xvars__ = {length:" + countImports + ", __exec: function(__chord){\n" + xvars
					+ "\n;if(__chord.getCount() == 0){__chord.getOnFinishFunction()()}}}\n" + result.toString();
		} else {
			return "var __xvars__ = null;" + result.toString();
		}
	}

	private static String[] parse(String fn, String line, String nextLine) {
		int index = nextLine.indexOf(";");
		String newNextLine = null;
		if (index > 0) {
			newNextLine = nextLine.substring(index + 1);
			nextLine = nextLine.substring(0, index);
		}
		String varName = nextLine.split(" ")[1];
		return new String[] { varName, line.substring(fn.length()).trim(), newNextLine };
	}

	private static String thisFunctions(String var, String eval) {
		String[] split = eval.split("\\|");
		StringBuilder sb = new StringBuilder();
		for (String fn : split) {
			if (fn != null && !fn.trim().equals("")) {
				sb.append(var + "." + fn + " = " + fn + ";");
			}
		}
		return sb.toString();
	}

	public static void main(String[] args) {
		try {
			XComponents.components.add(new String[] { "", "", "teste.a.call" });
			XComponents.components.add(new String[] { "", "", "x.a.call" });
			// System.out.println(instrument(XStreamUtil.loadResource("/test.js"),
			// "teste", "teste"));
			Map<String, String> m = new HashMap<String, String>();
			m.put("titulo", "<strong><xobject var='xuser.empresa.razaoSocial'></xobject></strong>");
			System.out.println(XJson.toJson(m));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
