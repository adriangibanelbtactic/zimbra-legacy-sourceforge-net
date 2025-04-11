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

package com.zimbra.cs.service.account;

import com.zimbra.common.soap.AccountConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.Account;
import com.zimbra.cs.account.CalendarResource;
import com.zimbra.cs.account.DataSource;
import com.zimbra.cs.account.EntrySearchFilter;
import com.zimbra.cs.account.EntrySearchFilter.Multi;
import com.zimbra.cs.account.EntrySearchFilter.Single;
import com.zimbra.cs.account.EntrySearchFilter.Visitor;
import com.zimbra.cs.account.Identity;
import com.zimbra.cs.account.Provisioning;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;

public class ToXML {

    public static Element encodeAccount(Element parent, Account account, boolean applyCos) {
        Element acctElem = parent.addElement(AccountConstants.E_ACCOUNT);
        acctElem.addAttribute(AccountConstants.A_NAME, account.getName());
        acctElem.addAttribute(AccountConstants.A_ID, account.getId());
        Map attrs = account.getAttrs(applyCos);
        addAccountAttrs(acctElem, attrs, AccountConstants.A_N);
        return acctElem;
    }

    public static Element encodeAccountOld(Element parent, Account account, boolean applyCos) {
        Element acctElem = parent.addElement(AccountConstants.E_ACCOUNT);
        acctElem.addAttribute(AccountConstants.A_NAME, account.getName());
        acctElem.addAttribute(AccountConstants.A_ID, account.getId());
        Map attrs = account.getAttrs(applyCos);
        addAccountAttrsOld(acctElem, attrs, AccountConstants.A_N);
        return acctElem;
    }

    public static Element encodeAccountOld(Element parent, Account account) {
        return encodeAccountOld(parent, account, true);
    }

    public static Element encodeCalendarResource(Element parent, CalendarResource resource, boolean applyCos) {
        Element resElem = parent.addElement(AccountConstants.E_CALENDAR_RESOURCE);
        resElem.addAttribute(AccountConstants.A_NAME, resource.getName());
        resElem.addAttribute(AccountConstants.A_ID, resource.getId());
        Map attrs = resource.getAttrs(applyCos);
        addAccountAttrs(resElem, attrs, AccountConstants.A_N);
        return resElem;
    }

    public static Element encodeCalendarResourceOld(Element parent, CalendarResource resource, boolean applyCos) {
        Element resElem = parent.addElement(AccountConstants.E_CALENDAR_RESOURCE);
        resElem.addAttribute(AccountConstants.A_NAME, resource.getName());
        resElem.addAttribute(AccountConstants.A_ID, resource.getId());
        Map attrs = resource.getAttrs(applyCos);
        addAccountAttrsOld(resElem, attrs, AccountConstants.A_N);
        return resElem;
    }

    public static Element encodeCalendarResource(Element parent, CalendarResource resource) {
        return encodeCalendarResource(parent, resource, false);
    }

    private static void addAccountAttrs(Element e, Map attrs, String key) {
        for (Iterator iter = attrs.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry entry = (Entry) iter.next();
            String name = (String) entry.getKey();
            Object value = entry.getValue();

            // Never return data source passwords
            if (name.equalsIgnoreCase(Provisioning.A_zimbraDataSourcePassword))
                continue;

            // Never return password.
            if (name.equalsIgnoreCase(Provisioning.A_userPassword))
                value = "VALUE-BLOCKED";

            if (value instanceof String[]) {
                String sv[] = (String[]) value;
                for (int i = 0; i < sv.length; i++)
                    e.addKeyValuePair(name, sv[i], AccountConstants.E_A, key);
            } else if (value instanceof String) {
                e.addKeyValuePair(name, (String) value, AccountConstants.E_A, key);
            }
        }       
    }

