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

package com.zimbra.cs.client.soap;

import org.dom4j.Element;
import org.dom4j.DocumentHelper;

import com.zimbra.soap.DomUtil;
import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.service.mail.MailService;

public class LmcFolderActionRequest extends LmcSoapRequest {

        private String mIDList;

        private String mOp;

        private String mTargetFolder;

        private String mName;
        
        private String mPerm;
        private String mGrantee;
        private String mD;
        private boolean mInherit;

        /**
         * Set the list of Folder ID's to operate on
         * @param idList - a list of the folders to operate on
         */
        public void setFolderList(String idList) {
                mIDList = idList;
        }

        /**
         * Set the operation
         * @param op - the operation (delete, read, etc.)
         */
        public void setOp(String op) {
                mOp = op;
        }

        public void setName(String t) {
                mName = t;
        }

        public void setTargetFolder(String f) {
                mTargetFolder = f;
        }

        public void setGrant(String perm, String grantee, String d, boolean inherit) {
        	mPerm = perm;
        	mGrantee = grantee;
        	mD = d;
        	mInherit = inherit;
        }
        
        public String getFolderList() {
                return mIDList;
        }

        public String getOp() {
                return mOp;
        }

        public String getTargetFolder() {
                return mTargetFolder;
        }

        public String getName() {
                return mName;
        }

        protected Element getRequestXML() {
                Element request = DocumentHelper.createElement(MailService.FOLDER_ACTION_REQUEST);
                Element a = DomUtil.add(request, MailService.E_ACTION, "");
                if (mIDList != null)
                    DomUtil.addAttr(a, MailService.A_ID, mIDList);
                if (mOp != null) {
                    DomUtil.addAttr(a, MailService.A_OPERATION, mOp);
                    if (mOp.equals("grant") ||
                    		mOp.equals("!grant")) {
                    	Element grant = DomUtil.add(a, MailService.E_GRANT, "");
                    	if (mPerm != null)
                    		DomUtil.addAttr(grant, MailService.A_RIGHTS, mPerm);
                    	if (mGrantee != null)
                    		DomUtil.addAttr(grant, MailService.A_GRANT_TYPE, mGrantee);
                    	if (mD != null)
                    		DomUtil.addAttr(grant, MailService.A_DISPLAY, mD);
                		DomUtil.addAttr(grant, MailService.A_INHERIT, mInherit);
                    }
                }
                if (mName != null)
                    DomUtil.addAttr(a, MailService.A_NAME, mName);
                if (mTargetFolder != null)
                    DomUtil.addAttr(a, MailService.A_FOLDER, mTargetFolder);
                return request;
        }

        protected LmcSoapResponse parseResponseXML(Element responseXML)
                        throws ServiceException {
                LmcFolderActionResponse response = new LmcFolderActionResponse();
                Element a = DomUtil.get(responseXML, MailService.E_ACTION);
                response.setFolderList(DomUtil.getAttr(a, MailService.A_ID));
                response.setOp(DomUtil.getAttr(a, MailService.A_OPERATION));
                return response;
        }
}