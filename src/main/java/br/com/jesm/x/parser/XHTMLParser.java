package br.com.jesm.x.parser;

import br.com.jesm.x.XFileUtil;

public class XHTMLParser {

	private char[] ca;

	private int i;

	XNode current;

	XElement currentParent = null;

	int lastAdvance;

	public XHTMLDocument parse(String html) throws XHTMLParsingException {
		ca = html.toCharArray();
		XHTMLDocument doc = new XHTMLDocument();
		currentParent = doc;
		while (hasMore()) {
			if (nextIs("<!--")) {
				advance();
				inComment();
			} else if (nextIs("<![")) {
				advance();
				inComment();
			} else if (nextIs("</")) {
				advance();
				close();
			} else if (!nextIs("< ") && nextIs("<")) {
				advance();
				inTag(doc);
			} else {
				read();
			}
		}
		while (!currentParent.isClosed() && currentParent != doc) {
			currentParent.setNotClosed();
			currentParent = currentParent.getParent();
		}
		return doc;
	}

	private void inTag(XHTMLDocument doc) {
		String name = readTill(" ", ">", "/>", "\n", "\t").toLowerCase();
		XElement element = new XElement(name, doc);
		currentParent.addChild(element);
		currentParent = element;
		current = element;
		StringBuffer attVal = new StringBuffer();
		int dynAttr = 0;
		while (true) {
			if (discard(' ')) {
				if (attVal.toString().trim().length() > 0) {
					element.setAttribute(attVal.toString().trim(), null);
				}
				attVal = new StringBuffer();
			}
			if (nextIs("/>")) {
				advance();
				if (attVal.length() > 0) {
					element.setAttribute(attVal.toString(), null);
				}
				closeElement();
				break;
			} else if (nextIs(">")) {
				advance();
				if (attVal.length() > 0) {
					element.setAttribute(attVal.toString(), null);
				}
				current = null;
				break;
			} else if (nextIs("{{")) {
				advance();
				StringBuffer script = new StringBuffer();
				while (!nextIs("}}")) {
					read(script);
				}
				discard('}');
				discard('}');
				element.setAttribute("_outxdynattr_" + dynAttr++, "##" + script.toString() + "##");
			}

			char s = read(attVal);
			if (s == '=') {
				String attName = attVal.substring(0, attVal.length() - 1);
				attVal = new StringBuffer();
				char c = ca[i];
				if (c == '\'' || c == '"' || c != ' ') {
					s = c;
					read(attVal);
					boolean aspas = c == '\'' || c == '"';
					while (true) {
						c = read(attVal);
						boolean endNoAspas = (!aspas && c == ' ')
								|| (!aspas && ((c == '/' && ca[i + 1] == '>') || c == '>'));
						if (endNoAspas || (aspas && c == s && previous(2) != '\\')) {
							String val;
							if (endNoAspas) {
								i--;
								val = attVal.substring(0, attVal.length() - 1);
							} else {
								val = attVal.toString();
							}
							element.setAttribute(attName, val);
							attVal = new StringBuffer();
							break;
						}
					}
				} else {
					element.setAttribute(attName, null);
				}
			}
		}
	}

	private char previous(int t) {
		return ca[i - t];
	}

	private boolean discard(char c) {
		int j = i;
		boolean discarded = false;
		while (ca[j] == c) {
			j++;
			discarded = true;
		}
		i = j;
		return discarded;
	}

	private void close() throws XHTMLParsingException {
		String tagName = null;
		try {
			tagName = readTill(">").toLowerCase();
			XElement toClose = current instanceof XElement ? (XElement) current : currentParent;
			while (!tagName.equals(toClose.getName())) {
				if (!toClose.isClosed()) {
					toClose.setNotClosed();
				}
				XNode prev = toClose;
				while (true) {
					prev = prev.getPrevious();
					if (prev == null) {
						toClose = toClose.getParent();
						break;
					} else if (prev instanceof XElement && !((XElement) prev).isClosed()) {
						toClose = (XElement) prev;
						break;
					}
				}
			}
			toClose.close();
			currentParent = toClose.getParent();

			i++;
			current = null;
		} catch (Exception e) {
			throw new XHTMLParsingException("Error closing tag " + (tagName != null ? tagName : ""));
		}
	}

	private void closeElement() {
		currentParent = current.getParent();
		current.close();
		current = null;
	}

	private String readTill(String... s) {
		StringBuffer sb = new StringBuffer();
		int j = i;
		main: while (true) {
			for (int z = 0; z < s.length; z++) {
				if (nextIs(s[z], j)) {
					break main;
				}
			}
			sb.append(ca[j++]);
		}
		lastAdvance = j - i;
		advance();
		return sb.toString();
	}

	private void inComment() {
		current = new XComment();
		currentParent.addChild(current);
		while (true) {
			if (nextIs("-->")) {
				advance();
				current.close();
				current = null;
				break;
			}
			read();
		}
	}

	private void advance() {
		i += lastAdvance;
	}

	private boolean nextIs(String s) {
		return nextIs(s, null);
	}

	private boolean nextIs(String s, Integer index) {
		StringBuffer sb = new StringBuffer();
		lastAdvance = s.length();
		int usedIndex = index == null ? i : index;
		int j = usedIndex;
		for (; j < s.length() + usedIndex && j < ca.length; j++) {
			sb.append(ca[j]);
		}
		return sb.toString().equals(s);
	}

	private boolean hasMore() {
		return i < ca.length - 1;
	}

	private char read() {
		return read(null);
	}

	private char read(StringBuffer sb) {
		char c = ca[i++];
		if (sb == null) {
			if (current == null) {
				current = new XText();
				currentParent.addChild(current);
			}
			current.addChar(c);
		} else {
			sb.append(c);
		}
		return c;
	}

	public static void main(String[] args) {
		try {
			XHTMLParser parser = new XHTMLParser();
			XHTMLDocument doc = parser
					.parse(XFileUtil.readFile("/Users/eduardo/work/eclipseworkspaces/xloja/Testes/teste.html"));

			System.out.println(doc.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
