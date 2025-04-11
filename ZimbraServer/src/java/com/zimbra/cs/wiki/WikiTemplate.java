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
package com.zimbra.cs.wiki;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.zimbra.cs.db.DbMailItem;
import com.zimbra.cs.mailbox.Document;
import com.zimbra.cs.mailbox.Folder;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.WikiItem;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.wiki.Wiki.WikiContext;
import com.zimbra.cs.wiki.Wiki.WikiUrl;

/**
 * WikiTemplate is a parsed Wiki page.  Each parsed tokens represent either
 * a block of text, or a wiklet.  A wiklet can refer to another document
 * stored in someone else's mailbox.  To render a wiki page, it will go through
 * each wiklet, and get the contents denoted by each wiklet and based on
 * the context the wiklet is run (privilege of the requestor, location of
 * the requested page, location of the page referred by the wiklet).
 * 
 * Each parsed templates are cached in the class <code>Wiki</code>.
 * 
 * @author jylee
 *
 */
public class WikiTemplate implements Comparable<WikiTemplate> {

	public static WikiTemplate getDefaultTOC() {
		return new WikiTemplate("{{TOC}}");
	}
	public WikiTemplate(String item, String id, String key, String name) {
		this(item);
		StringBuilder buf = new StringBuilder();
		if (id != null) buf.append(id);
		buf.append(":");
		if (key != null) buf.append(key);
		buf.append(":");
		if (name != null) buf.append(name);
		mId = buf.toString();
	}
	public WikiTemplate(String item) {
		mTemplate = item;
		mTokens = new ArrayList<Token>();
		mModifiedTime = 0;
		mId = "";
		touch();
	}
	
	public static WikiTemplate findTemplate(Context ctxt, String name)
	throws ServiceException {
    	Wiki wiki = Wiki.getInstance(ctxt.wctxt, ctxt.item);
    	return wiki.getTemplate(ctxt.wctxt, name);
	}
	
	public String toString(WikiContext ctxt, MailItem item)
	throws ServiceException, IOException {
		return toString(new Context(ctxt, item));
	}
	
	public String toString(Context ctxt) throws ServiceException, IOException {
		if (!mParsed) {
			parse();
		}

		StringBuffer buf = new StringBuffer();
		for (Token tok : mTokens) {
			ctxt.token = tok;
			buf.append(apply(ctxt));
		}
		touch();
		return buf.toString();
	}
	
	public Token getToken(int i) {
		return mTokens.get(i);
	}

	public long getModifiedTime() {
		return mModifiedTime;
	}
	
	public String getComposedPage(WikiContext ctxt, MailItem item, String chrome)
	throws ServiceException, IOException {
		return getComposedPage(new Context(ctxt, item), chrome);
	}
	
	public String getComposedPage(Context ctxt, String chrome)
	throws ServiceException, IOException {
		Wiki wiki = Wiki.getInstance(ctxt.wctxt, ctxt.item);
		WikiTemplate chromeTemplate = wiki.getTemplate(ctxt.wctxt, chrome);
		String templateVal;

		if (ctxt.item instanceof WikiItem)
			templateVal = chromeTemplate.toString(ctxt);
		else {
			String inner = toString(ctxt);
			ctxt.content = inner;
			templateVal = chromeTemplate.toString(ctxt);
		}

		return templateVal;
	}
	
	public void parse() {
		if (!mParsed)
			Token.parse(mTemplate, mTokens);
		mParsed = true;
	}
	
	public String getId() {
		return mId;
	}
	
	public int compareTo(WikiTemplate t) {
		return mId.compareTo(t.mId);
	}

	public void getInclusions(Context ctxt, List<WikiTemplate> inclusions) {
		parse();
		for (Token tok : mTokens) {
			if (tok.getType() == Token.TokenType.TEXT)
				continue;
			Wiklet w = Wiklet.get(tok);
			WikiTemplate t = null;
			if (w != null) {
				try {
					ctxt.token = tok;
					t = w.findInclusion(ctxt);
				} catch (Exception e) {
				}
				if (t != null && !inclusions.contains(t)) {
					inclusions.add(t);
					t.getInclusions(ctxt, inclusions);
				}
			}
		}
	}
	
