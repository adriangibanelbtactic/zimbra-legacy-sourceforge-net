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
package com.zimbra.cs.service.formatter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import javax.servlet.ServletException;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.index.MailboxIndex;
import com.zimbra.cs.index.ZimbraHit;
import com.zimbra.cs.index.ZimbraQueryResults;
import com.zimbra.cs.index.queryparser.ParseException;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.operation.Operation;
import com.zimbra.cs.service.UserServlet;
import com.zimbra.cs.service.UserServletException;
import com.zimbra.cs.service.UserServlet.Context;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;

public abstract class Formatter {

    public abstract String getType();

    public String[] getDefaultMimeTypes() {
        return new String[0];
    }

    /**
     * @return true if this formatter requires auth
     */
    public boolean requiresAuth() {
        return true;
    }
    
    /**
     * 
     * @return true if this formatter can be blocked by zimbraAttachmentsBlocked attr.
     */
    public abstract boolean canBeBlocked();
    
    // eventually get this from query param ?start=long|YYYYMMMDDHHMMSS
    public long getDefaultStartTime() {
        return -1;
    }
    
    public long getDefaultEndTime() {
        return -1;
    }
    
    public String getDefaultSearchTypes() {
        return MailboxIndex.SEARCH_FOR_MESSAGES;
    }

