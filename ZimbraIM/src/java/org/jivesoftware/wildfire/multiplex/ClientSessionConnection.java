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
 * Portions created by Zimbra are Copyright (C) 2006, 2007 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */
package org.jivesoftware.wildfire.multiplex;

import org.dom4j.Element;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.net.VirtualConnection;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

import java.net.InetAddress;

/**
 * Represents a connection of a Client Session that was established to a Connection Manager.
 * Connection Managers have their own physical connections to the server that are multiplexed
 * among connected clients. Each created {@link org.jivesoftware.wildfire.ClientSession} will
 * use an instance of this class as its connection.
 *
 * @author Gaston Dombiak
 */
public class ClientSessionConnection extends VirtualConnection {

    private String connectionManagerName;
    private String serverName;
    private ConnectionMultiplexerManager multiplexerManager;

    public ClientSessionConnection(String connectionManagerName) {
        this.connectionManagerName = connectionManagerName;
        multiplexerManager = ConnectionMultiplexerManager.getInstance();
        serverName = XMPPServer.getInstance().getServerInfo().getName();
    }

    /**
     * Delivers the packet to the Connection Manager that in turn will forward it to the
     * target user. Connection Managers may have one or many connections to the server so
     * just get any connection to the Connection Manager (uses a random) and use it.<p>
     *
     * If the packet to send does not have a TO attribute then wrap the packet with a
     * special IQ packet. The wrapper IQ packet will be sent to the Connection Manager
     * and the stream ID of this Client Session will be used for identifying that the wrapped
     * packet must be sent to the connected user. Since some packets can be exchanged before
     * the user has a binded JID we need to use the stream ID as the unique identifier.
     *
     * @param packet the packet to send to the user.
     */
    public void deliver(Packet packet) {
        ConnectionMultiplexerSession multiplexerSession =
                multiplexerManager.getMultiplexerSession(connectionManagerName);
        if (multiplexerSession != null) {
            // Wrap packet so that the connection manager can figure out the target session
            Route wrapper = new Route(session.getStreamID().getID());
            wrapper.setFrom(serverName);
            wrapper.setTo(connectionManagerName);
            wrapper.setChildElement(packet.getElement().createCopy());
            // Deliver wrapper
            multiplexerSession.deliver(wrapper);
            session.incrementServerPacketCount();
        }
    }

    /**
     * Delivers the stanza to the Connection Manager that in turn will forward it to the
     * target user. Connection Managers may have one or many connections to the server so
     * just get any connection to the Connection Manager (uses a random) and use it.<p>
     *
     * The stanza to send wrapped with a special IQ packet. The wrapper IQ packet will be
     * sent to the Connection Manager and the stream ID of this Client Session will be used
     * for identifying that the wrapped stanza must be sent to the connected user.
     *
     * @param text the stanza to send to the user.
     */
    public void deliverRawText(String text) {
        ConnectionMultiplexerSession multiplexerSession =
                multiplexerManager.getMultiplexerSession(connectionManagerName);
        if (multiplexerSession != null) {
            // Wrap packet so that the connection manager can figure out the target session
            StringBuilder sb = new StringBuilder(200 + text.length());
            sb.append("<route from=\"").append(serverName);
            sb.append("\" to=\"").append(connectionManagerName);
            sb.append("\" streamid=\"").append(session.getStreamID().getID()).append("\">");
            sb.append(text);
            sb.append("</route>");
            // Deliver the wrapped stanza
            multiplexerSession.getConnection().deliverRawText(sb.toString());
        }
    }

    public InetAddress getInetAddress() {
        //TODO Future version may return actual IP client address. We would need to pass this info
        // Return IP address of the connection manager that the client used to log in
        ConnectionMultiplexerSession multiplexerSession =
                multiplexerManager.getMultiplexerSession(connectionManagerName);
        if (multiplexerSession != null) {
            return multiplexerSession.getConnection().getInetAddress();
        }
        return null;
    }

    public void systemShutdown() {
        // Do nothing since a system-shutdown error will be sent to the Connection Manager
        // that in turn will send a system-shutdown to connected clients. This is an
        // optimization to reduce number of packets being sent from the server.
    }

    /**
     * If the Connection Manager or the Client requested to close the connection then just do
     * nothing. But if the server originated the request to close the connection then we need
     * to send to the Connection Manager a packet letting him know that the Client Session needs
     * to be terminated.
     */
    public void closeVirtualConnection() {
        // Figure out who requested the connection to be closed
        String streamID = session.getStreamID().getID();
        if (multiplexerManager.getClientSession(connectionManagerName, streamID) == null) {
            // Client or Connection manager requested to close the session
            // Do nothing since it has already been removed and closed
        }
        else {
            ConnectionMultiplexerSession multiplexerSession =
                    multiplexerManager.getMultiplexerSession(connectionManagerName);
            if (multiplexerSession != null) {
                // Server requested to close the client session so let the connection manager
                // know that he has to finish the client session
                IQ closeRequest = new IQ(IQ.Type.set);
                closeRequest.setFrom(serverName);
                closeRequest.setTo(connectionManagerName);
                Element child = closeRequest.setChildElement("session",
                        "http://jabber.org/protocol/connectionmanager");
                child.addAttribute("id", streamID);
                child.addElement("close");
                multiplexerSession.deliver(closeRequest);
            }
        }
    }
}
