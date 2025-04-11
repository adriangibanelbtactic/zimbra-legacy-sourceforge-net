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

package com.zimbra.cs.pop3;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;

import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.CliUtil;
import com.zimbra.cs.stats.RealtimeStatsCallback;
import com.zimbra.cs.stats.ZimbraPerf;
import com.zimbra.cs.tcpserver.ProtocolHandler;
import com.zimbra.cs.tcpserver.TcpServer;
import com.zimbra.cs.util.Config;
import com.zimbra.cs.util.NetUtil;

public class Pop3Server extends TcpServer
implements RealtimeStatsCallback {

    private static final int D_POP3_THREADS = 10;
    private static final String D_POP3_BIND_ADDRESS = null;
    private static final String D_POP3_ANNOUNCE_NAME = null;

    private static Pop3Server sPopServer;
    private static Pop3Server sPopSSLServer;

    private boolean mConnectionSSL;

    boolean allowCleartextLogins()
    throws ServiceException {
        Server server = Provisioning.getInstance().getLocalServer();
        return server.getBooleanAttr(Provisioning.A_zimbraPop3CleartextLoginEnabled, false);
    }

    boolean isConnectionSSL()       { return mConnectionSSL; }

    public Pop3Server(int numThreads, ServerSocket serverSocket, boolean ssl) {
        super("Pop3Server", numThreads, serverSocket);
        mConnectionSSL = ssl;
        ZimbraPerf.addStatsCallback(this);
    }

    protected ProtocolHandler newProtocolHandler() {
        return new Pop3Handler(this);
    }

    public void run() {
        /* Check is for initial sanity - you can always shoot yourself later by setting these to null. */
        //if (getConfigName() == null) throw new IllegalStateException("Call LmtpServer.setConfigName() first");
        //if (getConfigBackend() == null) throw new IllegalStateException("Call LmtpServer.setConfigBackend() first");
        super.run();
    }

    // TODO actually get it from configuration!

    /*
      * Config idle. should be at least 10 minutes, per POP3 RFC 1939.
      */
    public static final int DEFAULT_MAX_IDLE_SECONDS = 600;

    private int mConfigMaxIdleMilliSeconds = DEFAULT_MAX_IDLE_SECONDS * 1000;

    public void setConfigMaxIdleSeconds(int configMaxIdleSeconds) {
        mConfigMaxIdleMilliSeconds = configMaxIdleSeconds * 1000;
    }

    public int getConfigMaxIdleSeconds() {
        return mConfigMaxIdleMilliSeconds / 1000;
    }

    public int getConfigMaxIdleMilliSeconds() {
        return mConfigMaxIdleMilliSeconds;
    }

    /*
      * Config name.
      */
    private String mConfigName;

    public void setConfigNameFromHostname() {
        setConfigName(LC.zimbra_server_hostname.value());
    }

    public void setConfigName(String name) {
        mConfigName = name;
        mBanner = new String(name + " Zimbra POP3 server ready");
        mGoodbye = new String(name + " closing connection");
    }

    public String getConfigName() {
        return mConfigName;
    }

    /*
      * This falls out of the configuration, so stick it here.
      */
    private String mBanner;

    public String getBanner() {
        return mBanner;
    }

    private String mGoodbye;

    public String getGoodbye() {
        return mGoodbye;
    }

    public synchronized static void startupPop3Server() throws ServiceException {
        if (sPopServer != null)
            return;

        Server server = Provisioning.getInstance().getLocalServer();
        String address = server.getAttr(Provisioning.A_zimbraPop3BindAddress, D_POP3_BIND_ADDRESS);
        int port = server.getIntAttr(Provisioning.A_zimbraPop3BindPort, Config.D_POP3_BIND_PORT);
        int numThreads = server.getIntAttr(Provisioning.A_zimbraPop3NumThreads, D_POP3_THREADS);

        ServerSocket serverSocket = NetUtil.getTcpServerSocket(address, port);

        sPopServer = new Pop3Server(numThreads, serverSocket, false);

        String advName = server.getAttr(Provisioning.A_zimbraPop3AdvertisedName, D_POP3_ANNOUNCE_NAME);
        if (advName == null) {
            sPopServer.setConfigNameFromHostname();
        } else {
            sPopServer.setConfigName(advName);
        }

        Thread pop3Thread = new Thread(sPopServer);
        pop3Thread.setName("Pop3Server");
        pop3Thread.start();
    }

    public synchronized static void startupPop3SSLServer() throws ServiceException {
        if (sPopSSLServer != null)
            return;

        Server server = Provisioning.getInstance().getLocalServer();
        String address = server.getAttr(Provisioning.A_zimbraPop3SSLBindAddress, D_POP3_BIND_ADDRESS);
        int port = server.getIntAttr(Provisioning.A_zimbraPop3SSLBindPort, Config.D_POP3_SSL_BIND_PORT);
        int numThreads = server.getIntAttr(Provisioning.A_zimbraPop3NumThreads, D_POP3_THREADS);

        ServerSocket serverSocket = NetUtil.getSslTcpServerSocket(address, port);

        sPopSSLServer = new Pop3Server(numThreads, serverSocket, true);

        sPopSSLServer.setSSL(true);

        String advName = server.getAttr(Provisioning.A_zimbraPop3AdvertisedName, D_POP3_ANNOUNCE_NAME);
        if (advName == null) {
            sPopSSLServer.setConfigNameFromHostname();
        } else {
            sPopSSLServer.setConfigName(advName);
        }

        Thread pop3Thread = new Thread(sPopSSLServer);
        pop3Thread.setName("Pop3SSLServer");
        pop3Thread.start();
    }

    public synchronized static void shutdownPop3Servers() {
        if (sPopServer != null)
            sPopServer.shutdown(10); // TODO shutdown grace period from config
        sPopServer = null;

        if (sPopSSLServer != null)
            sPopSSLServer.shutdown(10); // TODO shutdown grace period from config
        sPopSSLServer = null;
    }

    /**
     * Implementation of <code>RealtimeStatsCallback</code> that returns the number
     * of active handlers for this server.
     */
    public Map<String, Object> getStatData() {
        Map<String, Object> data = new HashMap<String, Object>();
        String statName = mConnectionSSL ? ZimbraPerf.RTS_POP_SSL_CONN : ZimbraPerf.RTS_POP_CONN;
        data.put(statName, numActiveHandlers());
        return data;
    }

    public static void main(String args[]) throws ServiceException {
        CliUtil.toolSetup();
        startupPop3Server();
    }
}
