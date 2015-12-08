package br.com.jesm.x.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XElement extends XNode {

	public static final String NO_END_TAG = "_base_link_meta_hr_br_wbr_area_img_track_embed_param_source_col_input_keygen_";

	private List<XNode> children = new ArrayList<XNode>();

	private Map<String, XAttribute> attributes = new HashMap<String, XAttribute>();

	private String innerText;

	private String tagText;

	private String name;

	private boolean notClosed;

	private boolean isClosed;

	protected XHTMLDocument root;

	public XElement(String name, XHTMLDocument root) {
		this.root = root;
		this.name = name;
	}

	public List<XElement> getElementsByName(String name) {
		List<XElement> list = new ArrayList<XElement>();
		for (XElement e : getChildrenElement()) {
			if (e instanceof XElement) {
				if (((XElement) e).getName().equals(name)) {
					list.add(e);
				}
				list.addAll(e.getElementsByName(name));
			}
		}
		return list;
	}

	public void addElement(String name) {
		XElement e = new XElement(name, this.root);
		addChild(e);
	}

	public void addChild(XNode node) {
		node.parent = this;
		this.children.add(node);
	}

	public void insertChildren(XNode node, int index) {
		node.parent = this;
		this.children.add(index, node);
	}

	public void insertChildAfter(XNode node, XNode after) {
		node.parent = this;
		this.children.add(this.children.indexOf(after) + 1, node);
	}

	public void insertChildBefore(XNode node, XNode before) {
		node.parent = this;
		this.children.add(this.children.indexOf(before), node);
	}

	public List<XNode> getChildren() {
		return children;
	}

	public List<XElement> getChildrenElement() {
		List<XElement> result = new ArrayList<XElement>();
		List<XNode> list = this.getChildren();
		for (XNode xNode : list) {
			if (xNode instanceof XElement) {
				result.add((XElement) xNode);
			}
		}
		return result;
	}

	public String getInnerText() {
		return innerText;
	}

	public String getTagText() {
		return tagText;
	}

	public Collection<XAttribute> getAttributes() {
		return attributes.values();
	}

	public void setAttribute(String n, String v) {
		XAttribute a = new XAttribute(n,
				v != null && v.length() > 0 ? prepareValue("xattrobject", v, v.charAt(0) == '"' ? '\'' : '"') : null);
		this.attributes.put(a.getName(), a);
	}

	public String getAttribute(String n) {
		return this.attributes.containsKey(n) ? this.attributes.get(n).getValue() : null;
	}

	public String getName() {
		return name;
	}

	public void close() {
		this.isClosed = true;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer("<");
		sb.append(name);
		for (XAttribute a : getAttributes()) {
			sb.append(" ").append(a.toString());
		}
		sb.append(">");
		if (!this.notClosed && NO_END_TAG.indexOf("_" + name + "_") < 0) {
			printHTML(sb);
			sb.append("</" + name + ">");
		}
		return sb.toString();
	}

	public String innerHTML() {
		StringBuffer sb = new StringBuffer();
		printHTML(sb);
		return sb.toString();
	}

	private void printHTML(StringBuffer sb) {
		for (XNode n : this.getChildren()) {
			sb.append(n.toString());
		}
	}

	public void setNotClosed() {
		if (this.getParent() != null) {
			this.notClosed = true;
			for (XNode n : this.getChildren()) {
				this.getParent().addChild(n);
			}
			this.getChildren().clear();
		}
	}

	public boolean isClosed() {
		return isClosed;
	}

	public void remove() {
		this.root.elementWithDynamicAttributes.remove(this);
		super.remove();
	}

	public void replaceWith(XNode node) {
		this.getParent().insertChildAfter(node, this);
		this.remove();
	}

	public void addClass(String c) {
		String classes = getAttribute("class");
		if (classes == null || classes.trim().equals("")) {
			this.setAttribute("class", c);
		} else {
			this.setAttribute("class", classes + " " + c);
		}
	}

}
