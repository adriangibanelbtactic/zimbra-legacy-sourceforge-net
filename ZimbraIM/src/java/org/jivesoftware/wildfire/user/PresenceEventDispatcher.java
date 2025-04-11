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
package org.jivesoftware.wildfire.user;

import org.jivesoftware.wildfire.ClientSession;
import org.xmpp.packet.Presence;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Dispatches presence events. The following events are supported:
 * <ul>
 * <li><b>availableSession</b> --> A session is now available to receive communication.</li>
 * <li><b>unavailableSession</b> --> A session is now longer available.</li>
 * <li><b>presencePriorityChanged</b> --> The priority of a resource has changed.</li>
 * <li><b>presenceChanged</b> --> The show or status value of an available session has changed.</li>
 * </ul>
 * Use {@link #addListener(PresenceEventListener)} and
 * {@link #removeListener(PresenceEventListener)} to add or remove {@link PresenceEventListener}.
 *
 * @author Gaston Dombiak
 */
public class PresenceEventDispatcher {

    private static List<PresenceEventListener> listeners =
            new CopyOnWriteArrayList<PresenceEventListener>();

    /**
     * Registers a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void addListener(PresenceEventListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        listeners.add(listener);
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void removeListener(PresenceEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notification message indicating that a session that was not available is now
     * available. A session becomes available when an available presence is received.
     * Sessions that are available will have a route in the routing table thus becoming
     * eligible for receiving messages (in particular messages sent to the user bare JID).
     *
     * @param session the session that is now available.
     * @param presence the received available presence.
     */
    public static void availableSession(ClientSession session, Presence presence) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                listener.availableSession(session, presence);
            }
        }
    }

    /**
     * Notification message indicating that a session that was available is no longer
     * available. A session becomes unavailable when an unavailable presence is received.
     * The entity may still be connected to the server and may send an available presence
     * later to indicate that communication can proceed.
     *
     * @param session the session that is no longer available.
     * @param presence the received unavailable presence.
     */
    public static void unavailableSession(ClientSession session, Presence presence) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                listener.unavailableSession(session, presence);
            }
        }
    }


    /**
     * Notification message indicating that the presence priority of a session has
     * been modified. Presence priorities are used when deciding which session of
     * the same user should receive a message that was sent to the user bare's JID.
     *
     * @param session the affected session.
     * @param presence the presence that changed the priority.
     */
    public static void presencePriorityChanged(ClientSession session, Presence presence) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                listener.presencePriorityChanged(session, presence);
            }
        }
    }


    /**
     * Notification message indicating that an available session has changed its
     * presence. This is the case when the user presence changed the show value
     * (e.g. away, dnd, etc.) or the presence status message.
     *
     * @param session the affected session.
     * @param presence the received available presence with the new information.
     */
    public static void presenceChanged(ClientSession session, Presence presence) {
        if (!listeners.isEmpty()) {
            for (PresenceEventListener listener : listeners) {
                listener.presenceChanged(session, presence);
            }
        }
    }
}
