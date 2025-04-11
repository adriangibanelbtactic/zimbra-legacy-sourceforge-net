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
 * Portions created by Zimbra are Copyright (C) 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on 2005. 4. 27.
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.zimbra.cs.httpclient;

import java.util.Iterator;
import java.util.List;

import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;

import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.servlet.ZimbraServlet;

/**
 * @author jhahm
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class URLUtil {

    private static Log mLog = LogFactory.getLog(URLUtil.class);

    private static final String SCHEME_HTTP  = "http://";
    private static final String SCHEME_HTTPS = "https://";
    
    private static int DEFAULT_HTTP_PORT = 80;
    private static int DEFAULT_HTTPS_PORT = 443;
    

    /**
     * Return the URL where SOAP service is available for given store server.
     * 
     * @see getMailURL()
     */
    public static String getSoapURL(Server server, boolean preferSSL) throws ServiceException {
        return getMailURL(server, ZimbraServlet.USER_SERVICE_URI, preferSSL);  
    }
    
    /**
     * Returns absolute URL with scheme, host, and port for mail app on server.
     * 
     * @param server
     * @param path what follows port number; begins with slash
     * @param preferSSL if both SSL and and non-SSL are available, whether to prefer SSL 
     * @return desired URL
     */
    public static String getMailURL(Server server, String path, boolean preferSSL) throws ServiceException {
        String hostname = server.getAttr(Provisioning.A_zimbraServiceHostname);
        if (hostname == null) {
            throw ServiceException.INVALID_REQUEST("server " + server.getName() + " does not have " + Provisioning.A_zimbraServiceHostname, null);
        }
        
        String modeString = server.getAttr(Provisioning.A_zimbraMailMode, null);
        if (modeString == null) {
            throw ServiceException.INVALID_REQUEST("server " + server.getName() + " does not have " + Provisioning.A_zimbraMailMode + " set, maybe it is not a store server?", null);
        }
        
        Provisioning.MAIL_MODE mode;
        try {
            mode = Provisioning.MAIL_MODE.valueOf(modeString);
        } catch (IllegalArgumentException iae) {
            throw ServiceException.INVALID_REQUEST("server " + server.getName() + " has invalid " + Provisioning.A_zimbraMailMode + ": " + modeString, iae);
        }
        
        boolean ssl;
        boolean printPort = true;
        
        switch (mode) {
        case both:
        case mixed:
	case redirect:
            ssl = preferSSL;
            break;
        case https:
            ssl = true;
            break;
        case http:
            ssl = false;
            break;
        default:
            throw ServiceException.INVALID_REQUEST("server " + server.getName() + " has unknown " + Provisioning.A_zimbraMailMode + ": " + mode, null);
        }
        
        String scheme;
        int port = 0;

        if (ssl) {
            scheme = SCHEME_HTTPS;
            port = server.getIntAttr(Provisioning.A_zimbraMailSSLPort, 0);
            if (port < 1) {
                throw ServiceException.INVALID_REQUEST("server " + server.getName() + " has invalid " + Provisioning.A_zimbraMailSSLPort + ": " + port, null);
            }
            if (port == DEFAULT_HTTPS_PORT)
            	printPort = false;
        } else {
            scheme = SCHEME_HTTP;
            port = server.getIntAttr(Provisioning.A_zimbraMailPort, 0);
            if (port < 1) {
                throw ServiceException.INVALID_REQUEST("server " + server.getName() + " has invalid " + Provisioning.A_zimbraMailPort + ": " + port, null);
            }
            if (port == DEFAULT_HTTP_PORT)
            	printPort = false;
        }

        StringBuffer sb = new StringBuffer(128);
        sb.append(scheme).append(hostname);
        if (printPort)
        	sb.append(":").append(port);
        sb.append(path);
        return sb.toString();
    }

    /**
     * Returns absolute URL with scheme, host, and port for admin app on server.
     * Admin app only runs over SSL.
     * @param server
     * @param path what follows port number; begins with slash
     * @return
     */
    public static String getAdminURL(Server server, String path) {
        String hostname = server.getAttr(Provisioning.A_zimbraServiceHostname);
        int port = server.getIntAttr(Provisioning.A_zimbraAdminPort, 0);
        StringBuffer sb = new StringBuffer(128);
        sb.append(LC.zimbra_admin_service_scheme.value()).append(hostname).append(":").append(port).append(path);
        return sb.toString();
    }


    /**
     * Returns absolute URL with scheme, host, and port for admin app on server.
     * Admin app only runs over SSL. Uses port from localconfig.
     * @param server hostname
     * @return
     */
    public static String getAdminURL(String hostname) {
        int port = (int) LC.zimbra_admin_service_port.longValue();
        StringBuffer sb = new StringBuffer(128);
        sb.append(LC.zimbra_admin_service_scheme.value()).append(hostname).append(":").append(port).append(ZimbraServlet.ADMIN_SERVICE_URI);
        return sb.toString();
    }
    
    /**
     * Returns absolute URL with scheme, host, and port for admin app on server.
     * Admin app only runs over SSL.
     * @param server
     * @param path what follows port number; begins with slash
     * @return
     */
    public static String getAdminURL(Server server) {
        return getAdminURL(server, ZimbraServlet.ADMIN_SERVICE_URI);
    }
    
    /**
     * Utility method to translate zimbraMtaAuthHost -> zimbraMtaAuthURL.
     * 
     * Not the best place for this method, but do not want to pollute
     * Provisioning with utility methods either.
     */
    public static String getMtaAuthURL(String authHost) throws ServiceException {
        List servers = Provisioning.getInstance().getAllServers();
        for (Iterator it = servers.iterator(); it.hasNext();) {
            Server server = (Server) it.next();
            String serviceName = server.getAttr(Provisioning.A_zimbraServiceHostname, null);
            if (authHost.equalsIgnoreCase(serviceName)) {
                return URLUtil.getSoapURL(server, true);
            }
        }
        throw ServiceException.INVALID_REQUEST("specified " + Provisioning.A_zimbraMtaAuthHost + " does not correspond to a valid service hostname: " + authHost, null);
    }

}
