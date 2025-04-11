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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zimbra.common.util.StringUtil;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.DataSource;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.cs.account.Provisioning.DataSourceBy;
import com.zimbra.cs.db.DbPop3Message;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailboxManager;
import com.zimbra.cs.zclient.ZDataSource;
import com.zimbra.cs.zclient.ZMailbox;
import com.zimbra.cs.zclient.ZPop3DataSource;

import junit.framework.TestCase;


public class TestPop3Import extends TestCase {

    private static final String USER_NAME = "user1";
    private static final String DATA_SOURCE_NAME = "TestPop3Import";
    private static final String TEMP_USER_NAME = "temppop3";

    @Override
    public void setUp()
    throws Exception {
        cleanUp();
        createDataSource();
    }

    /**
     * Tests the UID persistence methods in {@link DbPop3Message}.
     */
    public void testUidPersistence()
    throws Exception {
        DataSource ds = getDataSource();
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);

        // Create set of id's
        Set<String> uids = new HashSet<String>();
        uids.add("1");
        uids.add("2");
        
        // Make sure no id's match
        Set<String> matchingUids = DbPop3Message.getMatchingUids(mbox, ds, uids);
        assertEquals("Test 1: set size", 0, matchingUids.size());
        
        // Store UID 1 and make sure it matches
        DbPop3Message.storeUid(mbox, ds.getId(), "1", 1);
        matchingUids = DbPop3Message.getMatchingUids(mbox, ds, uids);
        assertEquals("Test 2: set size", 1, matchingUids.size());
        assertTrue("Test 2: did not find UID 1", matchingUids.contains("1"));
        
        // Test delete
        DbPop3Message.deleteUids(mbox, ds.getId());
        matchingUids = DbPop3Message.getMatchingUids(mbox, ds, uids);
        assertEquals("Test 3: set size", 0, matchingUids.size());
    }
    
    /**
     * Confirms that the UID database gets cleared when the host name, account
     * name or leave on server flag are changed. 
     */
    public void testModifyDataSource()
    throws Exception {
        // Test modifying host
        ZPop3DataSource zds = (ZPop3DataSource) getZDataSource();
        zds.setHost(zds.getHost() + "2");
        modifyAndCheck(zds);
        
        // Test modifying username
        zds = (ZPop3DataSource) getZDataSource();
        zds.setUsername(zds.getUsername() + "2");
        modifyAndCheck(zds);
        
        // Test modifying leave on server
        zds = (ZPop3DataSource) getZDataSource();
        zds.setLeaveOnServer(!zds.leaveOnServer());
        modifyAndCheck(zds);
    }
    
    /**
     * Confirms that POP3 data is deleted when the mailbox is deleted.  Any leftover POP3
     * data will cause a foreign key violation.
     */
    public void testDeleteMailbox()
    throws Exception {
        // Create temp account and mailbox
        Provisioning prov = Provisioning.getInstance();
        Account account = prov.createAccount(TestUtil.getAddress(TEMP_USER_NAME), "test123", null);
        Mailbox mbox = MailboxManager.getInstance().getMailboxByAccount(account);
        
        // Store bogus POP3 message row and delete mailbox
        DbPop3Message.storeUid(mbox, "TestPop3Import", "uid1", Mailbox.ID_FOLDER_INBOX);
        mbox.deleteMailbox();
    }
    
    private void modifyAndCheck(ZPop3DataSource zds)
    throws Exception {
        Mailbox mbox = TestUtil.getMailbox(USER_NAME);
        DataSource ds = getDataSource();
        ZMailbox zmbox = TestUtil.getZMailbox(USER_NAME);

        // Reinitialize persisted UID's
        DbPop3Message.deleteUids(mbox, ds.getId());
        DbPop3Message.storeUid(mbox, ds.getId(), "1", 1);

        // Modify data source and make sure the existing
        // UID's were deleted
        zmbox.modifyDataSource(zds);
        
        Set<String> uids = new HashSet<String>();
        uids.add("1");
        Set<String> matchingUids = DbPop3Message.getMatchingUids(mbox, ds, uids);
        assertEquals("matching UID's: " + StringUtil.join(",", matchingUids), 0, matchingUids.size());
    }

    private ZDataSource getZDataSource()
    throws Exception {
        ZMailbox mbox = TestUtil.getZMailbox(USER_NAME);
        List<ZDataSource> dataSources = mbox.getAllDataSources();
        for (ZDataSource ds : dataSources) {
            if (ds.getName().equals(DATA_SOURCE_NAME)) {
                return ds;
            }
        }
        fail("Could not find data source " + DATA_SOURCE_NAME);
        return null;
    }
    
    @Override
    public void tearDown()
    throws Exception {
        cleanUp();
    }
    
    private void createDataSource()
    throws Exception {
        Provisioning prov = Provisioning.getInstance();
        Account account = TestUtil.getAccount(USER_NAME);
        Map<String, Object> attrs = new HashMap<String, Object>();
        attrs.put(Provisioning.A_zimbraDataSourceEnabled, Provisioning.FALSE);
        attrs.put(Provisioning.A_zimbraDataSourceHost, "host");
        attrs.put(Provisioning.A_zimbraDataSourcePort, "1");
        attrs.put(Provisioning.A_zimbraDataSourceUsername, "user1");
        attrs.put(Provisioning.A_zimbraDataSourcePassword, "password");
        attrs.put(Provisioning.A_zimbraDataSourceFolderId, Integer.toString(Mailbox.ID_FOLDER_INBOX));
        attrs.put(Provisioning.A_zimbraDataSourceConnectionType, "cleartext");
        attrs.put(Provisioning.A_zimbraDataSourceLeaveOnServer, Provisioning.FALSE);
        prov.createDataSource(account, DataSource.Type.pop3, DATA_SOURCE_NAME, attrs);
    }
    
    private DataSource getDataSource()
    throws Exception {
        Provisioning prov = Provisioning.getInstance();
        Account account = TestUtil.getAccount(USER_NAME);
        return prov.get(account, DataSourceBy.name, DATA_SOURCE_NAME);
    }
    
    private void cleanUp()
    throws Exception {
        // Delete data source
        Provisioning prov = Provisioning.getInstance();
        DataSource ds = getDataSource();
        if (ds != null) {
            Account account = TestUtil.getAccount(USER_NAME);
            prov.deleteDataSource(account, ds.getId());
        }
        
        // Delete temporary account
        Account account = prov.get(AccountBy.name, TestUtil.getAddress(TEMP_USER_NAME));
        if (account != null) {
            prov.deleteAccount(account.getId());
        }
    }
}
