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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.operation;

import java.io.IOException;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.index.queryparser.ParseException;

import com.zimbra.cs.index.MailboxIndex;
import com.zimbra.cs.index.SearchParams;
import com.zimbra.cs.index.ZimbraQueryResults;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.session.Session;
import com.zimbra.soap.SoapProtocol;
import com.zimbra.soap.ZimbraSoapContext;

public class SearchOperation extends Operation {
    private SearchParams mParams;
    private ZimbraQueryResults mResults;
    private SoapProtocol mProto = SoapProtocol.Soap12;

    private static int LOAD = 5;
    static {
        Operation.Config c = loadConfig(SearchOperation.class);
        if (c != null)
            LOAD = c.mLoad;
    }


    public SearchOperation(Session session, OperationContext oc, Mailbox mbox, Requester req, SearchParams params, boolean prefetch, Mailbox.SearchResultMode mode) {
        super(session, oc, mbox, req, req.getPriority(), LOAD);
        mParams = params;
        mParams.setPrefetch(prefetch);
        mParams.setMode(mode);
    }

    public SearchOperation(Session session, OperationContext oc, Mailbox mbox, Requester req, SearchParams params) {
        super(session, oc, mbox, req, req.getPriority(), LOAD);
        mParams = params;
    }

    public SearchOperation(Session session, ZimbraSoapContext zc, OperationContext oc, Mailbox mbox, Requester req, SearchParams params) {
        super(session, oc, mbox, req, req.getPriority(), LOAD);
        mParams = params;
        mProto = zc.getResponseProtocol();
    }


    protected void callback() throws ServiceException {
        // we're going to hide IMAP \Deleted items unless it's an IMAP request
        String query = mParams.getQueryStr();
        if (getRequester() != Requester.IMAP)
            query = "(" + query + ") -tag:\\Deleted";
        
        mParams.setQueryStr(query);

        try {
//            mResults = getMailbox().search(mProto, getOpCtxt(), query, mParams.getTimeZone(), mParams.getLocale(), types, mParams.getSortBy(), mParams.getLimit() + mParams.getOffset(), mPrefetch, mMode);
            
            mResults = getMailbox().search(mProto, getOpCtxt(), mParams);
            
        } catch (IOException e) {
            throw ServiceException.FAILURE("IO error", e);
        } catch (ParseException e) {
            if (e.currentToken != null)
                throw MailServiceException.QUERY_PARSE_ERROR(mParams.getQueryStr(), e, e.currentToken.image, e.currentToken.beginLine, e.currentToken.beginColumn);
            else 
                throw MailServiceException.QUERY_PARSE_ERROR(mParams.getQueryStr(), e, "", -1, -1);
        }
    }

    public ZimbraQueryResults getResults() { return mResults; }


    public String toString() {
        return super.toString()+" offset="+mParams.getOffset()+" limit="+mParams.getLimit();
    }
}
