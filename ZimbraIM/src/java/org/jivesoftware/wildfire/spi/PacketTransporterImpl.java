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
package org.jivesoftware.wildfire.spi;

import org.jivesoftware.wildfire.container.BasicModule;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.*;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.transport.TransportHandler;
import org.xmpp.packet.Packet;

/**
 * In-memory implementation of the packet transporter service.
 *
 * @author Iain Shigeoka
 */
public class PacketTransporterImpl extends BasicModule  {

    /**
     * The handler that does the actual delivery (could be a channel instead)
     */
    private TransportHandler transportHandler;

    /**
     * deliverer for xmpp server
     */
    private PacketDeliverer deliverer;

    /**
     * xmpp server
     */
    private XMPPServer xmppServer;

    /**
     * This is a singleton, you can't create one. Be very careful not to do anything
     * that refers back to the factory's create method. Do initialization in the init()
     * method if at all possible.
     */
    public PacketTransporterImpl() {
        super("XMPP Packet Transporter");
    }

    /**
     * Obtain the transport handler that this transporter uses for delivering
     * transport packets.
     *
     * @return The transport handler instance used by this transporter
     */
    public TransportHandler getTransportHandler() {
        return transportHandler;
    }

    /**
     * Delivers the given packet based on packet recipient and sender. The
     * deliverer defers actual routing decisions to other classes.
     * <h2>Warning</h2>
     * Be careful to enforce concurrency DbC of concurrent by synchronizing
     * any accesses to class resources.
     *
     * @param packet The packet to route
     * @throws NullPointerException If the packet is null or the
     *                              packet could not be routed
     */
    public void deliver(Packet packet) throws UnauthorizedException, PacketException {
        if (packet == null) {
            throw new NullPointerException();
        }

        if (xmppServer != null && xmppServer.isLocal(packet.getTo())) {
            deliverer.deliver(packet);
        }
        else if (transportHandler != null) {
            transportHandler.process(packet);
        }
        else {
            Log.warn("Could not deliver message: no deliverer available "
                    + packet.toString());
        }
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        xmppServer = server;
        deliverer = server.getPacketDeliverer();
        transportHandler = server.getTransportHandler();
    }
}
