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
package com.zimbra.cs.index;

import java.util.ArrayList;
import java.util.List;

import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.AuthToken;
import com.zimbra.cs.account.AuthTokenException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Server;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.index.MailboxIndex.SortBy;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.soap.SoapProtocol;

class RemoteQueryOperation extends QueryOperation {
    
    RemoteQueryOperation() {}

    private UnionQueryOperation mOp = null;
    private ProxiedQueryResults mResults = null;
    private QueryTarget mTarget = null;

    int getOpType() {
        return OP_TYPE_REMOTE;
    }

    boolean tryAddOredOperation(QueryOperation op) {
        QueryTargetSet targets = op.getQueryTargets();
        assert(targets.countExplicitTargets() == 1);
        assert(targets.hasExternalTargets());

        for (QueryTarget t : targets) {
            assert(t != QueryTarget.LOCAL);
            if (t != QueryTarget.UNSPECIFIED) {
                if (mTarget == null) 
                    mTarget = t; 
                else 
                    if (!mTarget.equals(t))
                        return false;
            }
        }

        assert(mTarget != null);

        if (mOp == null)
            mOp = new UnionQueryOperation();

        mOp.add(op);
        return true;
    }

    String toQueryString() {
        return mOp.toQueryString();
    }

    public String toString() {
        return "REMOTE["+mTarget.toString()+"]:"+mOp.toString();
    }

    QueryTargetSet getQueryTargets() {
        return mOp.getQueryTargets();
    }

    QueryOperation ensureSpamTrashSetting(Mailbox mbox, boolean includeTrash,
                boolean includeSpam) throws ServiceException {

        return mOp.ensureSpamTrashSetting(mbox, includeTrash, includeSpam);
    }

    boolean hasSpamTrashSetting() {
        return mOp.hasSpamTrashSetting();
    }

    void forceHasSpamTrashSetting() {
        mOp.forceHasSpamTrashSetting();
    }

    boolean hasNoResults() {
        return mOp.hasNoResults();
    }

    boolean hasAllResults() {
        return mOp.hasAllResults();
    }

    QueryOperation optimize(Mailbox mbox) throws ServiceException {
        return mOp.optimize(mbox);
    }

    protected QueryOperation combineOps(QueryOperation other, boolean union) {
        return null;
    }

    protected void setup(SoapProtocol proto, Account authenticatedAccount, boolean isAdmin, byte[] types, SortBy searchOrder, int offset, int limit, Mailbox.SearchResultMode mode) throws ServiceException {
        Provisioning prov  = Provisioning.getInstance();
        Account acct = prov.get(AccountBy.id, mTarget.toString());
        if (acct == null)
            throw AccountServiceException.NO_SUCH_ACCOUNT(mTarget.toString());

        Server remoteServer = prov.getServer(acct);

        SearchParams params = new SearchParams();
        params.setSortBy(searchOrder);
        params.setTypes(types);
        params.setOffset(offset);
        params.setLimit(limit);

        if (ZimbraLog.index.isDebugEnabled()) 
            ZimbraLog.index.debug("RemoteQuery of \""+mOp.toQueryString()+"\" sent to "+mTarget.toString()+" on server "+remoteServer.getName());

        params.setQueryStr(mOp.toQueryString());
        try {
            mResults = new ProxiedQueryResults(proto, new AuthToken(authenticatedAccount, isAdmin).getEncoded(), mTarget.toString(), remoteServer.getName(), params, mode);
        } catch (AuthTokenException e) {
            throw ServiceException.FAILURE("AuthTokenException getting auth token: " + e.toString(), e);
        }
    }

    protected void prepare(Mailbox mbx, ZimbraQueryResultsImpl res, MailboxIndex mbidx, SearchParams params, int chunkSize) {
        mParams = params;
    }

    public void resetIterator() throws ServiceException {
        if (mResults != null)
            mResults.resetIterator();
    }

    public ZimbraHit getNext() throws ServiceException {
        if (mResults != null)
            return mResults.getNext();
        else
            return null;
    }

    public ZimbraHit peekNext() throws ServiceException {
        if (mResults != null)
            return mResults.peekNext();
        else
            return null;
    }

    public void doneWithSearchResults() throws ServiceException {
        if (mResults != null)
            mResults.doneWithSearchResults();
    }
    public List<QueryInfo> getResultInfo() {
        if (mResults != null)
            return mResults.getResultInfo();
        else
            return new ArrayList<QueryInfo>();
    }
    
    public int estimateResultSize() throws ServiceException {
        return 0;
    }
}
