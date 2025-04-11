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
package com.zimbra.cs.mailbox;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ArrayUtil;
import com.zimbra.common.util.Constants;
import com.zimbra.common.util.Pair;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.SoapFaultException;
import com.zimbra.cs.mailbox.InitialSync.InviteMimeLocator;
import com.zimbra.cs.mailbox.MailItem.TypedIdList;
import com.zimbra.cs.mailbox.MailServiceException.NoSuchItemException;
import com.zimbra.cs.mailbox.OfflineMailbox.OfflineContext;
import com.zimbra.cs.mime.Mime;
import com.zimbra.cs.mime.ParsedMessage;
import com.zimbra.cs.offline.OfflineLog;
import com.zimbra.cs.service.mail.ItemAction;
import com.zimbra.cs.service.mail.Sync;
import com.zimbra.cs.service.mail.ToXML;
import com.zimbra.cs.service.util.ItemIdFormatter;
import com.zimbra.cs.session.PendingModifications.Change;
import com.zimbra.cs.util.JMSession;
import com.zimbra.cs.zclient.ZMailbox;

public class PushChanges {
	
	private static class LocalInviteMimeLocator implements InviteMimeLocator {
		OfflineMailbox ombx;
		
		public LocalInviteMimeLocator(OfflineMailbox ombx) {
			this.ombx = ombx;
		}
		
		public byte[] getInviteMime(int calendarItemId, int inviteId) throws ServiceException {
			CalendarItem cal = ombx.getCalendarItemById(PushChanges.sContext, calendarItemId);
			MimeMessage mm = cal.getSubpartMessage(inviteId);
			
			try {
				ByteArrayOutputStream bao = new ByteArrayOutputStream();
				mm.writeTo(bao);
				return bao.toByteArray();
			} catch (Exception x) {
				throw ServiceException.FAILURE("appt=" + calendarItemId + ";inv=" + inviteId, x);
			}
		}
	}

    /** The bitmask of all message changes that we propagate to the server. */
    static final int MESSAGE_CHANGES = Change.MODIFIED_UNREAD | Change.MODIFIED_FLAGS | Change.MODIFIED_TAGS |
                                       Change.MODIFIED_FOLDER | Change.MODIFIED_COLOR | Change.MODIFIED_CONTENT;

    /** The bitmask of all chat changes that we propagate to the server. */
    static final int CHAT_CHANGES = Change.MODIFIED_UNREAD | Change.MODIFIED_FLAGS | Change.MODIFIED_TAGS |
                                    Change.MODIFIED_FOLDER | Change.MODIFIED_COLOR | Change.MODIFIED_CONTENT;

    /** The bitmask of all contact changes that we propagate to the server. */
    static final int CONTACT_CHANGES = Change.MODIFIED_FLAGS | Change.MODIFIED_TAGS | Change.MODIFIED_FOLDER |
                                       Change.MODIFIED_COLOR | Change.MODIFIED_CONTENT;

    /** The bitmask of all folder changes that we propagate to the server. */
    static final int FOLDER_CHANGES = Change.MODIFIED_FLAGS | Change.MODIFIED_FOLDER | Change.MODIFIED_NAME |
                                      Change.MODIFIED_COLOR | Change.MODIFIED_URL    | Change.MODIFIED_ACL;

    /** The bitmask of all search folder changes that we propagate to the server. */
    static final int SEARCH_CHANGES = Change.MODIFIED_FLAGS | Change.MODIFIED_FOLDER | Change.MODIFIED_NAME |
                                      Change.MODIFIED_COLOR | Change.MODIFIED_QUERY;

    /** The bitmask of all mountpoint changes that we propagate to the server. */
    static final int MOUNT_CHANGES = Change.MODIFIED_FLAGS | Change.MODIFIED_FOLDER | Change.MODIFIED_NAME |
                                     Change.MODIFIED_COLOR;

    /** The bitmask of all tag changes that we propagate to the server. */
    static final int TAG_CHANGES = Change.MODIFIED_NAME | Change.MODIFIED_COLOR;
    
    /** The bitmask of all appointment changes that we propagate to the server. */
    static final int APPOINTMENT_CHANGES = Change.MODIFIED_FLAGS | Change.MODIFIED_TAGS | Change.MODIFIED_FOLDER |
                                           Change.MODIFIED_COLOR | Change.MODIFIED_CONTENT | Change.MODIFIED_INVITE;

    /** A list of all the "leaf types" (i.e. non-folder types) that we
     *  synchronize with the server. */
    private static final byte[] PUSH_LEAF_TYPES = new byte[] {
        MailItem.TYPE_TAG, MailItem.TYPE_CONTACT, MailItem.TYPE_MESSAGE, MailItem.TYPE_CHAT, MailItem.TYPE_APPOINTMENT
    };

    /** The set of all the MailItem types that we synchronize with the server. */
    static final Set<Byte> PUSH_TYPES_SET = new HashSet<Byte>(Arrays.asList(
        MailItem.TYPE_FOLDER, MailItem.TYPE_SEARCHFOLDER, MailItem.TYPE_MOUNTPOINT,
        MailItem.TYPE_TAG, MailItem.TYPE_CONTACT, MailItem.TYPE_MESSAGE, MailItem.TYPE_CHAT, MailItem.TYPE_APPOINTMENT
    ));


    private static final OfflineContext sContext = new OfflineContext();

    private final OfflineMailbox ombx;
    private ZMailbox mZMailbox = null;

    private PushChanges(OfflineMailbox mbox) {
        ombx = mbox;
    }

    private ZMailbox getZMailbox() throws ServiceException {
        if (mZMailbox == null) {
            ZMailbox.Options options = new ZMailbox.Options(ombx.getAuthToken(), ombx.getSoapUri());
            options.setNoSession(true);
            mZMailbox = ZMailbox.getMailbox(options);
        }
        return mZMailbox;
    }


