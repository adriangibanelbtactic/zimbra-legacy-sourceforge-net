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
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on Nov 26, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.zimbra.cs.pop3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.index.ZimbraHit;
import com.zimbra.cs.index.ZimbraQueryResults;
import com.zimbra.cs.index.MailboxIndex;
import com.zimbra.cs.index.MessageHit;
import com.zimbra.cs.index.queryparser.ParseException;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Message;
import com.zimbra.cs.service.ServiceException;


class Pop3Mbx {
    private int mId; // id of the mailbox
    private int mNumDeleted; // number of messages deleted
    private long mTotalSize; // raw size from blob store
    private long mDeletedSize; // raw size from blob store    
    private ArrayList mMessages; // array of pop messages
    private Mailbox.OperationContext mOpContext;
    
    /**
     * initialize the Pop3Mbx, without keeping a reference to either the Mailbox object or
     * any of the Message objects in the inbox.
     * @param mailbox
     * @param acct TODO
     * 
     * @throws ServiceException
     */
    Pop3Mbx(Mailbox mailbox, Account acct, String query) throws ServiceException {
        mId = mailbox.getId();
        mNumDeleted = 0;
        mDeletedSize = 0;
        mOpContext = new Mailbox.OperationContext(acct);

        if (query == null || query.equals("")) {
           List items = mailbox.getItemList(mOpContext, MailItem.TYPE_MESSAGE, Mailbox.ID_FOLDER_INBOX);
           mMessages = new ArrayList(items.size());           
           for (Iterator it=items.iterator(); it.hasNext(); ) {
               Object obj = it.next();
               if (obj instanceof Message) {
                   Message m = (Message) obj;
                   mTotalSize += m.getSize();
                   mMessages.add(new Pop3Msg(m));
               }
           }
        } else {
            ZimbraQueryResults results;
            mMessages = new ArrayList(500);            
            try {
                results = mailbox.search(mOpContext, query, new byte[] { MailItem.TYPE_MESSAGE }, MailboxIndex.SEARCH_ORDER_DATE_DESC, 500);

                while (results.hasNext()) {
                    ZimbraHit hit = results.getNext();
                    if (hit instanceof MessageHit) {
                        MessageHit mh = (MessageHit) hit;
                        Message m = mh.getMessage();
                        mTotalSize += m.getSize();
                        mMessages.add(new Pop3Msg(m));
                    }
                }
            } catch (IOException e) {
                throw ServiceException.FAILURE(e.getMessage(), e);
            } catch (ParseException e) {
                throw ServiceException.FAILURE(e.getMessage(), e);
            }
        }
    }
    
    /**
     * 
     * @return the zimbra mailbox id
     */
    int getId() {
        return mId;
    }
    
    /**
     * @return total size of all non-deleted messages
     */        
    long getSize() {
        return mTotalSize-mDeletedSize;
    }
    
    /**
     * @return number of undeleted messages
     */
    int getNumMessages() {
        return mMessages.size() - mNumDeleted;
    }
    
    /**
     * @return total number of messages, including deleted.
     */
    int getTotalNumMessages() {
        return mMessages.size();
    }
    
    /**
     * @return number of deleted messages
     */
    int getNumDeletedMessages() {
        return mNumDeleted;
    }

    /**
     * get the message by position in the array, starting at 0, even if it was deleted.
     * 
     * @param index
     * @return
     * @throws Pop3CmdException
     */
    Pop3Msg getMsg(int index) throws Pop3CmdException {
        if (index < 0 || index >= mMessages.size()) 
            throw new Pop3CmdException("invalid message");
        Pop3Msg pm = (Pop3Msg) mMessages.get(index);
        //if (pm.isDeleted()) 
        //    throw new Pop3CmdException("message is deleted");
        return pm;
    }
    
    private int parseInt(String s, String message) throws Pop3CmdException {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new Pop3CmdException(message);
        }
    }

    /**
     * get the undeleted Pop3Msg for the specified msg number (external, starting at 1 index).
     * @param msg
     * @return
     * @throws Pop3CmdException
     * @throws ServiceException
     */
    Pop3Msg getPop3Msg(String msg) throws Pop3CmdException {
        int index = parseInt(msg, "unable to parse msg");
        Pop3Msg pm = getMsg(index-1);
        if (pm.isDeleted())
            throw new Pop3CmdException("message is deleted");
        return pm;
    }

    /**
     * get the undeleted Message for the specified msg number (external, starting at 1 index).
     * @param msg
     * @return
     * @throws Pop3CmdException
     * @throws ServiceException
     */
    Message getMessage(String msg) throws Pop3CmdException, ServiceException {
        Pop3Msg pm = getPop3Msg(msg);
        Mailbox mbox = Mailbox.getMailboxById(mId);
        return mbox.getMessageById(mOpContext, pm.getId());
    }
    
    /**
     * Mark the message as deleted and update counts and mailbox size.
     * @param pm
     */
    public void delete(Pop3Msg pm) {
        if (!pm.isDeleted()) {
            pm.mDeleted = true;
            mNumDeleted++;
            mDeletedSize += pm.getSize();
        }
    }

    /**
     * unmark all messages that were marked as deleted and return the count that were deleted.
     */
    public int undeleteMarked() {
        int count = 0;
        for (int i=0; i < mMessages.size(); i++) {
            Pop3Msg pm = (Pop3Msg) mMessages.get(i);
            if (pm.isDeleted()) {
                mNumDeleted--;
                mDeletedSize -= pm.getSize();
                pm.mDeleted = false;
                count++;
            }
        }
        return count;
    }
    
    /**
     * delete all messages marked as deleted and return number deleted.
     * throws a Pop3CmdException on partial deletes
     * @throws ServiceException
     * @throws Pop3CmdException
     */
    public int deleteMarked(boolean hard) throws ServiceException, Pop3CmdException {
        Mailbox mbox = Mailbox.getMailboxById(mId);
        int count = 0;
        int failed = 0;
        for (int i=0; i < mMessages.size(); i++) {
            Pop3Msg pm = (Pop3Msg) mMessages.get(i);
            if (pm.isDeleted()) {
                try {
                    if (hard) {
                        mbox.delete(mOpContext, pm.getId(), MailItem.TYPE_MESSAGE);                        
                    } else {
                        mbox.move(mOpContext, pm.getId(), MailItem.TYPE_MESSAGE, Mailbox.ID_FOLDER_TRASH);
                    }
                    count++;                    
                } catch (ServiceException e) {
                    failed++;
                }
                mNumDeleted--;
                mDeletedSize -= pm.getSize();
                pm.mDeleted = false;
            }
        }
        if (failed != 0) {
            throw new Pop3CmdException("deleted "+count+"/"+(count+failed)+" message(s)");
        }
        return count;
    }
}