	private String apply(Context ctxt) throws ServiceException, IOException {
		if (ctxt.token.getType() == Token.TokenType.TEXT)
			return ctxt.token.getValue();
		Wiklet w = Wiklet.get(ctxt.token);
		if (w != null) {
			String ret = w.apply(ctxt);
			return ret;
		}
		return "";
	}
	
	private void touch() {
		mModifiedTime = System.currentTimeMillis();
	}
	
	private String mId;
	
	private long    mModifiedTime;
	private boolean mParsed;
	
	private List<Token> mTokens;
	private String mTemplate;
	
	public static class Token {
		public static final String sWIKLETTAG = "wiklet";
		public static final String sCLASSATTR = "class";
		
		public enum TokenType { TEXT, WIKLET, WIKILINK }
		public static void parse(String str, List<Token> tokens) throws IllegalArgumentException {
			Token.parse(str, 0, tokens);
		}
		public static void parse(String str, int pos, List<Token> tokens) throws IllegalArgumentException {
			int end = pos;
			if (str.startsWith("{{", pos)) {
				end = str.indexOf("}}", pos);
				if (end == -1)
					throw new IllegalArgumentException("parse error: unmatched {{");
				tokens.add(new Token(str.substring(pos+2, end), TokenType.WIKLET));
				end += 2;
			} else if (str.startsWith("[[", pos)) {
				end = str.indexOf("]]", pos);
				if (end == -1)
					throw new IllegalArgumentException("parse error: unmatched [[");
				tokens.add(new Token(str.substring(pos+2, end), TokenType.WIKILINK));
				end += 2;
			} else if (str.startsWith("<wiklet", pos)) {
				int padding = 2;
				end = str.indexOf(">", pos);
				if (str.charAt(end-1) == '/') {
					end = end - 1;
				} else {
					int endSection = str.indexOf("</wiklet>", end);
					padding = endSection - end + 9;
				}
				if (end == -1)
					throw new IllegalArgumentException("parse error: unmatched <wiklet");
				tokens.add(new Token(str.substring(pos+1, end), TokenType.WIKLET));
				end += padding;
			} else {
				int lastPos = str.length() - 1;
				while (end < lastPos) {
					if (str.startsWith("{{", end) ||
						str.startsWith("[[", end) ||
						str.startsWith("<wiklet", end)) {
						break;
					}
					end++;
				}
				if (end == lastPos)
					end = str.length();
				tokens.add(new Token(str.substring(pos, end), TokenType.TEXT));
			}
			if (end == -1 || end == str.length())
				return;
			
			Token.parse(str, end, tokens);
		}
		
		public Token(String text, TokenType type) {
			mVal = text;
			mType = type;
		}
		
		private TokenType mType;
		private String mVal;
		private String mData;
		private Map<String,String> mParams;
		
		public TokenType getType() {
			return mType;
		}
		
		public String getValue() {
			return mVal;
		}
		
		public String getData() {
			return mData;
		}
		
		public void setData(String str) {
			mData = str;
		}
		
