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

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.store.Volume;
import com.zimbra.soap.ZimbraSoapContext;

public class GetVolume extends AdminDocumentHandler {

    public Element handle(Element request, Map<String, Object> context) throws ServiceException {
        ZimbraSoapContext lc = getZimbraSoapContext(context);
        long idLong = request.getAttributeLong(AdminConstants.A_ID);
        Volume.validateID(idLong);  // avoid Java truncation
        short id = (short) idLong;
        Volume vol = Volume.getById(id);

        Element response = lc.createElement(AdminConstants.GET_VOLUME_RESPONSE);
        addVolumeElement(response, vol);
        return response;
    }

    static void addVolumeElement(Element parent, Volume vol) {
        Element eVol = parent.addElement(AdminConstants.E_VOLUME);
        eVol.addAttribute(AdminConstants.A_ID, vol.getId());
        eVol.addAttribute(AdminConstants.A_VOLUME_TYPE, vol.getType());
        eVol.addAttribute(AdminConstants.A_VOLUME_NAME, vol.getName());
        eVol.addAttribute(AdminConstants.A_VOLUME_ROOTPATH, vol.getRootPath());
        eVol.addAttribute(AdminConstants.A_VOLUME_MGBITS, vol.getMboxGroupBits());
        eVol.addAttribute(AdminConstants.A_VOLUME_MBITS,  vol.getMboxBits());
        eVol.addAttribute(AdminConstants.A_VOLUME_FGBITS, vol.getFileGroupBits());
        eVol.addAttribute(AdminConstants.A_VOLUME_FBITS,  vol.getFileBits());
        eVol.addAttribute(AdminConstants.A_VOLUME_COMPRESS_BLOBS, vol.getCompressBlobs());
        eVol.addAttribute(AdminConstants.A_VOLUME_COMPRESSION_THRESHOLD,
                          vol.getCompressionThreshold());
    }
}
