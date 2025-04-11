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

package com.zimbra.cs.service.admin;

import java.util.Map;

import com.zimbra.cs.store.Volume;
import com.zimbra.common.soap.Element;
import com.zimbra.soap.ZimbraSoapContext;
import com.zimbra.common.soap.AdminConstants;

public class GetCurrentVolumes extends AdminDocumentHandler {

    public Element handle(Element request, Map<String, Object> context) {
        ZimbraSoapContext lc = getZimbraSoapContext(context);

        Element response = lc.createElement(AdminConstants.GET_CURRENT_VOLUMES_RESPONSE);

        Volume msgVol = Volume.getCurrentMessageVolume();
        response.addElement(AdminConstants.E_VOLUME)
                .addAttribute(AdminConstants.A_VOLUME_TYPE, Volume.TYPE_MESSAGE)
                .addAttribute(AdminConstants.A_ID, msgVol.getId());

        Volume secondaryMsgVol = Volume.getCurrentSecondaryMessageVolume();
        if (secondaryMsgVol != null)
            response.addElement(AdminConstants.E_VOLUME)
                    .addAttribute(AdminConstants.A_VOLUME_TYPE, Volume.TYPE_MESSAGE_SECONDARY)
                    .addAttribute(AdminConstants.A_ID, secondaryMsgVol.getId());

        Volume indexVol = Volume.getCurrentIndexVolume();
        response.addElement(AdminConstants.E_VOLUME)
                .addAttribute(AdminConstants.A_VOLUME_TYPE, Volume.TYPE_INDEX)
                .addAttribute(AdminConstants.A_ID, indexVol.getId());

        return response;
    }
}
