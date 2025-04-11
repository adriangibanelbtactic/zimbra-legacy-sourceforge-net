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

package com.zimbra.qa.unittest;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import com.zimbra.common.soap.Element;
import com.zimbra.cs.index.TestSearch;

/**
 * Complete unit test suite for the Zimbra code base.
 * 
 * @author bburtin
 *
 */
public class ZimbraSuite extends TestSuite
{
    private static List<Test> sAdditionalTests = new ArrayList<Test>();

    /**
     * Used by extensions to add additional tests to the main test suite.
     */
    public static void addAdditionalTest(Test test) {
        synchronized (sAdditionalTests) {
            sAdditionalTests.add(test);
        }
    }
    
    /**
     * Runs the entire test suite and writes the output to the specified
     * <code>OutputStream</code>.
     */
    public static TestResult runTestSuite(Element response) {
        TestSuite suite = new TestSuite();

        suite.addTest(new TestSuite(TestWaitSet.class));
        suite.addTest(new TestSuite(TestUtilCode.class));
        suite.addTest(new TestSuite(TestEmailUtil.class));
        suite.addTest(new TestSuite(TestOutOfOffice.class));
        suite.addTest(new TestSuite(TestDbUtil.class));
        suite.addTest(new TestSuite(TestTableMaintenance.class));
        suite.addTest(new TestSuite(TestUnread.class));
        suite.addTest(new TestSuite(TestTags.class));
        suite.addTest(new TestSuite(TestItemCache.class));
        suite.addTest(new TestSuite(TestFolders.class));
        suite.addTest(new TestSuite(TestSpellCheck.class));
        suite.addTest(new TestSuite(TestAuthentication.class));
        suite.addTest(new TestSuite(TestAccount.class));
        suite.addTest(new TestSuite(TestConversion.class));
        suite.addTest(new TestSuite(TestMailItem.class));
        suite.addTest(new TestSuite(TestConcurrency.class));
        suite.addTest(new TestSuite(TestFolderFilterRules.class));
        suite.addTest(new TestSuite(TestTagFilterRules.class));
        suite.addTest(new TestSuite(TestPop3Import.class));
        suite.addTest(new TestSuite(TestFilter.class));
        suite.addTest(new TestSuite(TestPop3ImapAuth.class));
        suite.addTest(new TestSuite(TestContacts.class));
        suite.addTest(new TestSuite(TestTaskScheduler.class));
        suite.addTest(new TestSuite(TestSearch.class));
        suite.addTest(new TestSuite(TestSendAndReceive.class));
        suite.addTest(new TestSuite(TestConnectionPool.class));

        // xxx bburtin: Commenting out IMAP tests, since the new schema hasn't been
        // checked in
        // suite.addTest(new TestSuite(TestImapImport.class));
        // suite.addTest(new TestSuite(TestImapImport.TearDown.class));

        synchronized (sAdditionalTests) {
            for (Test additional : sAdditionalTests) {
                suite.addTest(additional);
            }
        }
        
        return TestUtil.runTest(suite, response);
    }
}