		public Map<String,String> parseParam() {
			return parseParam(mVal);
		}
		private enum ParseState { K, V, VQ }
		public Map<String,String> parseParam(String text) {
			if (mParams != null)
				return mParams;
			Map<String,String> map = new HashMap<String,String>();
			ParseState state = ParseState.K;
			String key = null;
			boolean done = false;
			char c = 0, cprev;
			for (int start = 0, end = 0; !done ; end++) {
				cprev = c;
				if (end == text.length()) {
					c = ' ';
					done = true;
				}
				else
					c = text.charAt(end);
				if (state == ParseState.K) {
					if (c == ' ' || c == '=') {
						key = text.substring(start,end);
						start = end + 1;
						if (c == ' ')
							map.put(key, key);
						else if (c == '=')
							state = ParseState.V;
					}
				} else if (state == ParseState.V) {
					if (c == '"' || c == '\'') {
						start++;
						state = ParseState.VQ;
					} else if (c == ' ') {
						map.put(key, text.substring(start,end));
						start = end + 1;
						state = ParseState.K;
					}
				} else if (state == ParseState.VQ) {
					if ((c == '"' || c == '\'') && cprev != '\\') {
						map.put(key, text.substring(start,end));
						start = end + 1;
						state = ParseState.K;
					}
				}
			}
			mParams = map;
			return map;
		}
		
		public String toString() {
			return "Token: type=" + mType + ", text=" + mVal;
		}
	}
	
	public static class Context {
		public Context(Context copy) {
			this(copy.wctxt, copy.item);
		}
		public Context(WikiContext wc, MailItem it) {
			wctxt = wc; item = it; content = null;
		}
		public WikiContext wctxt;
		public MailItem item;
		public Token token;
		public String content;
	}
	public static abstract class Wiklet {
		public abstract String getName();
		public abstract String getPattern();
		public abstract String apply(Context ctxt) throws ServiceException,IOException;
		public abstract WikiTemplate findInclusion(Context ctxt) throws ServiceException,IOException;
		public String reportError(String errorMsg) {
			String msg = "Error handling wiklet " + getName() + ": " + errorMsg;
			ZimbraLog.wiki.error(msg);
			return msg;
		}
		protected String handleTemplates(Context ctxt,
											List<MailItem> list,
											String bodyTemplate, 
											String itemTemplate)
		throws ServiceException, IOException {
			StringBuffer buf = new StringBuffer();
			for (MailItem item : list) {
				WikiTemplate t = WikiTemplate.findTemplate(ctxt, itemTemplate);
				buf.append(t.toString(ctxt.wctxt, item));
			}
			Context newCtxt = new Context(ctxt);
			newCtxt.content = buf.toString();
			WikiTemplate body = WikiTemplate.findTemplate(newCtxt, bodyTemplate);

			return body.toString(newCtxt);
		}
		public String toString() {
			return "Wiklet: " + getName();
		}
		private static Map<String,Wiklet> sWIKLETS;
		
		static {
			sWIKLETS = new HashMap<String,Wiklet>();
			addWiklet(new TocWiklet());
			addWiklet(new BreadcrumbsWiklet());
			addWiklet(new IconWiklet());
			addWiklet(new NameWiklet());
			addWiklet(new CreatorWiklet());
			addWiklet(new ModifierWiklet());
			addWiklet(new CreateDateWiklet());
			addWiklet(new ModifyDateWiklet());
			addWiklet(new VersionWiklet());
			addWiklet(new ContentWiklet());
			addWiklet(new IncludeWiklet());
			addWiklet(new InlineWiklet());
			addWiklet(new WikilinkWiklet());
			addWiklet(new UrlWiklet());
			addWiklet(new FragmentWiklet());
		}
		
		private static void addWiklet(Wiklet w) {
			sWIKLETS.put(w.getPattern(), w);
		}
		public static Wiklet get(Token tok) {
			Wiklet w;
			String tokenStr = tok.getValue();
			if (tok.getType() == Token.TokenType.WIKILINK) {
				w = sWIKLETS.get("WIKILINK");
			} else {
				String firstTok;
				int index = tokenStr.indexOf(' ');
				if (index != -1) {
					firstTok = tokenStr.substring(0, index);
				} else {
					firstTok = tokenStr;
				}
				if (firstTok.equals(Token.sWIKLETTAG)) {
					Map<String,String> params = tok.parseParam();
					String cls = params.get(Token.sCLASSATTR);
					if (cls == null) {
						// this is really a parse error.
						return null;
					}
					if (cls.equals("link"))
						w = sWIKLETS.get("WIKILINK");
					else
						w = sWIKLETS.get(cls.toUpperCase());
				} else {
					w = sWIKLETS.get(firstTok);
				}
			}
			return w;
		}
		
