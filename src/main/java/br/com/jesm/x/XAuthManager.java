package br.com.jesm.x;

import br.com.jesm.x.model.XUser;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 * Created by eduardo on 8/21/16.
 */
public enum XAuthManager {
    instance;

    private Map<String, Map<String, Object>> authPropertiesMap = new HashMap<String, Map<String, Object>>();
    private ServletContext ctx;

    public void init(ServletContext ctx) {
        this.ctx = ctx;
    }

    public Map<String, Object> getAuthProperties(String pathInfo) throws IOException {
        Map<String, Object> result = authPropertiesMap.get(pathInfo);
        if (result == null) {
            int dotIndex = pathInfo.lastIndexOf('.');
            int barIndex = pathInfo.lastIndexOf('/');
            String propPath;
            if (dotIndex > barIndex) {
                propPath = pathInfo.substring(0, dotIndex);
            } else {
                propPath = pathInfo;
            }
            byte[] bytes = XFileUtil.instance.readFromDisk(
                    "/pages" + propPath + (propPath.lastIndexOf('/') == 0 ? "/index" : "") + ".auth", null,
                    this.ctx);
            if (bytes == null) {
                bytes = XFileUtil.instance.readFromDisk("/pages" + propPath.substring(0, propPath.lastIndexOf('/')) + "/auth",
                        null, this.ctx);
            }
            if (bytes != null) {
                result = new HashMap<String, Object>();
                StringReader reader = new StringReader(new String(bytes));
                Properties p = new Properties();
                p.load(reader);
                String roles = (String) p.get("roles");
                if (roles != null) {
                    String[] rolesArray = roles.split(",");
                    for (int i = 0; i < rolesArray.length; i++) {
                        rolesArray[i] = rolesArray[i].trim();
                    }
                    result.put("roles", rolesArray);
                }
                String function = (String) p.get("function");
                if (function != null) {
                    result.put("function", function);
                }
                String authentication = (String) p.get("authentication");
                result.put("authentication", authentication != null && authentication.equalsIgnoreCase("TRUE"));
                authPropertiesMap.put(pathInfo, result.size() > 0 ? result : null);
            }
        }
        return result;
    }


    public boolean checkAuthorization(HttpServletResponse resp, XUser user, String path) throws IOException {
        Map<String, Object> authProperties = getAuthProperties(path);
        boolean allowed = true;
        if (authProperties != null) {
            allowed = false;
            String[] roles = (String[]) authProperties.get("roles");
            if (roles != null && user.getRole() != null && Arrays.binarySearch(roles, user.getRole()) >= 0) {
                allowed = true;
            }
            if (!allowed) {
                List<String> functions = user.getAvailableFunctions();
                if (functions != null && authProperties.get("function") != null
                        && functions.contains(authProperties.get("function"))) {
                    allowed = true;
                }
            }
        }
        return allowed;
    }

}
