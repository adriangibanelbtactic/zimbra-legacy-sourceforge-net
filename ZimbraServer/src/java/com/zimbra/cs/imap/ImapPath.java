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
package com.zimbra.cs.imap;

import java.io.UnsupportedEncodingException;

import com.zimbra.common.service.ServiceException;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.AccountServiceException;
import com.zimbra.cs.account.AuthToken;
import com.zimbra.cs.account.AuthTokenException;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.mailbox.ACL;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.MailServiceException;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.mailbox.SearchFolder;
import com.zimbra.cs.mailbox.Mailbox.OperationContext;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.cs.util.AccountUtil;
import com.zimbra.cs.zclient.ZFolder;
import com.zimbra.cs.zclient.ZMailbox;
import com.zimbra.cs.zclient.ZSearchFolder;

public class ImapPath {
    static final String NAMESPACE_PREFIX = "/home/";

    private ImapCredentials mCredentials;
    private String mOwner;
    private String mPath;
    private Object mMailbox;
    private Object mFolder;

    /** Takes a user-supplied IMAP mailbox path and converts it to a Zimbra
     *  folder pathname.  Applies all special, hack-specific folder mappings.
     *  Does <b>not</b> do IMAP-UTF-7 decoding; this is assumed to have been
     *  already done by the appropriate method in {@link ImapRequest}.
     *  
     * @param imapPath   The client-provided logical IMAP pathname.
     * @param creds      The authenticated user's login credentials.
     * @see #exportPath(String, ImapCredentials) */
    ImapPath(String imapPath, ImapCredentials creds) {
        mCredentials = creds;
        mPath = imapPath;

        if (imapPath.toLowerCase().startsWith(NAMESPACE_PREFIX)) {
            imapPath = imapPath.substring(NAMESPACE_PREFIX.length());
            if (!imapPath.equals("") && !imapPath.startsWith("/")) {
                int slash = imapPath.indexOf('/');
                mOwner = (slash == -1 ? imapPath : imapPath.substring(0, slash)).toLowerCase();
                mPath = (slash == -1 ? "" : imapPath.substring(slash));
            }
        }

        while (mPath.startsWith("/"))
            mPath = mPath.substring(1);
        while (mPath.endsWith("/"))
            mPath = mPath.substring(0, mPath.length() - 1);

        // Windows Mobile 5 hack: server must map "Sent Items" to "Sent"
        String lcname = mPath.toLowerCase();
        if (creds != null && creds.isHackEnabled(ImapCredentials.EnabledHack.WM5)) {
            if (lcname.startsWith("sent items") && (lcname.length() == 10 || lcname.charAt(10) == '/'))
                mPath = "Sent" + mPath.substring(10);
        }
    }

    ImapPath(String owner, String zimbraPath, ImapCredentials session) {
        mCredentials = session;
        mOwner = owner == null ? null : owner.toLowerCase();
        mPath = zimbraPath.startsWith("/") ? zimbraPath.substring(1) : zimbraPath;
    }

    ImapPath(String owner, Folder folder, ImapCredentials session) {
        this(owner, folder.getPath(), session);
        mMailbox = folder.getMailbox();
        mFolder = folder;
    }

    ImapPath(String owner, ZMailbox zmbx, ZFolder zfolder, ImapCredentials session) {
        this(owner, zfolder.getPath(), session);
        mMailbox = zmbx;
        mFolder = zfolder;
    }


