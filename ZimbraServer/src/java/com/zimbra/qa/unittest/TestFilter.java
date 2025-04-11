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
import java.util.List;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import com.zimbra.common.soap.SoapFaultException;
import com.zimbra.common.util.CliUtil;
import com.zimbra.cs.zclient.ZFilterAction;
import com.zimbra.cs.zclient.ZFilterCondition;
import com.zimbra.cs.zclient.ZFilterRule;
import com.zimbra.cs.zclient.ZFilterRules;
import com.zimbra.cs.zclient.ZMailbox;
import com.zimbra.cs.zclient.ZMessage;
import com.zimbra.cs.zclient.ZTag;
import com.zimbra.cs.zclient.ZFilterAction.MarkOp;
import com.zimbra.cs.zclient.ZFilterAction.ZFileIntoAction;
import com.zimbra.cs.zclient.ZFilterAction.ZKeepAction;
import com.zimbra.cs.zclient.ZFilterAction.ZMarkAction;
import com.zimbra.cs.zclient.ZFilterAction.ZTagAction;
import com.zimbra.cs.zclient.ZFilterCondition.HeaderOp;
import com.zimbra.cs.zclient.ZFilterCondition.ZHeaderCondition;
import com.zimbra.cs.zclient.ZMessage.Flag;


