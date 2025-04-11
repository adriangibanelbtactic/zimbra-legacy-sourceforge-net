package com.zimbra.cs.zimlet;

import com.zimbra.cs.servlet.ZimbraServlet;
import com.zimbra.common.util.ZimbraLog;

import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.xml.parsers.*;

import org.w3c.dom.*;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

public class ZimletResources
extends ZimbraServlet {

    //
    // Constants
    //

    private static final String COMPRESSED_EXT = ".zgz";
    
    private static final String P_DEBUG = "debug";

    private static final String T_CSS = "css";
    private static final String T_JAVASCRIPT = "javascript";
    private static final String T_PLAIN = "plain";

    private static final boolean DEBUG = false;

    private static final Map<String,String> TYPES = new HashMap<String,String>();

    private static final Pattern RE_REMOTE = Pattern.compile("^((https?|ftps?)://|/)");
    private static final Pattern RE_CSS_URL = Pattern.compile("(url\\(['\"]?)([^'\"\\)]*)", Pattern.CASE_INSENSITIVE);

    static {
        TYPES.put("css", "text/css");
        TYPES.put("js", "text/javascript");
        TYPES.put("plain", "text/plain");
    }

    //
    // Data
    //

    /**
     * <ul>
     * <li>Key: request uri
     * <li>Value: String buffer
     * </ul>
     */
    private Map<String,byte[]> cache = new HashMap<String,byte[]>();

    //
    // HttpServlet methods
    //

    public void doGet(HttpServletRequest req, HttpServletResponse resp)
    throws IOException, ServletException {
        String uri = req.getRequestURI();
        String contentType = getContentType(uri);
        String type = contentType.replaceAll("^.*/", "");
        boolean debug = req.getParameter(P_DEBUG) != null;

        String cacheId = getCacheId(req, type);

        if (DEBUG) {
            System.err.println("DEBUG: uri="+uri);
            System.err.println("DEBUG: cacheId="+cacheId);
            System.err.println("DEBUG: contentType="+contentType);
            System.err.println("DEBUG: type="+type);
        }

        // generate buffer
        byte[] buffer = !debug ? cache.get(cacheId) : null;
        if (buffer == null) {
            StringWriter writer = new StringWriter();
            PrintWriter printer = new PrintWriter(writer);

            // zimlet messages
            if (type.equals(T_JAVASCRIPT)) {
                ServletConfig config = this.getServletConfig();
                ServletContext baseContext = config.getServletContext();
                ServletContext clientContext = baseContext.getContext("/zimbra/");
                RequestDispatcher dispatcher = clientContext.getRequestDispatcher("/js/msgs/");

                List<ZimletFile> files = getZimletFiles(req, type);
                for (ZimletFile file : files) {
                    String filename = file.getAbsolutePath();
                    if (!filename.startsWith("/msgs/")) {
                        continue;
                    }
                    HttpServletRequest wrappedReq = new RequestWrapper(req, filename);
                    HttpServletResponse wrappedResp = new ResponseWrapper(resp, printer);
                    dispatcher.include(wrappedReq, wrappedResp);
                }
            }

            // zimlet resources
            if (DEBUG) System.err.println("DEBUG: generating buffer");
            generate(req, type, printer);

            // minimize javascript
            String text = writer.toString();
            if (type.equals(T_JAVASCRIPT)) {
                Context context = Context.enter();
                context.setOptimizationLevel(-1);
                Scriptable scriptable = context.initStandardObjects();
                Reader reader = new StringReader(text);
                String script = null;
                String sourceName = uri;
                int lineNum = 0;
                Object securityDomain = null;

                String mintext = org.mozilla.javascript.tools.shell.Main.compressScript(
                    context, scriptable, reader,
                    script, sourceName, lineNum, securityDomain
                );
                if (mintext == null) {
                    System.err.println("unable to minimize zimlet JS source");
                }
                else {
                    text = mintext;
                }
            }

            // compress
            buffer = text.getBytes("UTF-8");
            if (uri.endsWith(COMPRESSED_EXT)) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(buffer.length);
                OutputStream gzos = new GZIPOutputStream(bos);
                gzos.write(buffer);
                gzos.close();
                buffer = bos.toByteArray();
            }

            // store buffer
            if (!debug) {
                cache.put(cacheId, buffer);
            }
        }
        else {
            if (DEBUG) System.err.println("DEBUG: using previous buffer");
        }

        // write buffer
        try {
            // We browser sniff so need to make sure any caches do the same.
            resp.addHeader("Vary","User-Agent");
            // Cache It!
            resp.setHeader("Cache-control", "public, max-age=604800");
            resp.setContentType(contentType);
            resp.setContentLength(buffer.length);
            if (uri.endsWith(COMPRESSED_EXT)) {
                resp.setHeader("Content-Encoding", "gzip");
            }
        }
        catch (IllegalStateException e) {
            // ignore -- thrown if called from including JSP
            System.err.println("zimletres: "+cacheId);
            System.err.println("zimletres: "+e.getMessage());
        }

        try {
            // print files
            OutputStream out = resp.getOutputStream();
            out.write(buffer);
            out.flush();
        }
        catch (IllegalStateException e) {
            System.err.println("!!! illegal state: "+e.getMessage());
            // use writer if called from including JSP
            PrintWriter out = resp.getWriter();
            out.print(buffer);
            out.flush();
        }

    } // doGet(HttpServletRequest,HttpServletResponse)
    
    //
    // Private methods
    //

    private void generate(HttpServletRequest req, String type, PrintWriter out)
    throws IOException {
        boolean isCSS = type.equals(T_CSS);

        String commentStart = "/* ";
        String commentContinue = " * ";
        String commentEnd = " */";

        // create data buffers
        List<ZimletFile> files = getZimletFiles(req, type);
        for (ZimletFile file : files) {
            String filename = file.getAbsolutePath();
            if (filename.startsWith("/msgs/")) {
                continue;
            }

            out.println(commentStart);
            out.print(commentContinue);
            out.print("File: ");
            // NOTE: Hide base directory but show which file is being included
            out.println(filename.replaceAll("^.*?/zimlet/", ""));
            out.println(commentEnd);
            out.println();

            // print file
            if (file.exists()) {
                printFile(out, file, file.zimletName, isCSS);
            }
            else {
                out.print(commentStart);
                out.print("Error: file doesn't exist");
                out.println(commentEnd);
            }
            out.println();
        }
        out.flush();

    } // generate(HttpServletRequest,String):String

    private void printFile(PrintWriter out, File file,
                           String zimletName, boolean isCSS)
    throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(file));
        String line;
        while ((line = in.readLine()) != null) {
            if (isCSS) {
                Matcher url = RE_CSS_URL.matcher(line);
                if (url.find()) {
                    int offset = 0;
                    do {
                        int start = url.start();
                        if (start > offset) {
                            out.print(line.substring(offset, start));
                        }

                        out.print(url.group(1));

                        String s = url.group(2);
                        Matcher remote = RE_REMOTE.matcher(s);
                        if (!remote.find()) {
                            out.print("/service/zimlet/"+zimletName+"/");
                        }
                        out.print(s);

                        offset = url.end();
                    } while (url.find());
                    if (offset < line.length()) {
                        out.println(line.substring(offset));
                    }
                    continue;
                }
            }
            out.println(line);
        }
        in.close();
    }

    private static String getContentType(String uri) {
        int index = uri.indexOf('.');
        String type = T_PLAIN;
        if (index != -1) {
            type = uri.substring(index + 1);
            index = type.indexOf('.');
            if (index != -1) {
                type = type.substring(0, index);
            }
        }
        String contentType = TYPES.get(type);
        return contentType != null ? contentType : TYPES.get(T_PLAIN);
    }

    private String getCacheId(HttpServletRequest req, String type) {
        List<String> zimletNames = (List<String>)req.getAttribute(ZimletFilter.ALLOWED_ZIMLETS);

        StringBuilder str = new StringBuilder();
        str.append(getLocale(req).toString());
        str.append(":");
        str.append(type);
        str.append(":");

        LinkedList<String> ids = new LinkedList<String>(zimletNames);
//        Collections.sort(ids);
        Iterator<String> iter = ids.iterator();
        for (int i = 0; iter.hasNext(); i++) {
            if (i > 0) {
                str.append(",");
            }
            str.append(iter.next());
        }

        return str.toString();
    }

    private static Locale getLocale(HttpServletRequest req) {
        String language = req.getParameter("language");
        if (language != null) {
            String country = req.getParameter("country");
            if (country != null) {
                String variant = req.getParameter("variant");
                if (variant != null) {
                    return new Locale(language, country, variant);
                }
                return new Locale(language, country);
            }
            return new Locale(language);
        }
        return req.getLocale();
    }

    private List<ZimletFile> getZimletFiles(HttpServletRequest req, String type) {
        List<ZimletFile> files = new LinkedList<ZimletFile>();
        List<String> zimletNames = (List<String>)req.getAttribute(ZimletFilter.ALLOWED_ZIMLETS);
        for (String zimletName : zimletNames) {
            // read zimlet manifest
            String dirname = getServletContext().getRealPath("/zimlet/"+zimletName);
            String manifest = dirname+"/"+zimletName+".xml";
            File file = new File(manifest);

            Document document = parseDocument(file, zimletName);
            if (document == null) {
                continue;
            }

            // add properties files
            boolean isJavaScript = type.equals(T_JAVASCRIPT);
            if (isJavaScript) {
                files.add(new ZimletFile(zimletName, "/msgs/"+zimletName));
            }

            // add included files
            Element root = document.getDocumentElement();
            NodeList nodes = root.getElementsByTagName(isJavaScript ? "include" : "includeCSS");
            int nodeCount = nodes.getLength();
            for (int i = 0; i < nodeCount; i++) {
                Node node = nodes.item(i);
                String filename = getText(node).trim();

                // only add local files to list
                if (RE_REMOTE.matcher(filename).matches()) {
                    continue;
                }
                files.add(new ZimletFile(zimletName, dirname+"/"+filename));
            }
        }
        return files;
    }

    private Document parseDocument(File file, String zimletName) {
        Document document = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(file);
        }
        catch (Exception e) {
            ZimbraLog.zimlet.info("error loading "+zimletName+" manifest: "+e.getMessage());
        }
        return document;
    }

    private static String getText(Node node) {
        StringBuilder str = new StringBuilder();
        Node child = node.getFirstChild();
        while (child != null) {
            str.append(child.getNodeValue());
            child = child.getNextSibling();
        }
        return str.toString();
    }

    //
    // Classes
    //

    static class ZimletFile extends File {
        public String zimletName;
        public ZimletFile(String zimletName, String filename) {
            super(filename);
            this.zimletName = zimletName;
        }
    }

    static class RequestWrapper extends HttpServletRequestWrapper {
        private String filename;
        public RequestWrapper(HttpServletRequest req, String filename) {
            super(req);
            this.filename = filename;
        }
        public String getRequestURI() {
            return "/js"+filename+".js";
        }
    }

    static class ResponseWrapper extends HttpServletResponseWrapper {
        private PrintWriter out;
        public ResponseWrapper(HttpServletResponse resp, PrintWriter out) {
            super(resp);
            this.out = out;
        }
        public void setHeader(String name, String value) { /* NOP */ }
        public void addHeader(String name, String value) { /* NOP */ }

        public ServletOutputStream getOutputStream() throws IOException {
            return new ServletStream(getWriter());
        }
        public PrintWriter getWriter() throws IOException {
            return out;
        }
    }

    static class ServletStream extends ServletOutputStream {
        private PrintWriter out;
        public ServletStream(PrintWriter out) {
            this.out = out;
        }
        public void write(int i) throws IOException { out.write(i); }
        public void print(boolean b) throws IOException { out.print(b); }
        public void print(char c) throws IOException { out.print(c); }
        public void print(float f) throws IOException { out.print(f); }
        public void print(int i) throws IOException { out.print(i); }
        public void print(long l) throws IOException { out.print(l); }
        public void print(String s) throws IOException { out.print(s); }
        public void println() throws IOException { out.println(); }
        public void println(boolean b) throws IOException { out.println(b); }
        public void println(char c) throws IOException { out.println(c); }
        public void println(float f) throws IOException { out.println(f); }
        public void println(int i) throws IOException { out.println(i); }
        public void println(long l) throws IOException { out.println(l); }
        public void println(String s) throws IOException { out.println(s); }
    }

} // class ZimletResources