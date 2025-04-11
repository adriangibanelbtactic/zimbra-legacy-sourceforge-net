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
package com.zimbra.qa.unittest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.zimbra.common.util.CliUtil;
import com.zimbra.common.util.StringUtil;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.common.util.Log.Level;
import com.zimbra.cs.account.DataSource;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.zclient.ZDataSource;
import com.zimbra.cs.zclient.ZFolder;
import com.zimbra.cs.zclient.ZImapDataSource;
import com.zimbra.cs.zclient.ZMailbox;
import com.zimbra.cs.zclient.ZMessage;
import com.zimbra.cs.zclient.ZMailbox.ZImportStatus;

public class TestImapImport
extends TestCase {
    
    private static final String REMOTE_USER_NAME = "testimapimportremote";
    private static final String LOCAL_USER_NAME = "testimapimportlocal";
    private static final String NAME_PREFIX = "TestImapImport";
    private static final String DS_FOLDER_ROOT = "/" + NAME_PREFIX;
    
    // Folder hierarchy: /TestImapImport-f1/TestImapImport-f2, /TestImapImport-f3/TestImapImport-f4
    private static final String REMOTE_PATH_F1 = "/" + NAME_PREFIX + "-f1";
    private static final String REMOTE_PATH_F2 = REMOTE_PATH_F1 + "/" + NAME_PREFIX + "-f2";
    private static final String REMOTE_PATH_F3 = "/" + NAME_PREFIX + "-f3";
    private static final String REMOTE_PATH_F4 = REMOTE_PATH_F3 + "/" + NAME_PREFIX + "-f4";
    
    private static final String LOCAL_PATH_F1 = DS_FOLDER_ROOT + REMOTE_PATH_F1;
    private static final String LOCAL_PATH_F2 = DS_FOLDER_ROOT + REMOTE_PATH_F2;
    private static final String LOCAL_PATH_F3 = DS_FOLDER_ROOT + REMOTE_PATH_F3;
    private static final String LOCAL_PATH_F4 = DS_FOLDER_ROOT + REMOTE_PATH_F4;
    
    private static final String LOCAL_PATH_INBOX = DS_FOLDER_ROOT + "/INBOX";
    private static final String LOCAL_PATH_TRASH = DS_FOLDER_ROOT + "/Trash";
    
    private ZMailbox mRemoteMbox;
    private ZMailbox mLocalMbox;
    private String mOriginalCleartextValue;
    private ZDataSource mDataSource;
    
    public void setUp()
    throws Exception {
        cleanUp();
        
        // Get mailbox references
        if (!TestUtil.accountExists(LOCAL_USER_NAME)) {
            TestUtil.createAccount(LOCAL_USER_NAME);
        }
        if (!TestUtil.accountExists(REMOTE_USER_NAME)) {
            TestUtil.createAccount(REMOTE_USER_NAME);
        }
        mRemoteMbox = TestUtil.getZMailbox(REMOTE_USER_NAME);
        mLocalMbox = TestUtil.getZMailbox(LOCAL_USER_NAME);
        
        // Get or create folder
        ZFolder folder = mLocalMbox.getFolderByPath(DS_FOLDER_ROOT);
        if (folder == null) {
            folder = TestUtil.createFolder(mLocalMbox, NAME_PREFIX);
        }
        
        // Create data source
        int port = Integer.parseInt(TestUtil.getServerAttr(Provisioning.A_zimbraImapBindPort));
        mDataSource = new ZImapDataSource(NAME_PREFIX, true, "localhost",
            port, REMOTE_USER_NAME, TestUtil.DEFAULT_PASSWORD, folder.getId(), DataSource.ConnectionType.cleartext); 
        String id = mLocalMbox.createDataSource(mDataSource);
        mDataSource = null;
        for (ZDataSource ds : mLocalMbox.getAllDataSources()) {
            if (ds.getId().equals(id)) {
                mDataSource = ds;
            }
        }
        assertNotNull(mDataSource);
        
        // Turn on cleartext login
        mOriginalCleartextValue = TestUtil.getServerAttr(Provisioning.A_zimbraImapCleartextLoginEnabled);
        TestUtil.setServerAttr(
            Provisioning.A_zimbraImapCleartextLoginEnabled, Provisioning.TRUE);
    }
    
    public void testImapImport()
    throws Exception {
        // Remote: add 1 message
        ZimbraLog.test.debug("Testing adding message to remote inbox.");
        String remoteQuery = "in:inbox msg1";
        TestUtil.insertMessage(mRemoteMbox, 1, NAME_PREFIX + " msg1");
        checkMsgCount(mRemoteMbox, remoteQuery, 1);
        assertNull(mLocalMbox.getFolderByPath(LOCAL_PATH_INBOX));

        importImap();

        String localInboxQuery = "in:" + LOCAL_PATH_INBOX;
        checkMsgCount(mLocalMbox, localInboxQuery, 1);
        compare();
        
        // Remote: flag message
        ZimbraLog.test.debug("Testing flag.");
        List<ZMessage> msgs = TestUtil.search(mRemoteMbox, remoteQuery);
        assertEquals("Message count in remote inbox", 1, msgs.size());
        String remoteId = msgs.get(0).getId();
        mRemoteMbox.flagMessage(remoteId, true);
        
        // Make sure local copy is not flagged
        msgs = TestUtil.search(mLocalMbox, localInboxQuery);
        assertEquals("Message count in local inbox", 1, msgs.size());
        assertFalse("Local message is flagged", msgs.get(0).isFlagged());
        
        importImap();
        
        // Make sure that local copy is now flagged
        msgs = TestUtil.search(mLocalMbox, localInboxQuery);
        assertEquals("Message count in local inbox", 1, msgs.size());
        assertTrue("Local message is flagged", msgs.get(0).isFlagged());
        compare();
        
        // Remote: move to trash
        ZimbraLog.test.debug("Testing remote move to trash.");
        mRemoteMbox.trashMessage(remoteId);
        checkMsgCount(mRemoteMbox, "in:trash", 1);
        checkMsgCount(mLocalMbox, "in:trash", 0);
        importImap();
        checkMsgCount(mLocalMbox, "in:" + DS_FOLDER_ROOT + "/Trash", 1);
        compare();

        // Create folders on both sides
        ZimbraLog.test.debug("Testing folder creation.");
        TestUtil.createFolder(mRemoteMbox, REMOTE_PATH_F1);
        TestUtil.createFolder(mRemoteMbox, REMOTE_PATH_F2);
        TestUtil.createFolder(mLocalMbox, LOCAL_PATH_F3);
        TestUtil.createFolder(mLocalMbox, LOCAL_PATH_F4);
        importImap();
        
        // Make sure that new folders got created on both sides
        assertNotNull("Local folder " + LOCAL_PATH_F1, mLocalMbox.getFolderByPath(LOCAL_PATH_F1));
        assertNotNull("Local folder " + LOCAL_PATH_F2, mLocalMbox.getFolderByPath(LOCAL_PATH_F2));
        assertNotNull("Remote folder " + REMOTE_PATH_F3, mRemoteMbox.getFolderByPath(REMOTE_PATH_F3));
        assertNotNull("Remote folder " + REMOTE_PATH_F4, mRemoteMbox.getFolderByPath(REMOTE_PATH_F4));
        compare();
        
        // Add message to remote folder and delete local folder at the same time
        ZimbraLog.test.debug("Testing simultaneous message add and folder delete 1.");
        ZFolder remoteFolder2 = mRemoteMbox.getFolderByPath(REMOTE_PATH_F2); 
        TestUtil.insertMessage(mRemoteMbox, 2, NAME_PREFIX + " msg2", remoteFolder2.getId());
        ZFolder localFolder1 = mLocalMbox.getFolderByPath(LOCAL_PATH_F3);
        mLocalMbox.deleteFolder(localFolder1.getId());
        checkMsgCount(mLocalMbox, "in:" + LOCAL_PATH_F2, 0);
        importImap();
        
        // Make sure that remote folders got deleted and that the message was added locally
        assertNull("Remote folder 3", mRemoteMbox.getFolderByPath(REMOTE_PATH_F3));
        assertNull("Remote folder 4", mRemoteMbox.getFolderByPath(REMOTE_PATH_F4));
        checkMsgCount(mLocalMbox, "in:" + LOCAL_PATH_F2, 1);
        compare();
        
        // Add message to a local folder and delete the same folder in remote mailbox
        ZimbraLog.test.debug("Testing simultaneous message add and folder delete 2.");
        ZFolder localFolder = mLocalMbox.getFolderByPath(LOCAL_PATH_F2);
        TestUtil.insertMessage(mLocalMbox, 3, NAME_PREFIX + " msg3", localFolder.getId());
        ZFolder remoteFolder1 = mRemoteMbox.getFolderByPath(REMOTE_PATH_F1);
        mRemoteMbox.deleteFolder(remoteFolder1.getId());
        importImap();
        
        // Make sure the folders were deleted locally and remotely
        assertNull("Local folder 1", mLocalMbox.getFolderByPath(LOCAL_PATH_F1));
        assertNull("Local folder 2", mLocalMbox.getFolderByPath(LOCAL_PATH_F2));
        assertNull("Remote folder 1", mRemoteMbox.getFolderByPath(REMOTE_PATH_F1));
        assertNull("Remote folder 2", mRemoteMbox.getFolderByPath(REMOTE_PATH_F2));
        compare();
        
        // Add message to local inbox
        ZimbraLog.test.debug("Testing sync from local to remote.");
        checkMsgCount(mLocalMbox, "in:" + LOCAL_PATH_INBOX, 0);
        ZFolder localInbox = mLocalMbox.getFolderByPath(LOCAL_PATH_INBOX);
        TestUtil.insertMessage(mLocalMbox, 4, NAME_PREFIX + " msg4", localInbox.getId());
        checkMsgCount(mLocalMbox, "in:" + LOCAL_PATH_INBOX, 1);
        checkMsgCount(mRemoteMbox, "in:inbox", 0);

        // Empty local trash
        checkMsgCount(mLocalMbox, "in:" + LOCAL_PATH_TRASH, 1);
        ZFolder localTrash = mLocalMbox.getFolderByPath(LOCAL_PATH_TRASH);
        mLocalMbox.emptyFolder(localTrash.getId());
        checkMsgCount(mLocalMbox, "in:" + LOCAL_PATH_TRASH, 0);
        checkMsgCount(mRemoteMbox, "in:trash", 1);
        importImap();
        
        // Make sure that local changes got propagated to remote server
        checkMsgCount(mRemoteMbox, "in:inbox msg4", 1);
        checkMsgCount(mRemoteMbox, "in:trash", 0);
        compare();
    }
    
    private void checkMsgCount(ZMailbox mbox, String query, int expectedCount)
    throws Exception {
        List<ZMessage> msgs = TestUtil.search(mbox, query);
        assertEquals("Result size for query '" + query + "'", expectedCount, msgs.size());
    }
    
    private void importImap()
    throws Exception {
        List<ZDataSource> dataSources = new ArrayList<ZDataSource>();
        dataSources.add(mDataSource);
        mLocalMbox.importData(dataSources);
        
        // Wait for import to complete
        ZImportStatus status = null;
        while (true) {
            Thread.sleep(500);
            List<ZImportStatus> statusList = mLocalMbox.getImportStatus();
            assertEquals("Unexpected number of imports running", 1, statusList.size());
            status = statusList.get(0);
            assertEquals("Unexpected data source type", status.getType(), DataSource.Type.imap.name());
            if (!status.isRunning()) {
                break;
            }
        }
        assertTrue("Import failed: " + status.getError(), status.getSuccess());
        
        // Get any state changes from the server 
        mLocalMbox.noOp();
        mRemoteMbox.noOp();
    }

    private void compare()
    throws Exception {
        // Recursively compare the folder trees
        ZFolder folder1 = mRemoteMbox.getUserRoot();
        ZFolder folder2 = mLocalMbox.getFolderByPath(DS_FOLDER_ROOT);
        compare(mRemoteMbox, folder1, mLocalMbox, folder2);
    }
    
    private void compare(ZMailbox mbox1, ZFolder folder1, ZMailbox mbox2, ZFolder folder2)
    throws Exception {
        assertNotNull(mbox1);
        assertNotNull(folder1);
        assertNotNull(mbox2);
        assertNotNull(folder2);
        
        // Recursively compare children
        for (ZFolder child1 : folder1.getSubFolders()) {
            if (isMailFolder(child1)) {
                ZFolder child2 = folder2.getSubFolderByPath(child1.getName());
                String msg = String.format("Could not find folder %s/%s for %s",
                    folder2.getPath(), child1.getName(), mbox2.getName());
                assertNotNull(msg, child2);
                compare(mbox1, child1, mbox2, child2);
            }
        }
        assertEquals("Message count doesn't match", folder1.getMessageCount(), folder2.getMessageCount());
        
        // Compare folders as long as neither one is the user root
        if (!(folder1.getPath().equals("/") || folder2.getPath().equals("/"))) {
            List<ZMessage> msgs1 = TestUtil.search(mbox1, "in:" + folder1.getPath());
            List<ZMessage> msgs2 = TestUtil.search(mbox2, "in:" + folder2.getPath());
            compareMessages(msgs1, msgs2);
        }
    }

    private boolean isMailFolder(ZFolder folder) {
        ZFolder.View view = folder.getDefaultView();
        return view == null || view == ZFolder.View.message || view == ZFolder.View.conversation;
    }
    
    private void compareMessages(List<ZMessage> msgs1, List<ZMessage> msgs2)
    throws Exception {
        // Keep track of message ID's in first set
        Map<String, ZMessage> msgMap = new HashMap<String, ZMessage>();
        for (ZMessage msg : msgs1) {
            msgMap.put(msg.getMessageIdHeader(), msg);
        }
        
        // Compare messages in second set
        for (ZMessage msg2 : msgs2) {
            String id = msg2.getMessageIdHeader();
            ZMessage msg1 = msgMap.remove(id);
            assertNotNull("Found message '" + msg2.getSubject() + "' in mbox2 but not in mbox1", msg1);
            assertEquals("Message content", msg1.getContent(), msg2.getContent());
            assertEquals("Flags for message '" + msg1.getSubject() + "' don't match", msg1.getFlags(), msg2.getFlags());
        }
        
        // Fail if there are any remaining messages
        if (msgMap.size() != 0) {
            List<String> subjects = new ArrayList<String>();
            for (ZMessage msg : msgMap.values()) {
                subjects.add(msg.getSubject());
            }
            fail("Found messages in mbox1 but not in mbox2: " + StringUtil.join(",", subjects));
        }
    }
    
    public void tearDown()
    throws Exception {
        // cleanUp();
        TestUtil.setServerAttr(
            Provisioning.A_zimbraImapCleartextLoginEnabled, mOriginalCleartextValue);
    }
    
    public void cleanUp()
    throws Exception {
        if (TestUtil.accountExists(REMOTE_USER_NAME)) {
            TestUtil.deleteTestData(REMOTE_USER_NAME, NAME_PREFIX);
        }
        if (TestUtil.accountExists(LOCAL_USER_NAME)) {
            ZMailbox mbox = TestUtil.getZMailbox(LOCAL_USER_NAME);
            for (ZDataSource ds : mbox.getAllDataSources()) {
                if (ds.getName().contains(NAME_PREFIX)) {
                    mbox.deleteDataSource(ds);
                }
            }

            TestUtil.deleteTestData(LOCAL_USER_NAME, NAME_PREFIX);
        }
    }
    
    /**
     * Separate class for teardown, since adding/deleting the mailboxes
     * takes a long time.
     */
    public static class TearDown
    extends TestCase {
        
        public void testTeardown()
        throws Exception {
            if (TestUtil.accountExists(LOCAL_USER_NAME)) {
                TestUtil.deleteAccount(LOCAL_USER_NAME);
            }
            if (TestUtil.accountExists(REMOTE_USER_NAME)) {
                TestUtil.deleteAccount(REMOTE_USER_NAME);
            }
        }
    }
    
    public static void main(String[] args)
    throws Exception {
        CliUtil.toolSetup();
        TestUtil.runTest(new TestSuite(TestImapImport.TearDown.class));
        TestUtil.runTest(new TestSuite(TestImapImport.class));
    }
}
