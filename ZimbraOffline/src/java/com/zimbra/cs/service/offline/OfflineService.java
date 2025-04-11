/*
 * ***** BEGIN LICENSE BLOCK *****
 * 
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * The Original Code is: Zimbra Network
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.service.offline;

import org.dom4j.Namespace;
import org.dom4j.QName;

import com.zimbra.cs.service.mail.MailService;
import com.zimbra.soap.DocumentDispatcher;
import com.zimbra.soap.DocumentService;

public class OfflineService implements DocumentService {

    public static final String NAMESPACE_STR = "urn:zimbraOffline";
    public static final Namespace NAMESPACE = Namespace.get(NAMESPACE_STR);
    
    // sync
    public static final QName SYNC_REQUEST = QName.get("SyncRequest", NAMESPACE);
    public static final QName SYNC_RESPONSE = QName.get("SyncResponse", NAMESPACE);

    public void registerHandlers(DocumentDispatcher dispatcher) {
        // sync
        dispatcher.registerHandler(SYNC_REQUEST, new OfflineSync());

        // fetching external data
        dispatcher.registerHandler(MailService.FOLDER_ACTION_REQUEST, new OfflineFolderAction());
        dispatcher.registerHandler(MailService.GET_IMPORT_STATUS_REQUEST, new OfflineGetImportStatus());
        dispatcher.registerHandler(MailService.IMPORT_DATA_REQUEST, new OfflineImportData());
    }
}
