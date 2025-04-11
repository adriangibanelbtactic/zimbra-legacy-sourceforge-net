/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1
 * 
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 ("License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.zimbra.com/license
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is: Zimbra Collaboration Suite Server.
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 */
package com.zimbra.cs.service.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.fileupload.MultipartStream;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.MultipartPostMethod;

import com.zimbra.cs.util.StringUtil;

/**
 * Sends a request over HTTP to a remote server and parses the multipart
 * MIME response.
 *  
 * @author bburtin
 */
public class RemoteServerRequest {

    private static byte[] BOUNDARY = "----------------314159265358979323846".getBytes();
    private static Pattern NAME_PATTERN = Pattern.compile("name=\"([^\"]+)\"");
    
    private Map mParams = new HashMap();
    private Map mFiles = new HashMap();
    private boolean mHasMoreParts = true;
    private MultipartStream mMulti = null;

    /**
     * Adds a string parameter to the request.
     */
    public void addParameter(String name, String value) {
        if (StringUtil.isNullOrEmpty(name)) {
            throw new IllegalArgumentException("name cannot be null or empty");
        }
        mParams.put(name, value);
    }

    /**
     * Adds a file parameter to the request.
     */
    public void addParameter(String name, File file) {
        if (StringUtil.isNullOrEmpty(name)) {
            throw new IllegalArgumentException("name cannot be null or empty");
        }
        if (file == null) {
            throw new IllegalArgumentException("file cannot be null");
        }
        mFiles.put(name, file);
    }

    /**
     * Sends the request to the remote server at the specified URL.
     * @throws IOException if the request was not successful
     */
    public void invoke(String url)
    throws IOException {
        // Assemble the request
        HttpClient http = new HttpClient();
        MultipartPostMethod post = new MultipartPostMethod(url);
        Iterator i = mParams.keySet().iterator();
        while (i.hasNext()) {
            String name = (String) i.next();
            String value = (String) mParams.get(name);
            post.addParameter(name, value);
        }
        i = mFiles.keySet().iterator();
        while (i.hasNext()) {
            String name = (String) i.next();
            File file = (File) mFiles.get(name);
            post.addParameter(name, file);
        }
        
        // Post data and handle errors
        int code = 0;
        code = http.executeMethod(post);
        if (code != 200) {
            throw new HttpException(
                "Remote server at '" + url + "' returned response " + code);
        }
        
        mMulti = new MultipartStream(post.getResponseBodyAsStream(), BOUNDARY);
        mHasMoreParts = mMulti.skipPreamble();
    }

    /**
     * Gets the next parameter name returned by the remote server.
     * @return the parameter name or <code>null</code> if there are no more parameters
     * @throws IOException if an error occurs while processing the response stream
     */
    public String getNextResponseParameter()
    throws IOException {
        if (!mHasMoreParts) {
            return null;
        }
        
        String headers = mMulti.readHeaders();
        Matcher matcher = NAME_PATTERN.matcher(headers);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * Gets the next parameter value returned by the remote server.
     * @return the parameter value
     * @throws IOException if an error occurs while processing the response stream
     */
    public String getNextResponseValue()
    throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mMulti.readBodyData(out);
        mHasMoreParts = mMulti.readBoundary();
        return new String(out.toString());
    }
    
    /**
     * Writes the next parameter value to an <code>OutputStream</code>.
     * @throws IOException if an error occurs while processing the response stream
     */
    public void getNextResponseValueAsStream(OutputStream out)
    throws IOException {
        mMulti.readBodyData(out);
        mHasMoreParts = mMulti.readBoundary();
    }
    
    /**
     * Returns a <code>Map</code> that contains all the response parameters
     * and values returned as <code>String</code>s.
     * @throws IOException if an error occurs while processing the response stream
     */
    public Map getResponseParameters()
    throws IOException {
        String name = null;
        Map params = new HashMap();
        while ((name = getNextResponseParameter()) != null) {
            String value = getNextResponseValue();
            params.put(name, value);
        }
        return params;
    }
}