    public final void format(UserServlet.Context context, MailItem item) throws UserServletException, IOException, ServletException, ServiceException {
        try {
            FormatOperation op = new FormatOperation(context, item, this);
            op.schedule();
        } catch (ServiceException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UserServletException)
                throw (UserServletException) cause;
            if (cause instanceof ServletException)
                throw (ServletException) cause;
            if (cause instanceof IOException)
                throw (IOException) cause;
            throw e;
        }
    }

    public final void save(byte[] body, UserServlet.Context context, Folder folder) throws UserServletException, IOException, ServletException, ServiceException {
        try {
            SaveOperation op = new SaveOperation(body, context, folder, this);
            op.schedule();
        } catch (ServiceException e) {
            Throwable cause = e.getCause();
            if (cause instanceof UserServletException)
                throw (UserServletException) cause;
            if (cause instanceof ServletException)
                throw (ServletException) cause;
            if (cause instanceof IOException)
                throw (IOException) cause;
            throw e;
        }
    }
    
    public abstract void formatCallback(UserServlet.Context context, MailItem item) throws UserServletException, ServiceException, IOException, ServletException;

    public abstract void saveCallback(byte[] body, UserServlet.Context context, Folder folder) throws UserServletException, ServiceException, IOException, ServletException;

    public Iterator<? extends MailItem> getMailItems(Context context, MailItem item, long startTime, long endTime, long chunkSize) throws ServiceException {
        String query = context.getQueryString();
        if (query != null) {
            try {
                if (item instanceof Folder) {
                    Folder f = (Folder) item;
                    ZimbraLog.misc.info("folderId: " + f.getId());
                    if (f.getId() != Mailbox.ID_FOLDER_USER_ROOT)
                        query = "in:" + f.getPath() + " " + query; 
                }
                ZimbraLog.misc.info("query: " + query);
                String searchTypes = context.getTypesString();
                if (searchTypes == null)
                    searchTypes = getDefaultSearchTypes();
                byte[] types = MailboxIndex.parseTypesString(searchTypes);
                ZimbraQueryResults results = context.targetMailbox.search(context.opContext, query, types, MailboxIndex.SortBy.DATE_DESCENDING, context.getOffset() + context.getLimit());
                return new QueryResultIterator(results);                
            } catch (IOException e) {
                // TODO Auto-generated catch block
                throw ServiceException.FAILURE("search error", e);
            } catch (ParseException e) {
                throw ServiceException.FAILURE("search error", e);
            }
        } else if (item instanceof Folder) {
            Collection<? extends MailItem> items = getMailItemsFromFolder(context, (Folder) item, startTime, endTime, chunkSize);
            return items != null ? items.iterator() : null;
        } else {
            ArrayList<MailItem> result = new ArrayList<MailItem>();
            result.add(item);
            return result.iterator();
        }
    }

    private Collection<? extends MailItem> getMailItemsFromFolder(Context context, Folder folder, long startTime, long endTime, long chunkSize) throws ServiceException {
        switch (folder.getDefaultView()) {
            case MailItem.TYPE_APPOINTMENT:
            case MailItem.TYPE_TASK:
                return context.targetMailbox.getCalendarItemsForRange(context.opContext, startTime, endTime, folder.getId(), null);
            case MailItem.TYPE_CONTACT:
                return context.targetMailbox.getContactList(context.opContext, folder.getId());
            case MailItem.TYPE_WIKI:
                return context.targetMailbox.getWikiList(context.opContext, folder.getId());
            default:
                return context.targetMailbox.getItemList(context.opContext, MailItem.TYPE_MESSAGE, folder.getId());
        }
    }
 
    /**
     * 
     * @param attr
     * @param accountId
     * @return
     * @throws ServletException
     */
    public static boolean checkGlobalOverride(String attr, Account account) throws ServletException {
        Provisioning prov = Provisioning.getInstance();
        try {
            return prov.getConfig().getBooleanAttr(attr, false)
                    || account.getBooleanAttr(attr, false);
        } catch (ServiceException e) {
            throw new ServletException(e);
        }
    }

    protected static class QueryResultIterator implements Iterator<MailItem> {

        private ZimbraQueryResults mResults;
        
        QueryResultIterator(ZimbraQueryResults results) {
            mResults = results;
        }

        public boolean hasNext() {
            if (mResults == null)
                return false;
            try {
                return mResults.hasNext();
            } catch (ServiceException e) {
                ZimbraLog.misc.warn("caught exception", e);
                return false;
            }
        }

        public MailItem next() {
            if (mResults == null)
                return null;
            try {
                ZimbraHit hit = mResults.getNext();
                if (hit != null)
                    return hit.getMailItem();
            } catch (ServiceException e) {
                ZimbraLog.misc.warn("caught exception", e);                
            }
            return null;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void finished() {
            try {
                if (mResults != null)
                    mResults.doneWithSearchResults();
            } catch (ServiceException e) { }
            mResults = null;
        }
    }
    
    abstract int getFormatLoad();
    abstract int getSaveLoad();
    
    protected static class FormatOperation extends Operation {
        Context mContext;
        MailItem mItem;
        Formatter mFmt;

        public FormatOperation(Context context, MailItem item, Formatter fmt) {
            super(null, context.opContext, context.targetMailbox, Requester.REST, fmt.getFormatLoad());
            mContext = context;
            mItem = item;
            mFmt = fmt;
        }

        public void callback() throws ServiceException {
            try {
                mFmt.formatCallback(mContext, mItem);
            } catch (IOException e) {
                throw ServiceException.FAILURE("Caught IOException", e);
            } catch (ServletException e) {
                throw ServiceException.FAILURE("Caught ServletException", e); 
            } catch (UserServletException e) {
                throw ServiceException.FAILURE("Caught UserServletException", e); 
            }
        }
    }
        
    protected static class SaveOperation extends Operation {
        byte[] mBody;
        Context mContext;
        Folder mFolder;
        Formatter mFmt;

        public SaveOperation(byte[] body, Context context, Folder folder, Formatter fmt) {
            super(null, context.opContext, context.targetMailbox, Requester.REST, fmt.getSaveLoad());
            mBody = body;
            mContext = context;
            mFolder = folder;
            mFmt = fmt;
        }

        public void callback() throws ServiceException {
            try {
                mFmt.saveCallback(mBody, mContext, mFolder);
            } catch (IOException e) {
                throw ServiceException.FAILURE("Caught IOException", e);
            } catch (ServletException e) {
                throw ServiceException.FAILURE("Caught ServletException", e); 
            } catch (UserServletException e) {
                throw ServiceException.FAILURE("Caught UserServletException", e); 
            }
        }
    }

    
}
