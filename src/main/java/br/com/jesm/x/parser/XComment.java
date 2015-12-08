package br.com.jesm.x.parser;

public class XComment extends XText {

	public XComment() {
		this.addString("<!--");
	}

	public void close() {
		this.addString("-->");
	}

}
