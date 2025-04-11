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
 * Portions created by Zimbra are Copyright (C) 2004, 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * SoapHttpTransport.java
 */

package com.zimbra.common.soap;

import java.io.IOException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpRecoverableException;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpMethodParams;

import com.zimbra.common.soap.SoapProtocol;

/**
 */

public class SoapHttpTransport extends SoapTransport {

    private boolean mKeepAlive;
    private int mRetryCount;
    private int mTimeout;
    private String mUri;
	private HttpClient mClient;
    
    public String toString() { 
        return "SoapHTTPTransport(uri="+mUri+")";
    }

    private static final HttpClientParams sDefaultParams = new HttpClientParams();
        static {
            // we're doing the retry logic at the SoapHttpTransport level, so don't do it at the HttpClient level as well
            sDefaultParams.setParameter(HttpMethodParams.RETRY_HANDLER, new HttpMethodRetryHandler() {
                public boolean retryMethod(HttpMethod method, IOException exception, int executionCount)  { return false; }
            });
        }

    /**
     * Create a new SoapHttpTransport object for the specified URI.
     * Supported schemes are http and https. The connection
     * is not made until invoke or connect is called.
     *
     * Multiple threads using this transport must do their own
     * synchronization.
     */
    public SoapHttpTransport(String uri) {
    	super();
    	mClient = new HttpClient(sDefaultParams);
    	commonInit(uri);
    }

    /**
     * Creates a new SoapHttpTransport that supports multiple connections
     * to the specified URI.  Multiple threads can call the invoke()
     * method safely without synchronization.
     *
     * @param uri
     * @param maxConnections Note RFC2616 recommends the default of 2.
     */
    public SoapHttpTransport(String uri, int maxConnections, boolean connectionStaleCheckEnabled) {
    	super();
    	MultiThreadedHttpConnectionManager connMgr = new MultiThreadedHttpConnectionManager();
    	connMgr.setMaxConnectionsPerHost(maxConnections);
    	connMgr.setConnectionStaleCheckingEnabled(connectionStaleCheckEnabled);
    	mClient = new HttpClient(sDefaultParams, connMgr);
    	commonInit(uri);
    }

    /**
     * Frees any resources such as connection pool held by this transport.
     */
    public void shutdown() {
    	HttpConnectionManager connMgr = mClient.getHttpConnectionManager();
    	if (connMgr instanceof MultiThreadedHttpConnectionManager) {
    		MultiThreadedHttpConnectionManager multiConnMgr = (MultiThreadedHttpConnectionManager) connMgr;
    		multiConnMgr.shutdown();
    	}
    	mClient = null;
    }

    private void commonInit(String uri) {
        mUri = uri;
        mKeepAlive = false;
        mRetryCount = 3;
        setTimeout(0);
    }

    /**
     *  Gets the URI
     */
    public String getURI() {
        return mUri;
    }
    
    /**
     * The number of times the invoke method retries when it catches a 
     * RetryableIOException.
     *
     * <p> Default value is <code>3</code>.
     */
    public void setRetryCount(int retryCount) {
        this.mRetryCount = retryCount;
    }


    /**
     * Get the mRetryCount value.
     */
    public int getRetryCount() {
        return mRetryCount;
    }

    /**
     * Whether or not to keep the connection alive in between
     * invoke calls.
     *
     * <p> Default value is <code>false</code>.
     */
    private void setKeepAlive(boolean keepAlive) {
        this.mKeepAlive = keepAlive;
    }

    /**
     * Get the mKeepAlive value.
     */
    private boolean getKeepAlive() {
        return mKeepAlive;
    }

    /**
     * The number of miliseconds to wait when connecting or reading
     * during a invoke call. 
     * <p>
     * Default value is <code>0</code>, which means no mTimeout.
     */
    public void setTimeout(int timeout) {
        mTimeout = timeout;
        mClient.setConnectionTimeout(mTimeout);
        mClient.setTimeout(mTimeout);
    }

    /**
     * Get the mTimeout value.
     */
    public int getTimeout() {
        return mTimeout;
    }

    public Element invoke(Element document, boolean raw, boolean noSession, boolean noNotify, String requestedAccountId) 
	throws SoapFaultException, IOException, HttpException {
    	int statusCode = -1;

    	// the content-type charset will determine encoding used
    	// when we set the request body
    	PostMethod method = new PostMethod(mUri);
    	method.setRequestHeader("Content-Type", getSoapProtocol().getContentType());
    	String soapMessage = generateSoapMessage(document, raw, noSession, noNotify, requestedAccountId);
    	method.setRequestBody(soapMessage);
    	method.setRequestContentLength(EntityEnclosingMethod.CONTENT_LENGTH_AUTO);
    	
    	if (getSoapProtocol().hasSOAPActionHeader())
    		method.setRequestHeader("SOAPAction", mUri);

    	for (int attempt = 0; statusCode == -1 && attempt < mRetryCount; attempt++) {
    		try {
    			// execute the method.
    			statusCode = mClient.executeMethod(method);
    		} catch (HttpRecoverableException e) {
                if (attempt == mRetryCount - 1)
                    throw e;
    			System.err.println("A recoverable exception occurred, retrying." + e.getMessage());
    		}
    	}

    	// Read the response body.
    	byte[] responseBody = method.getResponseBody();

    	// Release the connection.
    	method.releaseConnection();

    	// Deal with the response.
    	// Use caution: ensure correct character encoding and is not binary data
    	String responseStr = SoapProtocol.toString(responseBody);

    	return parseSoapResponse(responseStr, raw);
    }    

}

/*
 * TODOs:
 * retry?
 * validation
 */