		public static Wiklet get(String name) {
			return sWIKLETS.get(name);
		}
	}
	public static class TocWiklet extends Wiklet {
		public static final String sFORMAT = "format";
		public static final String sBODYTEMPLATE = "bodyTemplate";
		public static final String sITEMTEMPLATE = "itemTemplate";

		public static final String sDEFAULTBODYTEMPLATE = "_TocBodyTemplate";
		public static final String sDEFAULTITEMTEMPLATE = "_TocItemTemplate";
		
		public static final String sSIMPLE   = "simple";
		public static final String sLIST     = "list";
		public static final String sTEMPLATE = "template";

		public static final String[][] sTAGS =
		{
			{ "zmwiki-tocList", "zmwiki-tocSimple" },
			{ "ul",        "span" },
			{ "li",        "span" }
		};
		
		public static final int sTAGLIST   = 0;
		public static final int sTAGSIMPLE = 1;
		
		public static final int sCLASS = 0;
		public static final int sOUTER = 1;
		public static final int sINNER = 2;
		
		public String getName() {
			return "Table of contents";
		}
		public String getPattern() {
			return "TOC";
		}
		public WikiTemplate findInclusion(Context ctxt) {
			return null;
		}
	    private String createLink(String name) {
	    	StringBuffer buf = new StringBuffer();
	    	buf.append("<a href=\"");
	    	buf.append(name);
	    	buf.append("\">");
	    	buf.append(name);
	    	buf.append("</a>");
	    	return buf.toString();
	    }
		public String generateList(Context ctxt, int style) throws ServiceException {
			Folder folder;
			if (ctxt.item instanceof Folder)
				folder = (Folder) ctxt.item;
			else
				folder = ctxt.item.getMailbox().getFolderById(ctxt.wctxt.octxt, ctxt.item.getFolderId());
	    	StringBuffer buf = new StringBuffer();
	    	buf.append("<");
	    	buf.append(sTAGS[sOUTER][style]);
	    	buf.append(" class='");
	    	buf.append(sTAGS[sCLASS][style]);
	    	buf.append("'>");
	    	List<Folder> subfolders = folder.getSubfolders(ctxt.wctxt.octxt);
        	for (Folder f : subfolders) {
    	    	buf.append("<");
        		buf.append(sTAGS[sINNER][style]);
        		buf.append(" class='zmwiki-pageLink'>");
        		buf.append(createLink(f.getName() + "/"));
        		buf.append("</");
        		buf.append(sTAGS[sINNER][style]);
        		buf.append(">");
        	}
	    	Mailbox mbox = ctxt.item.getMailbox();
            for (Document doc : mbox.getDocumentList(ctxt.wctxt.octxt, folder.getId(), (byte)(DbMailItem.SORT_BY_SUBJECT | DbMailItem.SORT_ASCENDING))) {
            	buf.append("<");
        		buf.append(sTAGS[sINNER][style]);
            	buf.append(" class='zmwiki-pageLink'>");
            	buf.append(createLink(doc.getName()));
        		buf.append("</");
        		buf.append(sTAGS[sINNER][style]);
        		buf.append(">");
            }

    		buf.append("</");
    		buf.append(sTAGS[sOUTER][style]);
    		buf.append(">");
	        return buf.toString();
		}
		public String applyTemplates(Context ctxt, Map<String,String> params) throws ServiceException, IOException {
			List<MailItem> list = new ArrayList<MailItem>();
			Folder folder;
			if (ctxt.item instanceof Folder)
				folder = (Folder) ctxt.item;
			else
				folder = ctxt.item.getMailbox().getFolderById(ctxt.wctxt.octxt, ctxt.item.getFolderId());
	    	list.addAll(folder.getSubfolders(ctxt.wctxt.octxt));
	    	
	    	Mailbox mbox = ctxt.item.getMailbox();
	    	if (ctxt.wctxt.view == null)
	    		list.addAll(mbox.getItemList(ctxt.wctxt.octxt, MailItem.TYPE_WIKI, folder.getId(), (byte)(DbMailItem.SORT_BY_SUBJECT | DbMailItem.SORT_ASCENDING)));
	    	else
	    		list.addAll(mbox.getItemList(ctxt.wctxt.octxt, MailItem.getTypeForName(ctxt.wctxt.view), folder.getId(), (byte)(DbMailItem.SORT_BY_SUBJECT | DbMailItem.SORT_ASCENDING)));

			String bt = params.get(sBODYTEMPLATE);
			String it = params.get(sITEMTEMPLATE);
			if (bt == null)
				bt = sDEFAULTBODYTEMPLATE;
			if (it == null)
				it = sDEFAULTITEMTEMPLATE;
			return handleTemplates(ctxt, list, bt, it);
		}
		