    public static boolean sync(OfflineMailbox ombx) throws ServiceException {
        return new PushChanges(ombx).sync();
    }

    public boolean sync() throws ServiceException {
        int limit;
        TypedIdList changes, tombstones;

        synchronized (ombx) {
            limit = ombx.getLastChangeID();
            tombstones = ombx.getTombstoneSet(0);
            changes = ombx.getLocalChanges(sContext);
        }

        if (changes.isEmpty() && tombstones.isEmpty())
            return false;

        OfflineLog.offline.debug("starting change push");
        pushChanges(changes, tombstones, limit);
        OfflineLog.offline.debug("ending change push");

        return true;
    }

    private void pushChanges(TypedIdList changes, TypedIdList tombstones, int limit) throws ServiceException {
        boolean hasDeletes = !tombstones.isEmpty();

        // because tags reuse IDs, we need to do tag deletes before any other changes (especially tag creates)
        List<Integer> tagDeletes = tombstones.getIds(MailItem.TYPE_TAG);
        if (tagDeletes != null && !tagDeletes.isEmpty()) {
            Element request = new Element.XMLElement(MailConstants.TAG_ACTION_REQUEST);
            request.addElement(MailConstants.E_ACTION).addAttribute(MailConstants.A_OPERATION, ItemAction.OP_HARD_DELETE).addAttribute(MailConstants.A_ID, concatenateIds(tagDeletes));
            ombx.sendRequest(request);
            OfflineLog.offline.debug("push: pushed tag deletes: " + tagDeletes);

            tombstones.remove(MailItem.TYPE_TAG);
        }

        // process pending "sent" messages
        if (ombx.getFolderById(sContext, OfflineMailbox.ID_FOLDER_OUTBOX).getSize() > 0)
            sendPendingMessages(changes);

        // do folder ops top-down so that we don't get dinged when folders switch places
        if (!changes.isEmpty()) {
            if (changes.getIds(MailItem.TYPE_FOLDER) != null || changes.getIds(MailItem.TYPE_SEARCHFOLDER) != null || changes.getIds(MailItem.TYPE_MOUNTPOINT) != null) {
                for (Folder folder : ombx.getFolderById(sContext, Mailbox.ID_FOLDER_ROOT).getSubfolderHierarchy()) {
                    if (changes.remove(folder.getType(), folder.getId())) {
                        switch (folder.getType()) {
                            case MailItem.TYPE_SEARCHFOLDER:  syncSearchFolder(folder.getId());  break;
                            case MailItem.TYPE_MOUNTPOINT:    syncMountpoint(folder.getId());    break;
                            case MailItem.TYPE_FOLDER:        syncFolder(folder.getId());        break;
                        }
                    }
                }
                changes.remove(MailItem.TYPE_FOLDER);  changes.remove(MailItem.TYPE_SEARCHFOLDER);  changes.remove(MailItem.TYPE_MOUNTPOINT);
            }
        }

        // make sure that tags are synced before subsequent item updates
        List<Integer> changedTags = changes.getIds(MailItem.TYPE_TAG);
        if (changedTags != null) {
            for (int id : changedTags)
                syncTag(id);
            changes.remove(MailItem.TYPE_TAG);
        }

        // modifies must come after folder and tag creates so that move/tag ops can succeed
        if (!changes.isEmpty()) {
            for (byte type : PUSH_LEAF_TYPES) {
                List<Integer> ids = changes.getIds(type);
                if (ids == null)
                    continue;
                for (int id : ids) {
                    switch (type) {
                        case MailItem.TYPE_TAG:         syncTag(id);          break;
                        case MailItem.TYPE_CONTACT:     syncContact(id);      break;
                        case MailItem.TYPE_MESSAGE:     syncMessage(id);      break;
                        case MailItem.TYPE_APPOINTMENT: syncCalendarItem(id); break;
                    }
                }
            }
        }

        // folder deletes need to come after moves are processed, else we'll be deleting items we shouldn't
        if (!tombstones.isEmpty()) {
            String ids = concatenateIds(tombstones.getAll());
            Element request = new Element.XMLElement(MailConstants.ITEM_ACTION_REQUEST);
            request.addElement(MailConstants.E_ACTION).addAttribute(MailConstants.A_OPERATION, ItemAction.OP_HARD_DELETE).addAttribute(MailConstants.A_ID, ids);
            ombx.sendRequest(request);
            OfflineLog.offline.debug("push: pushed deletes: [" + ids + ']');
        }

        if (hasDeletes)
            ombx.clearTombstones(sContext, limit);
    }

    /** Tracks messages that we've called SendMsg on but never got back a
     *  response.  This should help avoid duplicate sends when the connection
     *  goes away in the process of a SendMsg.<p>
     *  
     *  key: a String of the form <tt>account-id:message-id</tt><p>
     *  value: a Pair containing the content change ID and the "send UID"
     *         used when the message was previously sent. */
    private static final Map<String, Pair<Integer, String>> sSendUIDs = new HashMap<String, Pair<Integer, String>>();

