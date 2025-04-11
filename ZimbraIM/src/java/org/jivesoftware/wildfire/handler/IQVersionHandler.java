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
package org.jivesoftware.wildfire.handler;

import org.jivesoftware.wildfire.disco.ServerFeaturesProvider;
import org.jivesoftware.wildfire.IQHandlerInfo;
import org.jivesoftware.wildfire.PacketException;
//import org.jivesoftware.admin.AdminConsole;

import java.util.ArrayList;
import java.util.Iterator;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.xmpp.packet.IQ;

/**
 * Implements the TYPE_IQ jabber:iq:version protocol (version info). Allows
 * XMPP entities to query each other's application versions.  The server
 * will respond with its current version info.
 *
 * @author Iain Shigeoka
 */
public class IQVersionHandler extends IQHandler implements ServerFeaturesProvider {

    private static Element bodyElement;
    private IQHandlerInfo info;

    public IQVersionHandler() {
        super("XMPP Server Version Handler");
        info = new IQHandlerInfo("query", "jabber:iq:version");
        if (bodyElement == null) {
            bodyElement = DocumentHelper.createElement(QName.get("query", "jabber:iq:version"));
//            bodyElement.addElement("name").setText(AdminConsole.getAppName());
            bodyElement.addElement("os").setText("Java 5");
            bodyElement.addElement("version");
        }
    }

    public IQ handleIQ(IQ packet) throws PacketException {
        // Could cache this information for every server we see
        Element answerElement = bodyElement.createCopy();
//        answerElement.element("name").setText(AdminConsole.getAppName());
//        answerElement.element("version").setText(AdminConsole.getVersionString());
        IQ result = IQ.createResultIQ(packet);
        result.setChildElement(answerElement);
        return result;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public Iterator getFeatures() {
        ArrayList features = new ArrayList();
        features.add("jabber:iq:version");
        return features.iterator();
    }
}