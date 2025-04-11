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
 * Portions created by Zimbra are Copyright (C) 2004, 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on Nov 11, 2004
 *
 */
package com.zimbra.cs.filter.jsieve;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ListIterator;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.jsieve.Arguments;
import org.apache.jsieve.SieveException;
import org.apache.jsieve.StringListArgument;
import org.apache.jsieve.SyntaxException;
import org.apache.jsieve.TagArgument;
import org.apache.jsieve.mail.MailAdapter;
import org.apache.jsieve.tests.AbstractTest;

import com.zimbra.cs.filter.ZimbraMailAdapter;

/**
 * @author kchen
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class DateTest extends AbstractTest {
    
    static DateFormat mShortDateFormat = new SimpleDateFormat("yyyyMMdd");
    static final String BEFORE = ":before";
    static final String AFTER = ":after";

    /* (non-Javadoc)
     * @see org.apache.jsieve.tests.AbstractTest#executeBasic(org.apache.jsieve.mail.MailAdapter, org.apache.jsieve.Arguments)
     */
    protected boolean executeBasic(MailAdapter mail, Arguments arguments)
            throws SieveException {
        String comparator = null;
        Date date = null;
        ListIterator argumentsIter = arguments.getArgumentList().listIterator();

        // First argument MUST be a tag of ":before" or ":after"
        if (argumentsIter.hasNext())
        {
            Object argument = argumentsIter.next();
            if (argument instanceof TagArgument)
            {
                String tag = ((TagArgument) argument).getTag();
                if (tag.equals(BEFORE) || tag.equals(AFTER))
                    comparator = tag;
                else
                    throw new SyntaxException(
                        "Found unexpected: \"" + tag + "\"");
            }
        }
        if (null == comparator)
            throw new SyntaxException("Expecting \"" + BEFORE + "\" or \"" + AFTER + "\"");

        // Second argument MUST be a date
        if (argumentsIter.hasNext())
        {
            Object argument = argumentsIter.next();
            if (argument instanceof StringListArgument) {
                StringListArgument strList = (StringListArgument) argument;
                String datestr = (String) strList.getList().get(0);
                try {
                    date = mShortDateFormat.parse(datestr);
                } catch (ParseException e) {
                    
                }
            }
        }
        if (null == date)
            throw new SyntaxException("Expecting a valid date (yyyyMMdd)");

        // There MUST NOT be any further arguments
        if (argumentsIter.hasNext())
            throw new SyntaxException("Found unexpected argument(s)");               
        
        if (!(mail instanceof ZimbraMailAdapter))
            return false;
        return test(mail, comparator, date);
    }
    
    protected void validateArguments(Arguments arguments) throws SieveException {
        // already done
    }

    private boolean test(MailAdapter mail, String comparator, Date date) throws SieveException {
        // get the date from the mail
        MimeMessage mimeMsg = ((ZimbraMailAdapter) mail).getParsedMessage().getMimeMessage();
        try {
            Date msgDate = mimeMsg.getSentDate();
            if (msgDate == null) {
                // we don't understand the Date value in the message
                throw new SieveException("Invalid date (" + mail.getHeader("Date").get(0) + ")");
            }
            if (BEFORE.equals(comparator)) {
                return msgDate.before(date);
            } else if (AFTER.equals(comparator)) {
                return msgDate.after(date);
            }
        } catch (MessagingException e) {
            throw new SieveException(e.getMessage());
        }
        
        return false;
    }
}