    /** For each message in the Outbox, uploads it to the remote server, calls
     *  SendMsg to dispatch it appropriately, and deletes it from the local
     *  store.  As a side effect, removes the corresponding (now-deleted)
     *  drafts from the list of pending creates that need to be pushed to the
     *  server. */
    private void sendPendingMessages(TypedIdList creates) throws ServiceException {
        int[] pendingSends = ombx.listItemIds(sContext, MailItem.TYPE_MESSAGE, OfflineMailbox.ID_FOLDER_OUTBOX);
        if (pendingSends == null || pendingSends.length == 0)
            return;

        // ids are returned in descending order of date, so we reverse the order to send the oldest first
        for (int id : ArrayUtil.reverse(pendingSends)) {
            try {
                Message msg = ombx.getMessageById(sContext, id);

                // try to avoid repeated sends of the same message by tracking "send UIDs" on SendMsg requests
                String msgKey = ombx.getAccountId() + ':' + id;
                Pair<Integer, String> sendRecord = sSendUIDs.get(msgKey);
                String sendUID = sendRecord == null || sendRecord.getFirst() != msg.getSavedSequence() ? UUID.randomUUID().toString() : sendRecord.getSecond();
                sSendUIDs.put(msgKey, new Pair<Integer, String>(msg.getSavedSequence(), sendUID));

                // upload and send the message
                String uploadId = uploadMessage(msg.getContent());
                Element request = new Element.XMLElement(MailConstants.SEND_MSG_REQUEST).addAttribute(MailConstants.A_SEND_UID, sendUID);
                Element m = request.addElement(MailConstants.E_MSG).addAttribute(MailConstants.A_ATTACHMENT_ID, uploadId);
                if (msg.getDraftOrigId() > 0)
                    m.addAttribute(MailConstants.A_ORIG_ID, msg.getDraftOrigId()).addAttribute(MailConstants.A_REPLY_TYPE, msg.getDraftReplyType());
                
                try {
                	ombx.sendRequest(request);
                	OfflineLog.offline.debug("push: sent pending mail (" + id + "): " + msg.getSubject());
                } catch (SoapFaultException x) {
                	if (!x.isReceiversFault()) { //supposedly this is client fault
                		OfflineLog.offline.debug("push: failed to send mail (" + id + "): " + msg.getSubject(), x);
                		
	                	MimeMessage mm = new Mime.FixedMimeMessage(JMSession.getSession());
                		try {
                			mm.setFrom(new InternetAddress(ombx.getAccount().getName()));
	                		mm.setRecipient(RecipientType.TO, new InternetAddress(ombx.getAccount().getName()));
	                		mm.setSubject("Delivery failed: " + x.getMessage());
	                		
	                		mm.saveChanges(); //must call this to update the headers
	                		
	                		MimeMultipart mmp = new MimeMultipart();
	                		MimeBodyPart mbp = new MimeBodyPart();
	                		mbp.setText(x.getDetail().prettyPrint());
	               			mmp.addBodyPart(mbp);
	                		
	                		mbp = new MimeBodyPart();
	                		mbp.setContent(msg.getMimeMessage(), "message/rfc822");
	                		mbp.setHeader("Content-Disposition", "attachment");
	                		mmp.addBodyPart(mbp, mmp.getCount());
	                		
	                		mm.setContent(mmp);
	                		mm.saveChanges();
                		
	                		//directly bounce to local inbox
	                		ParsedMessage pm = new ParsedMessage(mm, true);
	                		ombx.addMessage(sContext, pm, OfflineMailbox.ID_FOLDER_INBOX, true, 0, null);
                		} catch (Exception e) {
                			OfflineLog.offline.warn("can't bounce failed push (" + id + ")" + msg.getSubject(), e);
                		}
                	} else {
                		throw x;
                	}
                }

                // remove the draft from the outbox
                ombx.delete(sContext, id, MailItem.TYPE_MESSAGE);
                OfflineLog.offline.debug("push: deleted pending draft (" + id + ')');

                // the draft is now gone, so remove it from the "send UID" hash and the list of items to push
                sSendUIDs.remove(msgKey);
                creates.remove(MailItem.TYPE_MESSAGE, id);
            } catch (NoSuchItemException nsie) {
                OfflineLog.offline.debug("push: ignoring deleted pending mail (" + id + ")");
            }
        }
    }

    /** Uploads the given message to the remote server using file upload.
     *  We scale the allowed timeout with the size of the message -- a base
     *  of 5 seconds, plus 1 second per 25K of message size. */
    private String uploadMessage(byte[] content) throws ServiceException {
        int timeout = (int) ((5 + content.length / 25000) * Constants.MILLIS_PER_SECOND);
        return getZMailbox().uploadAttachment("message", content, Mime.CT_MESSAGE_RFC822, timeout);
    }