		public String apply(Context ctxt) throws ServiceException, IOException {
			Map<String,String> params = ctxt.token.parseParam();
			String format = params.get(sFORMAT);
			if (format == null) {
				format = sLIST;
			}
			if (format.equals(sTEMPLATE)) {
				return applyTemplates(ctxt, params);
			}
			
			return generateList(ctxt, format.equals(sSIMPLE) ? sTAGSIMPLE : sTAGLIST);
		}
	}
	public static class BreadcrumbsWiklet extends Wiklet {
		public static final String sPAGE = "page";
		public static final String sFORMAT = "format";
		public static final String sBODYTEMPLATE = "bodyTemplate";
		public static final String sITEMTEMPLATE = "itemTemplate";
		public static final String sSEPARATOR = "separator";
		
		public static final String sSIMPLE   = "simple";
		public static final String sTEMPLATE = "template";
		
		public static final String sDEFAULTBODYTEMPLATE = "_PathBodyTemplate";
		public static final String sDEFAULTITEMTEMPLATE = "_PathItemTemplate";
		
		public String getName() {
			return "Breadcrumbs";
		}
		public String getPattern() {
			return "PATH";
		}
		public WikiTemplate findInclusion(Context ctxt) {
			return null;
		}
		private Folder getFolder(Context ctxt, MailItem item) throws ServiceException {
			Mailbox mbox = item.getMailbox();
			return mbox.getFolderById(ctxt.wctxt.octxt, item.getFolderId());
		}
		private List<MailItem> getBreadcrumbs(Context ctxt) {
			List<MailItem> list = new ArrayList<MailItem>();
			try {
				Folder f = getFolder(ctxt, ctxt.item);
				while (f.getId() != Mailbox.ID_FOLDER_USER_ROOT) {
					list.add(0, f);
					f = getFolder(ctxt, f);
				}
			} catch (ServiceException se) {
				// most likely permission problem trying to load the parent folder.
				// ignore and continue.
			}
			return list;
		}
		public String apply(Context ctxt) throws ServiceException, IOException {
			List<MailItem> list = getBreadcrumbs(ctxt);
			Map<String,String> params = ctxt.token.parseParam();
			String format = params.get(sFORMAT);
			if (format == null || format.equals(sSIMPLE)) {
				StringBuffer buf = new StringBuffer();
				buf.append("<span class='zmwiki-breadcrumbsSimple'>");
				StringBuffer path = new StringBuffer();
				path.append("/");
				for (MailItem item : list) {
					String name = item.getName();
					path.append(name);
					buf.append("<span class='zmwiki-pageLink'>");
					buf.append("[[").append(name).append("][").append(path).append("]]");
					buf.append("</span>");
					path.append("/");
				}
				buf.append("</span>");
				return new WikiTemplate(buf.toString()).toString(ctxt);
			} else if (format.equals(sTEMPLATE)) {
				String bt = params.get(sBODYTEMPLATE);
				String it = params.get(sITEMTEMPLATE);
				if (bt == null)
					bt = sDEFAULTBODYTEMPLATE;
				if (it == null)
					it = sDEFAULTITEMTEMPLATE;
				return handleTemplates(ctxt, list, bt, it);
			} else {
				return reportError("format " + format + " not recognized");
			}
		}
	}
	public static class IconWiklet extends Wiklet {
		public String getName() {
			return "Icon";
		}
		public String getPattern() {
			return "ICON";
		}
		public WikiTemplate findInclusion(Context ctxt) {
			return null;
		}
		public String apply(Context ctxt) {
			if (ctxt.item instanceof Document)
				return "<div class='ImgPage'></div>";
			if (ctxt.item.getFolderId() == Mailbox.ID_FOLDER_USER_ROOT)
				return "<div class='ImgNotebook'></div>";
			return "<div class='ImgSection'></div>";
		}
	}
	public static class NameWiklet extends Wiklet {
		public String getName() {
			return "Name";
		}
		public String getPattern() {
			return "NAME";
		}
		public WikiTemplate findInclusion(Context ctxt) {
			return null;
		}
		public String apply(Context ctxt) {
            return ctxt.item.getName();
		}
	}
	public static class FragmentWiklet extends Wiklet {
		public String getName() {
			return "Fragment";
		}
		public String getPattern() {
			return "FRAGMENT";
		}
		public WikiTemplate findInclusion(Context ctxt) {
			return null;
		}
		public String apply(Context ctxt) {
			if (!(ctxt.item instanceof Document)) 
				return "";
			Document doc = (Document) ctxt.item;
			return doc.getFragment();
		}
	}
	public static class CreatorWiklet extends Wiklet {
		public String getName() {
			return "Creator";
		}
		public String getPattern() {
			return "CREATOR";
		}
		public WikiTemplate findInclusion(Context ctxt) {
			return null;
		}
		public String apply(Context ctxt) throws ServiceException {
		   if (ctxt.item instanceof Folder) {
               //notebook folder
			   return ctxt.item.getMailbox().getAccount().getName();        
           }
           else if (ctxt.item instanceof Document) {
        	   Document doc = (Document) ctxt.item;
        	   return doc.getRevision(1).getCreator();        	   
           }

           return "";
		}
	}
	public static class ModifierWiklet extends Wiklet {
		public String getName() {
			return "Modifier";
		}
		public String getPattern() {
			return "MODIFIER";
		}
		public WikiTemplate findInclusion(Context ctxt) {
			return null;
		}
		public String apply(Context ctxt) throws ServiceException {
			if (!(ctxt.item instanceof Document)) 
				return "";
			Document doc = (Document) ctxt.item;
			return doc.getLastRevision().getCreator();
		}
	}
	public static abstract class DateTimeWiklet extends Wiklet {
		private static final String sFORMAT = "format";
		
