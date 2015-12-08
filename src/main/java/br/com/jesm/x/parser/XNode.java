package br.com.jesm.x.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class XNode {

	protected StringBuffer buffer = new StringBuffer();

	protected XElement parent;

	public XNode getNext() {
		int index = this.getParent().getChildren().indexOf(this);
		if (index == this.getParent().getChildren().size() - 1) {
			return null;
		}
		return this.getParent().getChildren().get(index + 1);
	}

	public XNode getPrevious() {
		int index = this.getParent().getChildren().indexOf(this);
		if (index == 0) {
			return null;
		}
		return this.getParent().getChildren().get(index - 1);
	}

	public void addAfter(XNode node) {
		int index = this.getParent().getChildren().indexOf(this);
		if (index == this.getParent().getChildren().size() - 1) {
			this.getParent().addChild(node);
		} else {
			this.getParent().insertChildren(node, index + 1);
		}
	}

	public void addBefore(XNode node) {
		int index = this.getParent().getChildren().indexOf(this);
		if (index == 0) {
			this.getParent().insertChildren(node, 0);
		} else {
			this.getParent().insertChildren(node, index);
		}
	}

	public XElement getParent() {
		return parent;
	}

	public void addChar(char c) {
		buffer.append(c);
	}

	public void addString(String s) {
		buffer.append(s);
	}

	public abstract void close();

	@Override
	public String toString() {
		return buffer.toString();
	}

	protected String prepareValue(String tag, String value, char a) {
		return prepareValue(tag, value, false, a);
	}

	protected String prepareValue(String tag, String value, boolean escape) {
		return prepareValue(tag, value, escape, '"');
	}

	protected String prepareValue(String tag, String value, boolean escape, char a) {
		if (value != null) {
			Pattern pattern = Pattern.compile("\\{\\{(?:(?!\\{\\{|}}).)*}}");
			Matcher matcher;
			while ((matcher = pattern.matcher(value)).find()) {
				String var = matcher.group().trim();
				var = var.substring(2, var.length() - 2).replaceAll("\"", "!#!");
				if (a == '\'') {
					var = var.replaceAll(Character.toString(a), "\\\\" + Character.toString(a));
				}
				value = value.substring(0, matcher.start())
						+ (escape ? "<" + tag + " xvar=\\\"" + var + "\\\"></" + tag + ">" : "<" + tag + " xvar=" + a
								+ var + a + "></" + tag + ">") + value.substring(matcher.end());
			}
		}
		return value;
	}
	
	public void remove() {
		this.getParent().getChildren().remove(this);
		this.parent = null;
	}

}
