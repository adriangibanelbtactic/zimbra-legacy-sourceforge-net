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

package com.zimbra.cs.ozserver;

import java.io.IOException;
import java.net.ServerSocket;

import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.util.NetUtil;
import com.zimbra.cs.util.Zimbra;

class TestServer {
    
    static Log mLog = LogFactory.getLog(TestServer.class);

    private static OzServer mServer;

    public TestServer(int port, boolean secure, boolean debugLogging) throws IOException, ServiceException {
        final boolean isSecure = secure;
        final boolean isDebugLogging = debugLogging;
        OzConnectionHandlerFactory testHandlerFactory = new OzConnectionHandlerFactory() {
            public OzConnectionHandler newConnectionHandler(OzConnection connection) {
                if (isSecure) {
                    connection.addFilter(new OzTLSFilter(connection, isDebugLogging, mLog));
                }
                return new TestConnectionHandler(connection);
            }
        };
        ServerSocket serverSocket = NetUtil.getOzServerSocket(null, port);
    	mServer = new OzServer("Test", 64, serverSocket, testHandlerFactory, debugLogging, mLog);
        mServer.start();
    }
    
    public static void main(String[] args) throws IOException, ServiceException {
        Zimbra.toolSetup("TRACE", null, true);
    	new TestServer(Integer.parseInt(args[0]), Boolean.parseBoolean(args[1]), true);
    }
    
    void shutdown() {
        mServer.shutdown();
    }
}
