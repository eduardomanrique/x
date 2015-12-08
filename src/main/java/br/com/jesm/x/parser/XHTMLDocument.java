package br.com.jesm.x.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class XHTMLDocument extends XElement{
	
	protected Map<XElement, List<XAttribute>> elementWithDynamicAttributes = new HashMap<XElement, List<XAttribute>>();

	public XHTMLDocument() {
		super("DOCUMENT", null);
		this.root = this;
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for(XNode n : this.getChildren()){
			sb.append(n.toString());
		}
		return sb.toString();
	}
}