    public boolean isEquivalent(ImapPath other) {
        if (!mPath.equalsIgnoreCase(other.mPath))
            return false;
        if (mOwner == other.mOwner || (mOwner != null && mOwner.equalsIgnoreCase(other.mOwner)))
            return true;
        try {
            Account acct = getOwnerAccount(), otheracct = other.getOwnerAccount();
            return (acct == null || otheracct == null ? false : acct.getId().equalsIgnoreCase(otheracct.getId()));
        } catch (ServiceException e) {
            return false;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ImapPath))
            return super.equals(obj);
        return asImapPath().equalsIgnoreCase(((ImapPath) obj).asImapPath());
    }

    @Override
    public int hashCode() {
        return (mOwner == null ? 0 : mOwner.toUpperCase().hashCode()) ^ mPath.toUpperCase().hashCode() ^ (mCredentials == null ? 0 : mCredentials.hashCode());
    }

    ImapPath canonicalize() throws ServiceException {
        if (getFolder() instanceof Folder)
            mPath = ((Folder) getFolder()).getPath();
        else
            mPath = ((ZFolder) getFolder()).getPath();

        while (mPath.startsWith("/"))
            mPath = mPath.substring(1);
        return this;
    }


    String getOwner() {
        return mOwner;
    }

    ImapCredentials getCredentials() {
        return mCredentials;
    }

    boolean belongsTo(Mailbox mbox) throws ServiceException {
        return belongsTo(mbox.getAccountId());
    }

    boolean belongsTo(ImapCredentials creds) throws ServiceException {
        return belongsTo(creds.getAccountId());
    }

    boolean belongsTo(String accountId) throws ServiceException {
        Account owner = getOwnerAccount();
        return owner != null && owner.getId().equalsIgnoreCase(accountId);
    }

    String getOwnerAccountId() throws ServiceException {
        if (mOwner == null && mCredentials != null)
            return mCredentials.getAccountId();
        else if (mOwner == null)
            return null;
        Account acct = getOwnerAccount();
        return acct == null ? null : acct.getId();
    }

    Account getOwnerAccount() throws ServiceException {
        if (mOwner != null)
            return Provisioning.getInstance().get(AccountBy.name, mOwner);
        else if (mCredentials != null)
            return Provisioning.getInstance().get(AccountBy.id, mCredentials.getAccountId());
        else
            return null;
    }

    boolean onLocalServer() throws ServiceException {
        return onLocalServer(getOwnerAccount());
    }

    boolean onLocalServer(Account acct) throws ServiceException {
        return acct != null && Provisioning.onLocalServer(acct);
    }

    Object getOwnerMailbox() throws ServiceException {
        if (mMailbox == null) {
            Account target = getOwnerAccount();
            if (target == null)
                mMailbox = null;
            else if (Provisioning.onLocalServer(target))
                mMailbox = MailboxManager.getInstance().getMailboxByAccount(target);
            else if (mCredentials == null)
                mMailbox = null;
            else
                mMailbox = getOwnerZMailbox();
        }
        return mMailbox;
    }

    ZMailbox getOwnerZMailbox() throws ServiceException {
        if (mMailbox instanceof ZMailbox)
            return (ZMailbox) mMailbox;
        if (mCredentials == null)
            return null;

        Account target = getOwnerAccount();
        Account acct = Provisioning.getInstance().get(AccountBy.id, mCredentials.getAccountId());
        try {
            ZMailbox.Options options = new ZMailbox.Options(new AuthToken(acct).getEncoded(), AccountUtil.getSoapUri(target));
            options.setTargetAccount(target.getName());
            options.setNoSession(true);
            return ZMailbox.getMailbox(options);
        } catch (AuthTokenException ate) {
            throw ServiceException.FAILURE("error generating auth token", ate);
        }
    }

    Object getFolder() throws ServiceException {
        if (mFolder == null) {
            Object mboxobj = getOwnerMailbox();
            if (mboxobj instanceof Mailbox) {
                mFolder = ((Mailbox) mboxobj).getFolderByPath(mCredentials == null ? null : mCredentials.getContext(), asZimbraPath());
            } else if (mboxobj instanceof ZMailbox) {
                mFolder = ((ZMailbox) mboxobj).getFolderByPath(asZimbraPath());
                if (mFolder == null)
                    throw MailServiceException.NO_SUCH_FOLDER(asImapPath());
            } else {
                throw AccountServiceException.NO_SUCH_ACCOUNT(getOwner());
            }
        }
        return mFolder;
    }


    boolean isCreatable() {
        String path = mPath.toLowerCase();
        return !path.matches("\\s*notebook\\s*(/.*)?") &&
               !path.matches("\\s*contacts\\s*(/.*)?") &&
               !path.matches("\\s*calendar\\s*(/.*)?");
    }

    /** Returns whether the server can return the <tt>READ-WRITE</tt> response
     *  code when the folder referenced by this path is <tt>SELECT</tt>ed. */
    boolean isWritable() throws ServiceException {
        // RFC 4314 5.2: "The server SHOULD include a READ-WRITE response code in the tagged OK
        //                response if at least one of the "i", "e", or "shared flag rights" is
        //                granted to the current user."
        return isWritable(ACL.RIGHT_DELETE) || isWritable(ACL.RIGHT_INSERT) || isWritable(ACL.RIGHT_WRITE);
    }

    /** Returns <tt>true</tt> if all of the specified rights have been granted
     *  on the folder referenced by this path to the authenticated user. */
    boolean isWritable(short rights) throws ServiceException {
        if (!isSelectable())
            return false;

        if (getFolder() instanceof Folder) {
            Folder folder = (Folder) mFolder;
            if (folder instanceof SearchFolder || folder.getDefaultView() == MailItem.TYPE_CONTACT)
                return false;
        } else {
            ZFolder zfolder = (ZFolder) mFolder;
            if (zfolder instanceof ZSearchFolder || zfolder.getDefaultView() == ZFolder.View.contact)
                return false;
        }

        if (rights == 0)
            return true;
        return (getFolderRights() & rights) == rights;
    }

    short getFolderRights() throws ServiceException {
        if (getFolder() instanceof Folder) {
            OperationContext octxt = mCredentials == null ? null : mCredentials.getContext();
            Folder folder = (Folder) mFolder;
            return folder.getMailbox().getEffectivePermissions(octxt, folder.getId(), folder.getType());
        } else {
            ZFolder zfolder = (ZFolder) mFolder;
            String rights = zfolder.getEffectivePerms();
            return rights == null ? ~0 : ACL.stringToRights(rights);
        }
    }

    boolean isSelectable() throws ServiceException {
        if (!isVisible())
            return false;

        if (getFolder() instanceof Folder)
            return !((Folder) mFolder).isTagged(((Folder) mFolder).getMailbox().mDeletedFlag);
        else
            return !((ZFolder) mFolder).isIMAPDeleted();
    }

    boolean isVisible() throws ServiceException {
        if (mCredentials != null && mCredentials.isHackEnabled(ImapCredentials.EnabledHack.WM5)) {
            String lcname = mPath.toLowerCase();
            if (lcname.startsWith("sent items") && (lcname.length() == 10 || lcname.charAt(10) == '/'))
                return false;
        }

        try {
            getFolder();
        } catch (ServiceException e) {
            if (ServiceException.PERM_DENIED.equals(e.getCode()))
                return false;
            throw e;
        }

        if (getFolder() instanceof Folder) {
            Folder folder = (Folder) mFolder;
            if (folder.getId() == Mailbox.ID_FOLDER_USER_ROOT)
                return false;
            byte view = folder.getDefaultView();
            if (view == MailItem.TYPE_APPOINTMENT || view == MailItem.TYPE_TASK || view == MailItem.TYPE_WIKI || view == MailItem.TYPE_DOCUMENT)
                return false;
            if (folder instanceof SearchFolder)
                return ((SearchFolder) folder).isImapVisible();
        } else {
            ZFolder zfolder = (ZFolder) mFolder;
            if (new ItemId(zfolder.getId(), (String) null).getId() == Mailbox.ID_FOLDER_USER_ROOT)
                return false;
            ZFolder.View view = zfolder.getDefaultView();
            if (view == ZFolder.View.appointment || view == ZFolder.View.task || view == ZFolder.View.wiki || view == ZFolder.View.document)
                return false;
            if (zfolder instanceof ZSearchFolder)
                return false;
        }
        return true;
    }


    String asZimbraPath() {
        return mPath;
    }

    @Override
    public String toString() {
        return asImapPath();
    }

    /** Formats a folder path as an IMAP-UTF-7 quoted-string.  Applies all
     *  special hack-specific path transforms.
     * @param mPath     The Zimbra-local folder pathname.
     * @param mCredentials  The authenticated user's current session.
     * @see #importPath(String, ImapCredentials) */
    String asImapPath() {
        String path = mPath, lcpath = path.toLowerCase();
        // make sure that the Inbox is called "INBOX", regardless of how we capitalize it
        if (lcpath.startsWith("inbox") && (lcpath.length() == 5 || lcpath.charAt(5) == '/')) {
            path = "INBOX" + path.substring(5);
        } else if (mCredentials != null && mCredentials.isHackEnabled(ImapCredentials.EnabledHack.WM5)) {
            if (lcpath.startsWith("sent") && (lcpath.length() == 4 || lcpath.charAt(4) == '/'))
                path = "Sent Items" + path.substring(4);
        }

        if (mOwner != null && !mOwner.equals(""))
            path = NAMESPACE_PREFIX + mOwner + (path.equals("") ? "" : "/") + path;
        return path;
    }

    String asUtf7String() {
        String path = asImapPath();
        try {
            path = '"' + new String(path.getBytes("imap-utf-7"), "US-ASCII") + '"';
        } catch (UnsupportedEncodingException e) {
            path = '"' + path + '"';
        }
        return path.replaceAll("\\\\", "\\\\\\\\");
    }
}