public class TestFilter
extends TestCase {

    private static String USER_NAME = "user1";
    private static String NAME_PREFIX = "TestFilter";
    private static String TAG_TEST_BASE64_SUBJECT = NAME_PREFIX + "-testBase64Subject";
    private static String FOLDER1_NAME = NAME_PREFIX + "-folder1";
    private static String FOLDER2_NAME = NAME_PREFIX + "-folder2";
    private static String FOLDER1_PATH = "/" + FOLDER1_NAME;
    private static String FOLDER2_PATH = "/" + FOLDER2_NAME;
    private static String TAG1_NAME = NAME_PREFIX + "-tag1";
    private static String TAG2_NAME = NAME_PREFIX + "-tag2";
    
    private ZMailbox mMbox;
    private ZFilterRules mOriginalRules;

    public void setUp()
    throws Exception {
        mMbox = TestUtil.getZMailbox(USER_NAME);
        cleanUp();
        mOriginalRules = mMbox.getFilterRules();
        mMbox.saveFilterRules(getRules());
    }
    
    public void testQuoteValidation()
    throws Exception {
        List<ZFilterCondition> conditions = new ArrayList<ZFilterCondition>();
        List<ZFilterAction> actions = new ArrayList<ZFilterAction>();
        List<ZFilterRule> rules = new ArrayList<ZFilterRule>();

        // if subject contains "a " b", keep
        ZFilterCondition condition = new ZHeaderCondition("subject", HeaderOp.CONTAINS, "a \" b");
        ZFilterAction action = new ZKeepAction();
        conditions.add(condition);
        actions.add(action);
        rules.add(new ZFilterRule("test quotes", true, false, conditions, actions));
        
        ZFilterRules zRules = new ZFilterRules(rules);
        try {
            mMbox.saveFilterRules(zRules);
            fail("Saving filter rules with quotes should not have succeeded");
        } catch (SoapFaultException e) {
            assertTrue("Unexpected exception: " + e, e.getMessage().contains("Doublequote not allowed"));
        }
        
        // if subject contains "a \ b", keep
        conditions.clear();
        conditions.add(new ZHeaderCondition("subject", HeaderOp.CONTAINS, "a \\ b"));
        rules.clear();
        rules.add(new ZFilterRule("test backslash", true, false, conditions, actions));
        try {
            mMbox.saveFilterRules(zRules);
            fail("Saving filter rules with backslash should not have succeeded");
        } catch (SoapFaultException e) {
            assertTrue("Unexpected exception: " + e, e.getMessage().contains("Backslash not allowed"));
        }
    }
    
    /**
     * Confirms that a message with a base64-encoded subject can be filtered correctly
     * (bug 11219).
     */
    public void testBase64Subject()
    throws Exception {
        // Note: tag gets created implicitly when filter rules are saved
        String address = TestUtil.getAddress(USER_NAME);
        TestUtil.insertMessageLmtp(1,
            "=?UTF-8?B?W2l0dnNmLUluY2lkZW5jaWFzXVs0OTc3Ml0gW2luY2lkZW5jaWFzLXZpbGxhbnVldmFdIENvcnRlcyBkZSBsdXosIGTDrWEgMjUvMDkvMjAwNi4=?=",
            address, address);
        List<ZMessage> messages = TestUtil.search(mMbox, "villanueva");
        assertEquals("Unexpected number of messages", 1, messages.size());
        List<ZTag> tags = mMbox.getTags(messages.get(0).getTagIds());
        assertEquals("Unexpected number of tags", 1, tags.size());
        assertEquals("Tag didn't match", TAG_TEST_BASE64_SUBJECT, tags.get(0).getName());
    }

    /**
     * Confirms that all actions are performed when a message matches multiple
     * filter rules.
     */
    public void testMatchMultipleFilters()
    throws Exception {
        String sender = TestUtil.getAddress("multiplefilters");
        String recipient = TestUtil.getAddress(USER_NAME);
        String subject = NAME_PREFIX + " This goes into folder1 and folder2";
        TestUtil.insertMessageLmtp(1, subject, recipient, sender);
        
        ZMessage msg = TestUtil.getMessage(mMbox, "in:" + FOLDER1_PATH + " " + subject);
        TestUtil.verifyTag(mMbox, msg, TAG1_NAME);
        TestUtil.verifyTag(mMbox, msg, TAG2_NAME);
        TestUtil.verifyFlag(mMbox, msg, Flag.flagged);
        
        msg = TestUtil.getMessage(mMbox, "in:" + FOLDER2_PATH + " " + subject);
        TestUtil.verifyTag(mMbox, msg, TAG1_NAME);
        TestUtil.verifyTag(mMbox, msg, TAG2_NAME);
        TestUtil.verifyFlag(mMbox, msg, Flag.flagged);
    }
    
    /**
     * Verifies the fix to bug 5455.  Tests sending a message that matches
     * two filter rules, each of which has a tag action and a flag action.   
     */
    public void testBug5455()
    throws Exception {
        String recipient = TestUtil.getAddress(USER_NAME);
        String subject = NAME_PREFIX + "Testing bug5455";
        TestUtil.insertMessageLmtp(1, subject, recipient, recipient);
        
        ZMessage msg = TestUtil.getMessage(mMbox, "in:" + FOLDER1_PATH + " " + subject);
        TestUtil.verifyFlag(mMbox, msg, Flag.flagged);
        TestUtil.verifyTag(mMbox, msg, TAG1_NAME);
        
        msg = TestUtil.getMessage(mMbox, "in:" + FOLDER2_PATH + " " + subject);
        TestUtil.verifyFlag(mMbox, msg, Flag.flagged);
        TestUtil.verifyTag(mMbox, msg, TAG1_NAME);
    }
    
    protected void tearDown() throws Exception {
        mMbox.saveFilterRules(mOriginalRules);
        cleanUp();
    }

    private void cleanUp()
    throws Exception {
        TestUtil.deleteTestData(USER_NAME, NAME_PREFIX);
        
        // Clean up messages created by testBase64Subject()
        for (ZMessage msg : TestUtil.search(mMbox, "villanueva")) {
            mMbox.deleteMessage(msg.getId());
        }
    }
    
    private ZFilterRules getRules()
    throws Exception {
        List<ZFilterRule> rules = new ArrayList<ZFilterRule>();

        // if subject contains "villanueva", tag with testBase64Subject and stop
        List<ZFilterCondition> conditions = new ArrayList<ZFilterCondition>();
        List<ZFilterAction> actions = new ArrayList<ZFilterAction>();
        conditions.add(new ZHeaderCondition("subject", HeaderOp.CONTAINS, "villanueva"));
        actions.add(new ZTagAction(TAG_TEST_BASE64_SUBJECT));
        rules.add(new ZFilterRule("testBase64Subject", true, false, conditions, actions));

        // if subject contains "folder1", file into folder1 and tag with tag1
        conditions = new ArrayList<ZFilterCondition>();
        actions = new ArrayList<ZFilterAction>();
        conditions.add(new ZHeaderCondition("subject", HeaderOp.CONTAINS, "folder1"));
        actions.add(new ZFileIntoAction(FOLDER1_PATH));
        actions.add(new ZTagAction(TAG1_NAME));
        rules.add(new ZFilterRule("testMatchMultipleFilters1", true, false, conditions, actions));

        // if from contains "multiplefilters", file into folder 2, tag with tag2 and flag
        conditions = new ArrayList<ZFilterCondition>();
        actions = new ArrayList<ZFilterAction>();
        conditions.add(new ZHeaderCondition("from", HeaderOp.CONTAINS, "multiplefilters"));
        actions.add(new ZFileIntoAction(FOLDER2_PATH));
        actions.add(new ZTagAction(TAG2_NAME));
        actions.add(new ZMarkAction(MarkOp.FLAGGED));
        rules.add(new ZFilterRule("testMatchMultipleFilters2", true, false, conditions, actions));
        
        // if subject contains bug5455, flag, file into folder1, tag with tag1, file into folder2
        conditions = new ArrayList<ZFilterCondition>();
        actions = new ArrayList<ZFilterAction>();
        conditions.add(new ZHeaderCondition("subject", HeaderOp.CONTAINS, "bug5455"));
        actions.add(new ZMarkAction(MarkOp.FLAGGED));
        actions.add(new ZFileIntoAction(FOLDER1_PATH));
        actions.add(new ZTagAction(TAG1_NAME));
        actions.add(new ZFileIntoAction(FOLDER2_PATH));
        rules.add(new ZFilterRule("testBug5455", true, false, conditions, actions));
        
        return new ZFilterRules(rules);
    }

    public static void main(String[] args)
    throws Exception {
        CliUtil.toolSetup();
        TestUtil.runTest(new TestSuite(TestFilter.class));
    }
}
