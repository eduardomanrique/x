package br.com.jesm.x.parser;


public class XText extends XNode {

	private String text;

	public String getText() {
		return text == null ? this.buffer.toString() : text;
	}

	public void setText(String text) {
		this.text = text;
	}

	@Override
	public String toString() {
		return prepareValue("xobject", getText(), this.getParent().getName().equalsIgnoreCase("script"));
	}

	public void close() {
	}
}