    private static void addAccountAttrsOld(Element e, Map attrs, String key) {
        for (Iterator iter = attrs.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry entry = (Entry) iter.next();
            String name = (String) entry.getKey();
            Object value = entry.getValue();

            // Never return data source passwords
            if (name.equalsIgnoreCase(Provisioning.A_zimbraDataSourcePassword))
                continue;

            // Never return password.
            if (name.equalsIgnoreCase(Provisioning.A_userPassword))
                value = "VALUE-BLOCKED";

            if (value instanceof String[]) {
                String sv[] = (String[]) value;
                for (int i = 0; i < sv.length; i++) {
                    Element pref = e.addElement(AccountConstants.E_A);
                    pref.addAttribute(key, name);
                    pref.setText(sv[i]);
                }
            } else if (value instanceof String) {
                Element pref = e.addElement(AccountConstants.E_A);
                pref.addAttribute(key, name);
                pref.setText((String) value);
            }
        }       
    }

    private static class EntrySearchFilterXmlVisitor implements Visitor {
        Stack<Element> mParentStack;
        Element mRootElement;

        public EntrySearchFilterXmlVisitor(Element parent) {
            mParentStack = new Stack<Element>();
            mParentStack.push(parent);
        }

        public Element getRootElement() { return mRootElement; }

        public void visitSingle(Single term) {
            Element parent = mParentStack.peek();
            Element elem = parent.addElement(AccountConstants.E_ENTRY_SEARCH_FILTER_SINGLECOND);
            if (mRootElement == null) mRootElement = elem;
            if (term.isNegation())
                elem.addAttribute(AccountConstants.A_ENTRY_SEARCH_FILTER_NEGATION, true);
            elem.addAttribute(AccountConstants.A_ENTRY_SEARCH_FILTER_ATTR, term.getLhs());
            elem.addAttribute(AccountConstants.A_ENTRY_SEARCH_FILTER_OP, term.getOperator().toString());
            elem.addAttribute(AccountConstants.A_ENTRY_SEARCH_FILTER_VALUE, term.getRhs());
        }

        public void enterMulti(Multi term) {
            Element parent = mParentStack.peek();
            Element elem = parent.addElement(AccountConstants.E_ENTRY_SEARCH_FILTER_MULTICOND);
            if (mRootElement == null) mRootElement = elem;
            if (term.isNegation())
                elem.addAttribute(AccountConstants.A_ENTRY_SEARCH_FILTER_NEGATION, true);
            if (!term.isAnd())
                elem.addAttribute(AccountConstants.A_ENTRY_SEARCH_FILTER_OR, true);
            mParentStack.push(elem);
        }

        public void leaveMulti(Multi term) {
            mParentStack.pop();
        }
    }

    public static Element encodeEntrySearchFilter(Element parent, EntrySearchFilter filter) {
        EntrySearchFilterXmlVisitor visitor = new EntrySearchFilterXmlVisitor(parent);
        filter.traverse(visitor);
        return visitor.getRootElement();
    }

    public static Element encodeLocale(Element parent, Locale locale) {
        Element e = parent.addElement(AccountConstants.E_LOCALE);
        // Always use US English for locale's display name.
        e.addAttribute(AccountConstants.A_NAME, locale.getDisplayName(Locale.US));
        e.addAttribute(AccountConstants.A_ID, locale.toString());
        return e;
    }

    public static Element encodeIdentity(Element parent, Identity identity) {
        Element e = parent.addElement(AccountConstants.E_IDENTITY);
        e.addAttribute(AccountConstants.A_NAME, identity.getName());
        e.addAttribute(AccountConstants.A_ID, identity.getId());
        addAccountAttrs(e, identity.getAttrs(), AccountConstants.A_NAME);
        return e;
    }

    public static Element encodeDataSource(Element parent, DataSource ds) {
        Element e = parent.addElement(AccountConstants.E_DATA_SOURCE);
        e.addAttribute(AccountConstants.A_NAME, ds.getName());
        e.addAttribute(AccountConstants.A_ID, ds.getId());
        e.addAttribute(AccountConstants.A_TYPE, ds.getType().name());
        addAccountAttrs(e, ds.getAttrs(), AccountConstants.A_N);
        return e;
    }
}
