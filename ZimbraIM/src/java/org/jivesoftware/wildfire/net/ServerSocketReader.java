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
package org.jivesoftware.wildfire.net;

import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.PacketRouter;
import org.jivesoftware.wildfire.RoutingTable;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.interceptor.PacketRejectedException;
import org.jivesoftware.wildfire.server.IncomingServerSession;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.*;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A SocketReader specialized for server connections. This reader will be used when the open
 * stream contains a jabber:server namespace. Server-to-server communication requires two
 * TCP connections between the servers where one is used for sending packets whilst the other
 * connection is used for receiving packets. The connection used for receiving packets will use
 * a ServerSocketReader since the other connection will not receive packets.<p>
 *
 * The received packets will be routed using another thread to ensure that many received packets
 * could be routed at the same time. To avoid creating new threads every time a packet is received
 * each <tt>ServerSocketReader</tt> instance uses a {@link ThreadPoolExecutor}. By default the
 * maximum number of threads that the executor may have is 50. However, this value may be modified
 * by changing the property <b>xmpp.server.processing.max.threads</b>.
 *
 * @author Gaston Dombiak
 */
public class ServerSocketReader extends SocketReader {

    /**
     * Pool of threads that are available for processing the requests.
     */
    private ThreadPoolExecutor threadPool;

    public ServerSocketReader(PacketRouter router, RoutingTable routingTable, String serverName,
            Socket socket, SocketConnection connection, boolean useBlockingMode) {
        super(router, routingTable, serverName, socket, connection, useBlockingMode);
        // Create a pool of threads that will process received packets. If more threads are
        // required then the command will be executed on the SocketReader process
        int coreThreads = JiveGlobals.getIntProperty("xmpp.server.processing.core.threads", 2);
        int maxThreads = JiveGlobals.getIntProperty("xmpp.server.processing.max.threads", 50);
        int queueSize = JiveGlobals.getIntProperty("xmpp.server.processing.queue", 50);
        threadPool =
                new ThreadPoolExecutor(coreThreads, maxThreads, 60, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<Runnable>(queueSize),
                        new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Processes the packet in another thread if the packet has not been rejected.
     *
     * @param packet the received packet.
     */
    protected void processIQ(final IQ packet) throws UnauthorizedException {
        try {
            packetReceived(packet);
            // Process the packet in another thread
            threadPool.execute(new Runnable() {
                public void run() {
                    try {
                        ServerSocketReader.super.processIQ(packet);
                    }
                    catch (UnauthorizedException e) {
                        Log.error("Error processing packet", e);
                    }
                }
            });
        }
        catch (PacketRejectedException e) {
            Log.debug("IQ rejected: " + packet.toXML(), e);
        }
    }

    /**
     * Processes the packet in another thread if the packet has not been rejected.
     *
     * @param packet the received packet.
     */
    protected void processPresence(final Presence packet) throws UnauthorizedException {
        try {
            packetReceived(packet);
            // Process the packet in another thread
            threadPool.execute(new Runnable() {
                public void run() {
                    try {
                        ServerSocketReader.super.processPresence(packet);
                    }
                    catch (UnauthorizedException e) {
                        Log.error("Error processing packet", e);
                    }
                }
            });
        }
        catch (PacketRejectedException e) {
            Log.debug("Presence rejected: " + packet.toXML(), e);
        }
    }

    /**
     * Processes the packet in another thread if the packet has not been rejected.
     *
     * @param packet the received packet.
     */
    protected void processMessage(final Message packet) throws UnauthorizedException {
        try {
            packetReceived(packet);
            // Process the packet in another thread
            threadPool.execute(new Runnable() {
                public void run() {
                    try {
                        ServerSocketReader.super.processMessage(packet);
                    }
                    catch (UnauthorizedException e) {
                        Log.error("Error processing packet", e);
                    }
                }
            });
        }
        catch (PacketRejectedException e) {
            Log.debug("Message rejected: " + packet.toXML(), e);
        }
    }

    /**
     * Remote servers may send subsequent db:result packets so we need to process them in order
     * to validate new domains.
     *
     * @param doc the unknown DOM element that was received
     * @return true if the packet is a db:result packet otherwise false.
     */
    protected boolean processUnknowPacket(Element doc) {
        // Handle subsequent db:result packets
        if ("db".equals(doc.getNamespacePrefix()) && "result".equals(doc.getName())) {
            if (!((IncomingServerSession) session).validateSubsequentDomain(doc)) {
                open = false;
            }
            return true;
        }
        else if ("db".equals(doc.getNamespacePrefix()) && "verify".equals(doc.getName())) {
            // The Receiving Server is reusing an existing connection for sending the
            // Authoritative Server a request for verification of a key
            ((IncomingServerSession) session).verifyReceivedKey(doc);
            return true;
        }
        return false;
    }

    /**
     * Make sure that the received packet has a TO and FROM values defined and that it was sent
     * from a previously validated domain. If the packet does not matches any of the above
     * conditions then a PacketRejectedException will be thrown.
     *
     * @param packet the received packet.
     * @throws PacketRejectedException if the packet does not include a TO or FROM or if the packet
     *                                 was sent from a domain that was not previously validated.
     */
    private void packetReceived(Packet packet) throws PacketRejectedException {
        if (packet.getTo() == null || packet.getFrom() == null) {
            Log.debug("Closing IncomingServerSession due to packet with no TO or FROM: " +
                    packet.toXML());
            // Send a stream error saying that the packet includes no TO or FROM
            StreamError error = new StreamError(StreamError.Condition.improper_addressing);
            connection.deliverRawText(error.toXML());
            // Close the underlying connection
            connection.close();
            open = false;
            throw new PacketRejectedException("Packet with no TO or FROM attributes");
        }
        else if (!((IncomingServerSession) session).isValidDomain(packet.getFrom().getDomain())) {
            Log.debug("Closing IncomingServerSession due to packet with invalid domain: " +
                    packet.toXML());
            // Send a stream error saying that the packet includes an invalid FROM
            StreamError error = new StreamError(StreamError.Condition.invalid_from);
            connection.deliverRawText(error.toXML());
            // Close the underlying connection
            connection.close();
            open = false;
            throw new PacketRejectedException("Packet with no TO or FROM attributes");
        }
    }

    protected void shutdown() {
        super.shutdown();
        // Shutdown the pool of threads that are processing packets sent by
        // the remote server
        threadPool.shutdown();
    }

    boolean createSession(String namespace) throws UnauthorizedException, XmlPullParserException,
            IOException {
        if ("jabber:server".equals(namespace)) {
            // The connected client is a server so create an IncomingServerSession
            session = IncomingServerSession.createSession(serverName, reader, connection);
            return true;
        }
        return false;
    }

    String getNamespace() {
        return "jabber:server";
    }

    String getName() {
        return "Server SR - " + hashCode();
    }

    boolean validateHost() {
        return true;
    }
}
