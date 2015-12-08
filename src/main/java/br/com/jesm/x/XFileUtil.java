package br.com.jesm.x;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.io.IOUtils;

public class XFileUtil {

	private static final String[] IMG_EXTENSIONS = { ".jpg", ".jpeg", ".png", ".gif" };

	private static byte[] gif;

	static {
		InputStream in = XFileUtil.class.getResourceAsStream("/px.gif");
		try {
			gif = IOUtils.toByteArray(in);
		} catch (IOException e) {
			throw new RuntimeException("Erro carregando a imagem vazia!", e);
		}

	}

	public static boolean validateImage(String fileName) {

		for (String extension : IMG_EXTENSIONS) {
			if (fileName.toUpperCase().endsWith(extension.toUpperCase()))
				return true;
		}

		return false;
	}

	public static void printUploadResponse(boolean ok) throws IOException {

		OutputStream out = XContext.getXResponse().getOutputStream();
		out.write(("<html><script>parent.X._uploadResponse('" + ok + "');</script></html>").getBytes());
		out.flush();
	}

	public static void printEmptyGif() throws IOException {
		XContext.getXResponse().setContentType("image/gif");
		OutputStream out = XContext.getXResponse().getOutputStream();
		out.write(gif);
		out.flush();
	}

	public static void sendFile(XFile f) throws IOException {
		XContext.getXResponse().setContentType(f.getContentType());
		XContext.getXResponse().setContentLength(f.getData().length);
		OutputStream output = XContext.getXResponse().getOutputStream();
		output.write(f.getData());
		output.flush();
	}

	public static String readFile(String fileName) throws IOException {
		BufferedReader br = new BufferedReader(new FileReader(fileName));
		try {
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				sb.append(line);
				sb.append("\n");
				line = br.readLine();
			}
			return sb.toString();
		} finally {
			br.close();
		}
	}
}
