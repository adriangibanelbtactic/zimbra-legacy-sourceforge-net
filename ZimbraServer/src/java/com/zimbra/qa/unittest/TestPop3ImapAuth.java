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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.qa.unittest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.zimbra.common.util.DummySSLSocketFactory;
import com.zimbra.common.util.EasySSLProtocolSocketFactory;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;


public class TestPop3ImapAuth
extends TestCase {

    private static final String CRLF = "\r\n";
    private static final String HOSTNAME = "localhost";
    
    private static final String POP3_CONNECT_RESPONSE = "\\+OK .* POP3 server ready";
    private static final String POP3_USER = "USER user1" + CRLF;
    private static final String POP3_USER_RESPONSE = "\\+OK hello user1, please enter your password";
    private static final String POP3_PASS = "PASS test123" + CRLF;
    private static final String POP3_PASS_RESPONSE = "\\+OK server ready";
    private static final String POP3_STLS = "STLS" + CRLF;
    private static final String POP3_STLS_RESPONSE = "\\+OK Begin TLS negotiation";
    private static final String POP3_CLEARTEXT_FAILED_RESPONSE = "-ERR only valid after entering TLS mode";
    private static final String POP3_QUIT = "QUIT" + CRLF;
    private static final String POP3_QUIT_RESPONSE = "\\+OK .* closing connection";
    
    private static final String IMAP_CONNECT_RESPONSE = "\\* OK .* Zimbra IMAP4rev1 service ready";
    private static final String IMAP_LOGIN = "1 LOGIN user1 test123" + CRLF;
    private static final String IMAP_LOGIN_RESPONSE1 = "\\* CAPABILITY.*";
    private static final String IMAP_LOGIN_RESPONSE2 = "1 OK LOGIN completed";
    private static final String IMAP_CLEARTEXT_FAILED_RESPONSE = "1 NO cleartext logins disabled";
    private static final String IMAP_STARTTLS = "2 STARTTLS" + CRLF;
    private static final String IMAP_STARTTLS_RESPONSE = "2 OK Begin TLS negotiation now";
    private static final String IMAP_LOGOUT = "3 LOGOUT" + CRLF;
    private static final String IMAP_LOGOUT_RESPONSE1 = "\\* BYE Zimbra IMAP4rev1 server terminating connection";
    private static final String IMAP_LOGOUT_RESPONSE2 = "3 OK LOGOUT completed";
    
    private Provisioning mProv;
    private boolean mOrigPop3CleartextLoginEnabled;
    private boolean mOrigImapCleartextLoginEnabled;
    private int mPop3CleartextPort;
    private int mPop3SslPort;
    private int mImapCleartextPort;
    private int mImapSslPort;
    private Map<Socket, BufferedReader> mReaders = new HashMap<Socket, BufferedReader>();
    
    public void setUp()
    throws Exception {
        // Initialize SSL for SOAP provisioning
        EasySSLProtocolSocketFactory.init();
        
        mProv = Provisioning.getInstance();
        Server server = mProv.getLocalServer();
        mOrigPop3CleartextLoginEnabled = server.getBooleanAttr(Provisioning.A_zimbraPop3CleartextLoginEnabled, false);
        mOrigImapCleartextLoginEnabled = server.getBooleanAttr(Provisioning.A_zimbraImapCleartextLoginEnabled, false);
        mPop3CleartextPort = server.getIntAttr(Provisioning.A_zimbraPop3BindPort, 7110);
        mPop3SslPort = server.getIntAttr(Provisioning.A_zimbraPop3SSLBindPort, 7995);
        mImapCleartextPort = server.getIntAttr(Provisioning.A_zimbraImapBindPort, 7143);
        mImapSslPort = server.getIntAttr(Provisioning.A_zimbraImapSSLBindPort, 7995);
    }
    
    public void testPop3CleartextTrue()
    throws Exception {
        setPop3Cleartext(true);
        
        // Test cleartext
        Socket socket = new Socket(HOSTNAME, mPop3CleartextPort);
        send(socket, "", POP3_CONNECT_RESPONSE);
        send(socket, POP3_USER, POP3_USER_RESPONSE);
        send(socket, POP3_PASS, POP3_PASS_RESPONSE);
        send(socket, POP3_QUIT, POP3_QUIT_RESPONSE);
        socket.close();

        // Test SSL
        socket = DummySSLSocketFactory.getDefault().createSocket(HOSTNAME, mPop3SslPort);
        send(socket, "", POP3_CONNECT_RESPONSE);
        send(socket, POP3_USER, POP3_USER_RESPONSE);
        send(socket, POP3_PASS, POP3_PASS_RESPONSE);
        send(socket, POP3_QUIT, POP3_QUIT_RESPONSE);
        socket.close();
        
        // Test TLS
        socket = new Socket(HOSTNAME, mPop3CleartextPort);
        send(socket, "", POP3_CONNECT_RESPONSE);
        send(socket, POP3_STLS, POP3_STLS_RESPONSE);
        SSLSocketFactory factory = (SSLSocketFactory) DummySSLSocketFactory.getDefault();
        socket = factory.createSocket(socket, HOSTNAME, mPop3CleartextPort, true);
        send(socket, POP3_USER, POP3_USER_RESPONSE);
        send(socket, POP3_PASS, POP3_PASS_RESPONSE);
        send(socket, POP3_QUIT, POP3_QUIT_RESPONSE);
    }

    public void testPop3CleartextFalse()
    throws Exception {
        setPop3Cleartext(false);

        // Test cleartext
        Socket socket = new Socket(HOSTNAME, mPop3CleartextPort);
        send(socket, null, POP3_CONNECT_RESPONSE);
        send(socket, POP3_USER, POP3_CLEARTEXT_FAILED_RESPONSE);
        send(socket, POP3_QUIT, POP3_QUIT_RESPONSE);
        socket.close();

        // Test SSL
        socket = DummySSLSocketFactory.getDefault().createSocket(HOSTNAME, mPop3SslPort);
        send(socket, null, POP3_CONNECT_RESPONSE);
        send(socket, POP3_USER, POP3_USER_RESPONSE);
        send(socket, POP3_PASS, POP3_PASS_RESPONSE);
        send(socket, POP3_QUIT, POP3_QUIT_RESPONSE);
        socket.close();
        
        // Test TLS
        socket = new Socket(HOSTNAME, mPop3CleartextPort);
        send(socket, null, POP3_CONNECT_RESPONSE);
        send(socket, POP3_STLS, POP3_STLS_RESPONSE);
        SSLSocketFactory factory = (SSLSocketFactory) DummySSLSocketFactory.getDefault();
        socket = factory.createSocket(socket, HOSTNAME, mPop3CleartextPort, true);
        send(socket, POP3_USER, POP3_USER_RESPONSE);
        send(socket, POP3_PASS, POP3_PASS_RESPONSE);
        send(socket, POP3_QUIT, POP3_QUIT_RESPONSE);
    }

    public void testImapCleartextTrue()
    throws Exception {
        setImapCleartext(true);
        
        // Test cleartext
        Socket socket = new Socket(HOSTNAME, mImapCleartextPort);
        send(socket, null, IMAP_CONNECT_RESPONSE);
        send(socket, IMAP_LOGIN, IMAP_LOGIN_RESPONSE1);
        send(socket, null, IMAP_LOGIN_RESPONSE2);
        send(socket, IMAP_LOGOUT, IMAP_LOGOUT_RESPONSE1);
        send(socket, null, IMAP_LOGOUT_RESPONSE2);
        
        // Test SSL
        socket = DummySSLSocketFactory.getDefault().createSocket(HOSTNAME, mImapSslPort);
        send(socket, null, IMAP_CONNECT_RESPONSE);
        send(socket, IMAP_LOGIN, IMAP_LOGIN_RESPONSE1);
        send(socket, null, IMAP_LOGIN_RESPONSE2);
        send(socket, IMAP_LOGOUT, IMAP_LOGOUT_RESPONSE1);
        send(socket, null, IMAP_LOGOUT_RESPONSE2);
        
        // Test TLS
        socket = new Socket(HOSTNAME, mImapCleartextPort);
        send(socket, null, IMAP_CONNECT_RESPONSE);
        send(socket, IMAP_STARTTLS, IMAP_STARTTLS_RESPONSE);
        SSLSocketFactory factory = (SSLSocketFactory) DummySSLSocketFactory.getDefault();
        socket = factory.createSocket(socket, HOSTNAME, mImapCleartextPort, true);
        send(socket, IMAP_LOGIN, IMAP_LOGIN_RESPONSE1);
        send(socket, null, IMAP_LOGIN_RESPONSE2);
        send(socket, IMAP_LOGOUT, IMAP_LOGOUT_RESPONSE1);
        send(socket, null, IMAP_LOGOUT_RESPONSE2);
    }
    
    public void testImapCleartextFalse()
    throws Exception {
        setImapCleartext(false);
        
        // Test cleartext
        Socket socket = new Socket(HOSTNAME, mImapCleartextPort);
        send(socket, null, IMAP_CONNECT_RESPONSE);
        send(socket, IMAP_LOGIN, IMAP_CLEARTEXT_FAILED_RESPONSE);
        send(socket, IMAP_LOGOUT, IMAP_LOGOUT_RESPONSE1);
        send(socket, null, IMAP_LOGOUT_RESPONSE2);
        
        // Test SSL
        socket = DummySSLSocketFactory.getDefault().createSocket(HOSTNAME, mImapSslPort);
        send(socket, null, IMAP_CONNECT_RESPONSE);
        send(socket, IMAP_LOGIN, IMAP_LOGIN_RESPONSE1);
        send(socket, null, IMAP_LOGIN_RESPONSE2);
        send(socket, IMAP_LOGOUT, IMAP_LOGOUT_RESPONSE1);
        send(socket, null, IMAP_LOGOUT_RESPONSE2);
        
        // Test TLS
        socket = new Socket(HOSTNAME, mImapCleartextPort);
        send(socket, null, IMAP_CONNECT_RESPONSE);
        send(socket, IMAP_STARTTLS, IMAP_STARTTLS_RESPONSE);
        SSLSocketFactory factory = (SSLSocketFactory) DummySSLSocketFactory.getDefault();
        socket = factory.createSocket(socket, HOSTNAME, mImapCleartextPort, true);
        send(socket, IMAP_LOGIN, IMAP_LOGIN_RESPONSE1);
        send(socket, null, IMAP_LOGIN_RESPONSE2);
        send(socket, IMAP_LOGOUT, IMAP_LOGOUT_RESPONSE1);
        send(socket, null, IMAP_LOGOUT_RESPONSE2);
    }
    
    public void tearDown()
    throws Exception {
        setPop3Cleartext(mOrigPop3CleartextLoginEnabled);
        setImapCleartext(mOrigImapCleartextLoginEnabled);
    }
    
    /**
     * Sends the given message to the socket's <code>OutputStream</code> and
     * validates the first line returned.
     * 
     * @param socket the socket
     * @param msg the message to send, or <code>null</code> to just read the next line
     * @param responsePattern the regexp pattern that the response should match 
     */
    private void send(Socket socket, String msg, String responsePattern)
    throws Exception {
        if (msg != null) {
            OutputStream out = socket.getOutputStream();
            out.write(msg.getBytes());
            out.flush();
        }

        // Get the reader associated with the socket.  We can't ever create
        // two readers for the same socket, since BufferedReader may read
        // past the current line.
        BufferedReader reader = mReaders.get(socket);
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            mReaders.put(socket, reader);
        }
            
        String response = reader.readLine();
        String errorMsg = "Unexpected response: '" + response + "'";
        assertTrue(errorMsg, response.matches(responsePattern));
    }
    
    private void setPop3Cleartext(boolean value)
    throws Exception {
        setServerAttr(Provisioning.A_zimbraPop3CleartextLoginEnabled, value);
    }
    
    private void setImapCleartext(boolean value)
    throws Exception {
        setServerAttr(Provisioning.A_zimbraImapCleartextLoginEnabled, value);
    }
    
    private void setServerAttr(String attrName, boolean value)
    throws Exception {
        String val = value ? Provisioning.TRUE : Provisioning.FALSE;
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(attrName, val);
        mProv.modifyAttrs(mProv.getLocalServer(), attrs);
    }
    
    public static void main(String[] args)
    throws Exception {
        TestUtil.cliSetup();
        TestUtil.runTest(new TestSuite(TestPop3ImapAuth.class));        
    }
}