		private static final String sSHORTDATE  = "shortdate";
		private static final String sMEDIUMDATE = "mediumdate";
		private static final String sLONGDATE   = "longdate";
		private static final String sFULLDATE   = "fulldate";
		
		private static final String sSHORTTIME  = "shorttime";
		private static final String sMEDIUMTIME = "mediumtime";
		private static final String sLONGTIME   = "longtime";
		private static final String sFULLTIME   = "fulltime";
		
		private static final String sSHORTDATETIME  = "shortdateandtime";
		private static final String sMEDIUMDATETIME = "mediumdateandtime";
		private static final String sLONGDATETIME   = "longdateandtime";
		private static final String sFULLDATETIME   = "fulldateandtime";
		protected static Map<String,DateFormat> sFORMATS;
		
		static {
			sFORMATS = new HashMap<String,DateFormat>();
			
			sFORMATS.put(sSHORTDATE,  DateFormat.getDateInstance(DateFormat.SHORT));
			sFORMATS.put(sMEDIUMDATE, DateFormat.getDateInstance(DateFormat.MEDIUM));
			sFORMATS.put(sLONGDATE,   DateFormat.getDateInstance(DateFormat.LONG));
			sFORMATS.put(sFULLDATE,   DateFormat.getDateInstance(DateFormat.FULL));
			
			sFORMATS.put(sSHORTTIME,  DateFormat.getTimeInstance(DateFormat.SHORT));
			sFORMATS.put(sMEDIUMTIME, DateFormat.getTimeInstance(DateFormat.MEDIUM));
			sFORMATS.put(sLONGTIME,   DateFormat.getTimeInstance(DateFormat.LONG));
			sFORMATS.put(sFULLTIME,   DateFormat.getTimeInstance(DateFormat.FULL));
			
			sFORMATS.put(sSHORTDATETIME,  DateFormat.getDateTimeInstance(DateFormat.SHORT,  DateFormat.SHORT));
			sFORMATS.put(sMEDIUMDATETIME, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM));
			sFORMATS.put(sLONGDATETIME,   DateFormat.getDateTimeInstance(DateFormat.LONG,   DateFormat.LONG));
			sFORMATS.put(sFULLDATETIME,   DateFormat.getDateTimeInstance(DateFormat.FULL,   DateFormat.FULL));
		}
		public WikiTemplate findInclusion(Context ctxt) {
			return null;
		}
		protected String formatDate(Context ctxt, Date date) {
			Map<String,String> params = ctxt.token.parseParam();
			String format = params.get(sFORMAT);
			if (format == null || !sFORMATS.containsKey(format))
				format = sSHORTDATETIME;
			DateFormat formatter = sFORMATS.get(format);
			synchronized (formatter) {
				return formatter.format(date);
			}
		}
	}
	public static class CreateDateWiklet extends DateTimeWiklet {
		public String getName() {
			return "Create Date";
		}
		public String getPattern() {
			return "CREATEDATE";
		}
		public String apply(Context ctxt) throws ServiceException {
			Date createDate;
			if (ctxt.item instanceof Document) {
				Document doc = (Document) ctxt.item;
				createDate = new Date(doc.getLastRevision().getRevDate());
			} else
				createDate = new Date(ctxt.item.getDate());
			return formatDate(ctxt, createDate);
		}
	}
	public static class ModifyDateWiklet extends DateTimeWiklet {
		public String getName() {
			return "Modified Date";
		}
		public String getPattern() {
			return "MODIFYDATE";
		}
		public String apply(Context ctxt) throws ServiceException {
			Date modifyDate;
			if (ctxt.item instanceof Document) {
				Document doc = (Document) ctxt.item;
				modifyDate = new Date(doc.getLastRevision().getRevDate());
			} else
				modifyDate = new Date(ctxt.item.getDate());
			return formatDate(ctxt, modifyDate);
		}
	}
	public static class VersionWiklet extends Wiklet {
		public String getName() {
			return "Version";
		}
		public String getPattern() {
			return "VERSION";
		}
		public WikiTemplate findInclusion(Context ctxt) {
			return null;
		}
		public String apply(Context ctxt) {
			if (!(ctxt.item instanceof Document)) 
				return "1";
			Document doc = (Document) ctxt.item;
			return Integer.toString(doc.getVersion());
		}
	}
	public static class ContentWiklet extends Wiklet {
		public String getName() {
			return "Content";
		}
		public String getPattern() {
			return "CONTENT";
		}
		public WikiTemplate findInclusion(Context ctxt) throws ServiceException {
			WikiItem wiki = (WikiItem) ctxt.item;
			return WikiTemplate.findTemplate(ctxt, wiki.getWikiWord());
		}
		public String apply(Context ctxt) throws ServiceException, IOException {
			if (ctxt.content != null)
				return ctxt.content;
			if (!(ctxt.item instanceof WikiItem))
				return "<!-- cotent wiklet on non-wiki item -->";
			WikiItem wiki = (WikiItem) ctxt.item;
			WikiTemplate template = WikiTemplate.findTemplate(ctxt, wiki.getWikiWord());
			return template.toString(ctxt);
		}
	}
	public static class InlineWiklet extends IncludeWiklet {
		public String getPattern() {
			return "INLINE";
		}
	}
	public static class IncludeWiklet extends Wiklet {
		public static final String sPAGE = "page";
		
		public String getName() {
			return "Include";
		}
		public String getPattern() {
			return "INCLUDE";
		}
		public WikiTemplate findInclusion(Context ctxt) throws ServiceException {
			Map<String,String> params = ctxt.token.parseParam();
			String page = params.get(sPAGE);
			if (page == null) {
				page = params.keySet().iterator().next();
			}
			return WikiTemplate.findTemplate(ctxt, page);
		}
		public String apply(Context ctxt) {
			try {
				WikiTemplate template = findInclusion(ctxt);
				return template.toString(ctxt);
			} catch (Exception e) {
				return "<!-- missing template "+ctxt.token+" -->";
			}
		}
	}
	public static class WikilinkWiklet extends Wiklet {
		private static final String PAGENAME = "pagename";
		private static final String TEXT = "text";
		
		public String getName() {
			return "Wikilink";
		}
		public String getPattern() {
			return "WIKILINK";
		}
		public WikiTemplate findInclusion(Context ctxt) {
			return null;
		}
		public String apply(Context ctxt) throws ServiceException, IOException {
			String link, title;
			if (ctxt.token.getType() == Token.TokenType.WIKILINK) {
				String text = ctxt.token.getValue();
				if (text.startsWith("http://")) {
					link = text;
					title = text;
				} else if (text.startsWith("<wiklet")) {
					WikiTemplate template = new WikiTemplate(text);
					link = template.toString(ctxt);
					title = link;
				} else {
					link = text;
					title = text;
					int pos = text.indexOf('|');
					if (pos != -1) {
						link = text.substring(0, pos);
						title = text.substring(pos+1);
					} else {
						pos = text.indexOf("][");
						if (pos != -1) {
							title = text.substring(0, pos);
							link = text.substring(pos+2);
						}
					}
				}
			} else {
				Map<String,String> params = ctxt.token.parseParam();
				link = params.get(PAGENAME);
				title = params.get(TEXT);
				if (title == null)
					title = link;
			}
			WikiUrl wurl = (ctxt.item instanceof Folder) ?
					new WikiUrl(link, ctxt.item.getId()) :
					new WikiUrl(link, ctxt.item.getFolderId());
			try {
				StringBuffer buf = new StringBuffer();
				buf.append("<a href='");
				buf.append(wurl.getFullUrl(ctxt.wctxt, ctxt.item.getMailbox().getAccountId()));
				buf.append("'>").append(title).append("</a>");
				return buf.toString();
			} catch (Exception e) {
				return "<!-- invalid wiki url "+link+" -->" + title;
			}
		}
	}
	public static class UrlWiklet extends Wiklet {
		public String getName() {
			return "Url";
		}
		public String getPattern() {
			return "URL";
		}
		public WikiTemplate findInclusion(Context ctxt) {
			return null;
		}
		public String apply(Context ctxt) {
			if (ctxt.item == null)
				return "<!-- cannot resolve item for url wiklet -->";
			String title = ctxt.item.getName();
			WikiUrl wurl = new WikiUrl(ctxt.item);
			try {
				StringBuffer buf = new StringBuffer();
				buf.append("<a href='");
				buf.append(wurl.getFullUrl(ctxt.wctxt, ctxt.item.getMailbox().getAccountId()));
				buf.append("'>").append(title).append("</a>");
				return buf.toString();
			} catch (Exception e) {
				return "<!-- cannot generate URL for item "+title+" -->" + title;
			}
		}
	}
}