    /** Turns a List of Integers into a String of the form <tt>1,2,3,4</tt>. */
    private String concatenateIds(List<Integer> ids) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Integer id : ids) {
            if (i++ != 0)
                sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    /** Dispatches a push request (either a create or an update) to the remote
     *  mailbox.  If there's a naming conflict, attempts to discover the remote
     *  item and rename it out of the way.  If that fails, renames the local
     *  item and throws the original <tt>mail.ALREADY_EXISTS</tt> exception.
     * 
     * @param request   The SOAP request to be executed remotely.
     * @param create    Whether the request is a create or update operation.
     * @param id        The id of the created/updated item.
     * @param type      The type of the created/updated item.
     * @param name      The name of the created/updated item.
     * @param folderId  The location of the created/updated item.
     * @return A {@link Pair} containing the new item's ID and content change
     *         sequence for creates, or <tt>null</tt> for updates. */
    private Pair<Integer,Integer> pushRequest(Element request, boolean create, int id, byte type, String name, int folderId)
    throws ServiceException {
        SoapFaultException originalException = null;
        try {
            // try to create/update the item as requested
            return sendRequest(request, create, id, type, name);
        } catch (SoapFaultException sfe) {
            if (name == null || !sfe.getCode().equals(MailServiceException.ALREADY_EXISTS))
                throw sfe;
            OfflineLog.offline.info("push: detected naming conflict with remote item " + folderId + '/' + name);
            originalException = sfe;
        }

        String uuid = '{' + UUID.randomUUID().toString() + '}', conflictRename;
        if (name.length() + uuid.length() > MailItem.MAX_NAME_LENGTH)
            conflictRename = name.substring(0, MailItem.MAX_NAME_LENGTH - uuid.length()) + uuid;
        else
            conflictRename = name + uuid;

        try {
            // figure out what the conflicting remote item is
            Element query = new Element.XMLElement(MailConstants.GET_ITEM_REQUEST);
            query.addElement(MailConstants.E_ITEM).addAttribute(MailConstants.A_FOLDER, folderId).addAttribute(MailConstants.A_NAME, name);
            Element conflict = ombx.sendRequest(query).listElements().get(0);
            int conflictId = (int) conflict.getAttributeLong(MailConstants.A_ID);
            byte conflictType = Sync.typeForElementName(conflict.getName());

            // rename the conflicting item out of the way
            Element rename = null;
            switch (conflictType) {
                case MailItem.TYPE_SEARCHFOLDER:
                case MailItem.TYPE_MOUNTPOINT:
                case MailItem.TYPE_FOLDER:  rename = new Element.XMLElement(MailConstants.FOLDER_ACTION_REQUEST);  break;

                case MailItem.TYPE_TAG:     rename = new Element.XMLElement(MailConstants.TAG_ACTION_REQUEST);  break;

                case MailItem.TYPE_DOCUMENT:
                case MailItem.TYPE_WIKI:    rename = new Element.XMLElement(MailConstants.WIKI_ACTION_REQUEST);  break;

                default:                    rename = new Element.XMLElement(MailConstants.ITEM_ACTION_REQUEST);  break;
            }
            rename.addElement(MailConstants.E_ACTION).addAttribute(MailConstants.A_OPERATION, ItemAction.OP_RENAME).addAttribute(MailConstants.A_ID, conflictId)
                                                   .addAttribute(MailConstants.A_FOLDER, folderId).addAttribute(MailConstants.A_NAME, conflictRename);
            ombx.sendRequest(rename);
            OfflineLog.offline.info("push: renamed remote " + MailItem.getNameForType(conflictType) + " (" + conflictId + ") to " + folderId + '/' + conflictRename);

            // retry the original create/update
            return sendRequest(request, create, id, type, name);
        } catch (SoapFaultException sfe) {
            // remote server doesn't support GetItem, so all we can do is to rename the local item and retry
            boolean unsupported = sfe.getCode().equals(ServiceException.UNKNOWN_DOCUMENT);
            OfflineLog.offline.info("push: could not resolve naming conflict with remote item; will rename locally", unsupported ? null : sfe);
        }

        ombx.rename(null, id, type, conflictRename, folderId);
        OfflineLog.offline.info("push: renamed local " + MailItem.getNameForType(type) + " (" + id + ") to " + folderId + '/' + conflictRename);
        throw originalException;
    }

    /** Dispatches a push request (either a create or an update) to the remote
     *  mailbox.  Merely sends the request and logs; does not perform any
     *  conflict resolution.
     * 
     * @param request  The SOAP request to be executed remotely.
     * @param create   Whether the request is a create or update operation.
     * @param id       The id of the created/updated item (for logging).
     * @param type     The type of the created/updated item (for logging).
     * @param name     The name of the created/updated item (for logging).
     * @return A {@link Pair} containing the new item's ID and content change
     *         sequence for creates, or <tt>null</tt> for updates. */
    private Pair<Integer,Integer> sendRequest(Element request, boolean create, int id, byte type, String name) throws ServiceException {
        // try to create/update the item as requested
        Element response = ombx.sendRequest(request);
        if (create) {
            int newId = (int) response.getElement(Sync.elementNameForType(type)).getAttributeLong(MailConstants.A_ID);
            int newRevision = (int) response.getElement(Sync.elementNameForType(type)).getAttributeLong(MailConstants.A_REVISION, -1);
            OfflineLog.offline.debug("push: created " + MailItem.getNameForType(type) + " (" + newId + ") from local (" + id + (name == null ? ")" : "): " + name));
            return new Pair<Integer,Integer>(newId, newRevision);
        } else {
            OfflineLog.offline.debug("push: updated " + MailItem.getNameForType(type) + " (" + id + (name == null ? ")" : "): " + name));
            return null;
        }
    }

    private boolean syncSearchFolder(int id) throws ServiceException {
        Element request = new Element.XMLElement(MailConstants.FOLDER_ACTION_REQUEST);
        Element action = request.addElement(MailConstants.E_ACTION).addAttribute(MailConstants.A_OPERATION, ItemAction.OP_UPDATE).addAttribute(MailConstants.A_ID, id);

        int flags, parentId;
        byte color;
        String name, query, searchTypes, sort;
        boolean create = false;
        synchronized (ombx) {
            SearchFolder search = ombx.getSearchFolderById(sContext, id);
            name = search.getName();    flags = search.getInternalFlagBitmask();
            color = search.getColor();  parentId = search.getFolderId();  
            query = search.getQuery();  searchTypes = search.getReturnTypes();  sort = search.getSortField();

            int mask = ombx.getChangeMask(sContext, id, MailItem.TYPE_SEARCHFOLDER);
            if ((mask & Change.MODIFIED_CONFLICT) != 0) {
                // this is a new search folder; need to push to the server
                request = new Element.XMLElement(MailConstants.CREATE_SEARCH_FOLDER_REQUEST);
                action = request.addElement(MailConstants.E_SEARCH);
                create = true;
            }
            if (create || (mask & Change.MODIFIED_FLAGS) != 0)
                action.addAttribute(MailConstants.A_FLAGS, Flag.bitmaskToFlags(flags));
            if (create || (mask & Change.MODIFIED_FOLDER) != 0)
                action.addAttribute(MailConstants.A_FOLDER, parentId);
            if (create || (mask & Change.MODIFIED_COLOR) != 0)
                action.addAttribute(MailConstants.A_COLOR, color);
            if (create || (mask & Change.MODIFIED_NAME) != 0)
                action.addAttribute(MailConstants.A_NAME, name);
            if (create || (mask & Change.MODIFIED_QUERY) != 0)
                action.addAttribute(MailConstants.A_QUERY, query).addAttribute(MailConstants.A_SEARCH_TYPES, searchTypes).addAttribute(MailConstants.A_SORT_FIELD, sort);
        }

        try {
            Pair<Integer,Integer> createData = pushRequest(request, create, id, MailItem.TYPE_SEARCHFOLDER, name, parentId);
            if (create) {
                // make sure the old item matches the new item...
                ombx.renumberItem(sContext, id, MailItem.TYPE_SEARCHFOLDER, createData.getFirst(), createData.getSecond());
                id = createData.getFirst();
            }
        } catch (SoapFaultException sfe) {
            if (!sfe.getCode().equals(MailServiceException.NO_SUCH_FOLDER))
                throw sfe;
            OfflineLog.offline.info("push: remote search folder " + id + " has been deleted; skipping");
            return true;
        }

        synchronized (ombx) {
            SearchFolder search = ombx.getSearchFolderById(sContext, id);
            // check to see if the search was changed while we were pushing the update...
            int mask = 0;
            if (flags != search.getInternalFlagBitmask())  mask |= Change.MODIFIED_FLAGS;
            if (parentId != search.getFolderId())          mask |= Change.MODIFIED_NAME;
            if (color != search.getColor())                mask |= Change.MODIFIED_COLOR;
            if (!name.equals(search.getName()))            mask |= Change.MODIFIED_NAME;
            if (!query.equals(search.getQuery()))              mask |= Change.MODIFIED_QUERY;
            if (!searchTypes.equals(search.getReturnTypes()))  mask |= Change.MODIFIED_QUERY;
            if (!sort.equals(search.getSortField()))           mask |= Change.MODIFIED_QUERY;

            // update or clear the change bitmask
            ombx.setChangeMask(sContext, id, MailItem.TYPE_SEARCHFOLDER, mask);
            return (mask == 0);
        }
    }

    private boolean syncMountpoint(int id) throws ServiceException {
        Element request = new Element.XMLElement(MailConstants.FOLDER_ACTION_REQUEST);
        Element action = request.addElement(MailConstants.E_ACTION).addAttribute(MailConstants.A_OPERATION, ItemAction.OP_UPDATE).addAttribute(MailConstants.A_ID, id);

        int flags, parentId;
        byte color;
        String name;
        boolean create = false;
        synchronized (ombx) {
            Mountpoint mpt = ombx.getMountpointById(sContext, id);
            name = mpt.getName();    flags = mpt.getInternalFlagBitmask();
            color = mpt.getColor();  parentId = mpt.getFolderId();

            int mask = ombx.getChangeMask(sContext, id, MailItem.TYPE_MOUNTPOINT);
            if ((mask & Change.MODIFIED_CONFLICT) != 0) {
                // this is a new mountpoint; need to push to the server
                request = new Element.XMLElement(MailConstants.CREATE_MOUNTPOINT_REQUEST);
                action = request.addElement(MailConstants.E_MOUNT).addAttribute(MailConstants.A_REMOTE_ID, mpt.getRemoteId())
                                .addAttribute(MailConstants.A_ZIMBRA_ID, mpt.getOwnerId())
                                .addAttribute(MailConstants.A_DEFAULT_VIEW, MailItem.getNameForType(mpt.getDefaultView()));
                create = true;
            }
            if (create || (mask & Change.MODIFIED_FLAGS) != 0)
                action.addAttribute(MailConstants.A_FLAGS, Flag.bitmaskToFlags(flags));
            if (create || (mask & Change.MODIFIED_FOLDER) != 0)
                action.addAttribute(MailConstants.A_FOLDER, parentId);
            if (create || (mask & Change.MODIFIED_COLOR) != 0)
                action.addAttribute(MailConstants.A_COLOR, color);
            if (create || (mask & Change.MODIFIED_NAME) != 0)
                action.addAttribute(MailConstants.A_NAME, name);
        }

        try {
            Pair<Integer,Integer> createData = pushRequest(request, create, id, MailItem.TYPE_MOUNTPOINT, name, parentId);
            if (create) {
                // make sure the old item matches the new item...
                ombx.renumberItem(sContext, id, MailItem.TYPE_MOUNTPOINT, createData.getFirst(), createData.getSecond());
                id = createData.getFirst();
            }
        } catch (SoapFaultException sfe) {
            if (!sfe.getCode().equals(MailServiceException.NO_SUCH_FOLDER))
                throw sfe;
            OfflineLog.offline.info("push: remote mountpoint " + id + " has been deleted; skipping");
            return true;
        }

        synchronized (ombx) {
            Mountpoint mpt = ombx.getMountpointById(sContext, id);
            // check to see if the mountpoint was changed while we were pushing the update...
            int mask = 0;
            if (flags != mpt.getInternalFlagBitmask())  mask |= Change.MODIFIED_FLAGS;
            if (parentId != mpt.getFolderId())          mask |= Change.MODIFIED_NAME;
            if (color != mpt.getColor())                mask |= Change.MODIFIED_COLOR;
            if (!name.equals(mpt.getName()))            mask |= Change.MODIFIED_NAME;

            // update or clear the change bitmask
            ombx.setChangeMask(sContext, id, MailItem.TYPE_MOUNTPOINT, mask);
            return (mask == 0);
        }
    }

    private boolean syncFolder(int id) throws ServiceException {
        Element request = new Element.XMLElement(MailConstants.FOLDER_ACTION_REQUEST);
        Element action = request.addElement(MailConstants.E_ACTION).addAttribute(MailConstants.A_OPERATION, ItemAction.OP_UPDATE).addAttribute(MailConstants.A_ID, id);

        int flags, parentId;
        byte color;
        String name, url;
        boolean create = false;
        synchronized (ombx) {
            Folder folder = ombx.getFolderById(sContext, id);
            name = folder.getName();  parentId = folder.getFolderId();  flags = folder.getInternalFlagBitmask();
            url = folder.getUrl();    color = folder.getColor();

            int mask = ombx.getChangeMask(sContext, id, MailItem.TYPE_FOLDER);
            if ((mask & Change.MODIFIED_CONFLICT) != 0) {
                // this is a new folder; need to push to the server
                request = new Element.XMLElement(MailConstants.CREATE_FOLDER_REQUEST);
                action = request.addElement(MailConstants.E_FOLDER).addAttribute(MailConstants.A_DEFAULT_VIEW, MailItem.getNameForType(folder.getDefaultView()));
                create = true;
            }
            if (create || (mask & Change.MODIFIED_FLAGS) != 0)
                action.addAttribute(MailConstants.A_FLAGS, Flag.bitmaskToFlags(flags));
            if (create || (mask & Change.MODIFIED_FOLDER) != 0)
                action.addAttribute(MailConstants.A_FOLDER, parentId);
            if (create || (mask & Change.MODIFIED_COLOR) != 0)
                action.addAttribute(MailConstants.A_COLOR, color);
            if (create || (mask & Change.MODIFIED_NAME) != 0)
                action.addAttribute(MailConstants.A_NAME, name);
            if (create || (mask & Change.MODIFIED_URL) != 0)
                action.addAttribute(MailConstants.A_URL, url);
            // FIXME: does not support ACL sync at all...
        }

        try {
            Pair<Integer,Integer> createData = pushRequest(request, create, id, MailItem.TYPE_FOLDER, name, parentId);
            if (create) {
                // make sure the old item matches the new item...
                ombx.renumberItem(sContext, id, MailItem.TYPE_FOLDER, createData.getFirst(), createData.getSecond());
                id = createData.getFirst();
            }
        } catch (SoapFaultException sfe) {
            if (!sfe.getCode().equals(MailServiceException.NO_SUCH_FOLDER))
                throw sfe;
            OfflineLog.offline.info("push: remote folder " + id + " has been deleted; skipping");
            return true;
        }

        synchronized (ombx) {
            Folder folder = ombx.getFolderById(sContext, id);
            // check to see if the folder was changed while we were pushing the update...
            int mask = 0;
            if (flags != folder.getInternalFlagBitmask())  mask |= Change.MODIFIED_FLAGS;
            if (parentId != folder.getFolderId())          mask |= Change.MODIFIED_NAME;
            if (color != folder.getColor())                mask |= Change.MODIFIED_COLOR;
            if (!name.equals(folder.getName()))            mask |= Change.MODIFIED_NAME;
            if (!url.equals(folder.getUrl()))              mask |= Change.MODIFIED_URL;

            // update or clear the change bitmask
            ombx.setChangeMask(sContext, id, MailItem.TYPE_FOLDER, mask);
            return (mask == 0);
        }
    }

    private boolean syncTag(int id) throws ServiceException {
        Element request = new Element.XMLElement(MailConstants.TAG_ACTION_REQUEST);
        Element action = request.addElement(MailConstants.E_ACTION).addAttribute(MailConstants.A_OPERATION, ItemAction.OP_UPDATE).addAttribute(MailConstants.A_ID, id);

        byte color;
        String name;
        boolean create = false;
        synchronized (ombx) {
            Tag tag = ombx.getTagById(sContext, id);
            color = tag.getColor();  name = tag.getName();

            int mask = ombx.getChangeMask(sContext, id, MailItem.TYPE_TAG);
            if ((mask & Change.MODIFIED_CONFLICT) != 0) {
                // this is a new tag; need to push to the server
                request = new Element.XMLElement(MailConstants.CREATE_TAG_REQUEST);
                action = request.addElement(MailConstants.E_TAG);
                create = true;
            }
            if (create || (mask & Change.MODIFIED_COLOR) != 0)
                action.addAttribute(MailConstants.A_COLOR, color);
            if (create || (mask & Change.MODIFIED_NAME) != 0)
                action.addAttribute(MailConstants.A_NAME, name);
        }

        try {
            Pair<Integer,Integer> createData = pushRequest(request, create, id, MailItem.TYPE_TAG, name, Mailbox.ID_FOLDER_TAGS);
            if (create) {
                int newId = createData.getFirst();
                // first, deal with more headaches caused by reusing tag ids
                if (id != createData.getFirst() && DeltaSync.getTag(ombx, newId) != null) {
                    int renumber = DeltaSync.getAvailableTagId(ombx);
                    if (renumber < 0)
                        ombx.delete(sContext, newId, MailItem.TYPE_TAG);
                    else
                        ombx.renumberItem(sContext, newId, MailItem.TYPE_TAG, renumber);
                }
                // make sure the old item matches the new item...
                ombx.renumberItem(sContext, id, MailItem.TYPE_TAG, newId, createData.getSecond());
                id = newId;
            }
        } catch (SoapFaultException sfe) {
            if (!sfe.getCode().equals(MailServiceException.NO_SUCH_TAG))
                throw sfe;
            OfflineLog.offline.info("push: remote tag " + id + " has been deleted; skipping");
            return true;
        }

        synchronized (ombx) {
            Tag tag = ombx.getTagById(sContext, id);
            // check to see if the tag was changed while we were pushing the update...
            int mask = 0;
            if (color != tag.getColor())      mask |= Change.MODIFIED_COLOR;
            if (!name.equals(tag.getName()))  mask |= Change.MODIFIED_NAME;

            // update or clear the change bitmask
            ombx.setChangeMask(sContext, id, MailItem.TYPE_TAG, mask);
            return (mask == 0);
        }
    }

    private boolean syncContact(int id) throws ServiceException {
        Element request = new Element.XMLElement(MailConstants.CONTACT_ACTION_REQUEST);
        Element action = request.addElement(MailConstants.E_ACTION).addAttribute(MailConstants.A_OPERATION, ItemAction.OP_UPDATE).addAttribute(MailConstants.A_ID, id);

        int flags, folderId;
        long date, tags;
        byte color;
        boolean create = false;
        synchronized (ombx) {
            Contact cn = ombx.getContactById(sContext, id);
            date = cn.getDate();    flags = cn.getFlagBitmask();  tags = cn.getTagBitmask();
            color = cn.getColor();  folderId = cn.getFolderId();

            int mask = ombx.getChangeMask(sContext, id, MailItem.TYPE_CONTACT);
            if ((mask & Change.MODIFIED_CONFLICT) != 0) {
                // this is a new contact; need to push to the server
                request = new Element.XMLElement(MailConstants.CREATE_CONTACT_REQUEST);
                action = request.addElement(MailConstants.E_CONTACT);
                create = true;
            }
            if (create || (mask & Change.MODIFIED_FLAGS) != 0)
                action.addAttribute(MailConstants.A_FLAGS, Flag.bitmaskToFlags(flags));
            if (create || (mask & Change.MODIFIED_TAGS) != 0)
                action.addAttribute(MailConstants.A_TAGS, cn.getTagString());
            if (create || (mask & Change.MODIFIED_FOLDER) != 0)
                action.addAttribute(MailConstants.A_FOLDER, folderId);
            if (create || (mask & Change.MODIFIED_COLOR) != 0)
                action.addAttribute(MailConstants.A_COLOR, color);
            if (create || (mask & Change.MODIFIED_CONTENT) != 0) {
                for (Map.Entry<String, String> field : cn.getFields().entrySet()) {
                    String name = field.getKey(), value = field.getValue();
                    if (name == null || name.trim().equals("") || value == null || value.equals(""))
                        continue;
                    action.addKeyValuePair(name, value);
                }
            }
        }

        try {
            Pair<Integer,Integer> createData = pushRequest(request, create, id, MailItem.TYPE_CONTACT, null, folderId);
            if (create) {
                // make sure the old item matches the new item...
                ombx.renumberItem(sContext, id, MailItem.TYPE_CONTACT, createData.getFirst(), createData.getSecond());
                id = createData.getFirst();
            }
        } catch (SoapFaultException sfe) {
            if (!sfe.getCode().equals(MailServiceException.NO_SUCH_CONTACT))
                throw sfe;
            OfflineLog.offline.info("push: remote contact " + id + " has been deleted; skipping");
            return true;
        }

        synchronized (ombx) {
            Contact cn = ombx.getContactById(sContext, id);
            // check to see if the contact was changed while we were pushing the update...
            int mask = 0;
            if (flags != cn.getInternalFlagBitmask())  mask |= Change.MODIFIED_FLAGS;
            if (tags != cn.getTagBitmask())            mask |= Change.MODIFIED_TAGS;
            if (folderId != cn.getFolderId())          mask |= Change.MODIFIED_FOLDER;
            if (color != cn.getColor())                mask |= Change.MODIFIED_COLOR;
            if (date != cn.getDate())                  mask |= Change.MODIFIED_CONTENT;

            // update or clear the change bitmask
            ombx.setChangeMask(sContext, id, MailItem.TYPE_CONTACT, mask);
            return (mask == 0);
        }
    }

    private boolean syncMessage(int id) throws ServiceException {
        Element request = new Element.XMLElement(MailConstants.MSG_ACTION_REQUEST);
        Element action = request.addElement(MailConstants.E_ACTION).addAttribute(MailConstants.A_OPERATION, ItemAction.OP_UPDATE).addAttribute(MailConstants.A_ID, id);

        int flags, folderId;
        long tags;
        String digest;
        byte color, newContent[] = null;
        boolean create = false;
        synchronized (ombx) {
            Message msg = ombx.getMessageById(sContext, id);
            digest = msg.getDigest();  flags = msg.getFlagBitmask();  tags = msg.getTagBitmask();
            color = msg.getColor();    folderId = msg.getFolderId();

            int mask = ombx.getChangeMask(sContext, id, MailItem.TYPE_MESSAGE);
            if ((mask & Change.MODIFIED_CONFLICT) != 0) {
                // this is a new message; need to push to the server
                request = new Element.XMLElement(msg.isDraft() ? MailConstants.SAVE_DRAFT_REQUEST : MailConstants.ADD_MSG_REQUEST);
                action = request.addElement(MailConstants.E_MSG);
                if (msg.isDraft() && msg.getDraftOrigId() > 0)
                    action.addAttribute(MailConstants.A_REPLY_TYPE, msg.getDraftReplyType()).addAttribute(MailConstants.A_ORIG_ID, msg.getDraftOrigId());
                newContent = msg.getContent();
                create = true;
            } else if ((mask & Change.MODIFIED_CONTENT) != 0) {
                // for draft message content changes, need to go through the SaveDraft door instead of the MsgAction door
                if (!msg.isDraft())
                    throw MailServiceException.IMMUTABLE_OBJECT(id);
                request = new Element.XMLElement(MailConstants.SAVE_DRAFT_REQUEST);
                action = request.addElement(MailConstants.E_MSG).addAttribute(MailConstants.A_ID, id);
                newContent = msg.getContent();
            }
            if (create || (mask & Change.MODIFIED_FLAGS | Change.MODIFIED_UNREAD) != 0)
                action.addAttribute(MailConstants.A_FLAGS, Flag.bitmaskToFlags(flags));
            if (create || (mask & Change.MODIFIED_TAGS) != 0)
                action.addAttribute(MailConstants.A_TAGS, msg.getTagString());
            if (create || (mask & Change.MODIFIED_FOLDER) != 0)
                action.addAttribute(MailConstants.A_FOLDER, folderId);
            if (create || (mask & Change.MODIFIED_COLOR) != 0)
                action.addAttribute(MailConstants.A_COLOR, color);
        }

        if (newContent != null) {
            // upload draft message body to the remote FileUploadServlet, then use the returned attachment id to save draft
            String attachId = uploadMessage(newContent);
            action.addAttribute(MailConstants.A_ATTACHMENT_ID, attachId);
        }

        try {
            Pair<Integer,Integer> createData = pushRequest(request, create, id, MailItem.TYPE_MESSAGE, null, folderId);
            if (create) {
                // make sure the old item matches the new item...
                ombx.renumberItem(sContext, id, MailItem.TYPE_MESSAGE, createData.getFirst(), createData.getSecond());
                id = createData.getFirst();
            }
        } catch (SoapFaultException sfe) {
            if (!sfe.getCode().equals(MailServiceException.NO_SUCH_MSG))
                throw sfe;
            OfflineLog.offline.info("push: remote message " + id + " has been deleted; skipping");
            return true;
        }

        synchronized (ombx) {
            Message msg = ombx.getMessageById(sContext, id);
            // check to see if the contact was changed while we were pushing the update...
            int mask = 0;
            if (flags != msg.getFlagBitmask())    mask |= Change.MODIFIED_FLAGS;
            if (tags != msg.getTagBitmask())      mask |= Change.MODIFIED_TAGS;
            if (folderId != msg.getFolderId())    mask |= Change.MODIFIED_FOLDER;
            if (color != msg.getColor())          mask |= Change.MODIFIED_COLOR;
            if (!StringUtil.equal(digest, msg.getDigest()))  mask |= Change.MODIFIED_CONTENT;

            // update or clear the change bitmask
            ombx.setChangeMask(sContext, id, MailItem.TYPE_MESSAGE, mask);
            return (mask == 0);
        }
    }
    
    private boolean syncCalendarItem(int id) throws ServiceException {

        int flags, folderId;
        long date, tags;
        byte color;
        int mask;
        
        Element request = null;
        boolean create = false;
        String name = null;
        
        synchronized (ombx) {
            CalendarItem cal = ombx.getCalendarItemById(sContext, id);
            name = cal.getSubject();
            date = cal.getDate();
            tags = cal.getTagBitmask();
            flags = cal.getFlagBitmask();
            folderId = cal.getFolderId();
            color = cal.getColor();
            mask = ombx.getChangeMask(sContext, id, MailItem.TYPE_APPOINTMENT);

	        if ((mask & Change.MODIFIED_CONFLICT) != 0 || (mask & Change.MODIFIED_CONTENT) != 0 || (mask & Change.MODIFIED_INVITE) != 0) { // need to push to the server
	        	request = new Element.XMLElement(MailConstants.SET_APPOINTMENT_REQUEST);
	            ToXML.encodeCalendarItemSummary(request, new ItemIdFormatter((String)null, (String)null, true), ombx.getOperationContext(), cal, ToXML.NOTIFY_FIELDS, true);
	            request = InitialSync.makeSetAppointmentRequest(request.getElement(MailConstants.E_APPOINTMENT), new LocalInviteMimeLocator(ombx), ombx.getAccount());
	        	create = true; //content mod is considered same as create since we use SetAppointment for both
	        } else {
	        	request = new Element.XMLElement(MailConstants.ITEM_ACTION_REQUEST);
	        	Element action = request.addElement(MailConstants.E_ACTION).addAttribute(MailConstants.A_OPERATION, ItemAction.OP_UPDATE).addAttribute(MailConstants.A_ID, id);
		        if ((mask & Change.MODIFIED_TAGS) != 0)
		        	action.addAttribute(MailConstants.A_TAGS, cal.getTagString());
		        if ((mask & Change.MODIFIED_FLAGS) != 0)
		        	action.addAttribute(MailConstants.A_FLAGS, cal.getFlagString());
		        if ((mask & Change.MODIFIED_FOLDER) != 0)
		        	action.addAttribute(MailConstants.A_FOLDER, folderId);
		        if ((mask & Change.MODIFIED_COLOR) != 0)
		        	action.addAttribute(MailConstants.A_COLOR, color);
	        }

        }

        try {
        	if (create) {
            	//Since we are using SetAppointment for both new and existing appointments we always need to sync ids
				Element response = ombx.sendRequest(request);
				int serverItemId = (int)response.getAttributeLong(MailConstants.A_CAL_ID);
				  
				//We are not processing the invIds from the SetAppointment response.
				//Instead, we just let it bounce back as a calendar update from server.
				//mod sequence will always be bounced back in the next sync so we'll set there.
				if (serverItemId != id) { //new item
					ombx.renumberItem(sContext, id, MailItem.TYPE_APPOINTMENT, serverItemId, -1);
				}
				id = serverItemId;
        	} else {
        		pushRequest(request, create, id, MailItem.TYPE_APPOINTMENT, name, folderId);
        	}
        } catch (SoapFaultException sfe) {
            if (!sfe.getCode().equals(MailServiceException.NO_SUCH_CONTACT))
                throw sfe;
            OfflineLog.offline.info("push: remote calendar item " + id + " has been deleted; skipping");
            return true;
        }

        synchronized (ombx) {
            CalendarItem cal = ombx.getCalendarItemById(sContext, id);
            // check to see if the calendar item was changed while we were pushing the update...
            mask = 0;
            if (tags != cal.getTagBitmask())            mask |= Change.MODIFIED_TAGS;
            if (flags != cal.getInternalFlagBitmask())  mask |= Change.MODIFIED_FLAGS;
            if (folderId != cal.getFolderId())          mask |= Change.MODIFIED_FOLDER;
            if (color != cal.getColor())                mask |= Change.MODIFIED_COLOR;
            if (date != cal.getDate())                  mask |= Change.MODIFIED_CONTENT;

            // update or clear the change bitmask
            ombx.setChangeMask(sContext, id, MailItem.TYPE_APPOINTMENT, mask);
            return (mask == 0);
        }
    }
}
