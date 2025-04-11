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

package com.zimbra.cs.index;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.Element;
import com.zimbra.common.soap.MailConstants;
import com.zimbra.cs.index.MailboxIndex.SortBy;
import com.zimbra.cs.mailbox.MailItem;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.calendar.ICalTimeZone;
import com.zimbra.cs.mailbox.calendar.WellKnownTimeZones;
import com.zimbra.cs.service.mail.CalendarUtils;
import com.zimbra.cs.service.mail.Search;
import com.zimbra.cs.service.mail.ToXML.OutputParticipants;
import com.zimbra.cs.service.util.ItemId;
import com.zimbra.soap.ZimbraSoapContext;


/**
 * Simple class that encapsulates all of the parameters involved in a Search request.
 * Not used everywhere, need to convert all code to use this....
 * 
 * To initialize, set:
 *   -- query str
 *   -- offset
 *   -- limit
 *   -- typesStr (sets type value)
 *   -- sortByStr (sets sortBy value)
 *   
 *   IMPORTANT NOTE: if you add new SearchParams, you MUST add parsing/serialization code
 *   to the SearchParams.encodeParams() and SearchParams.parse() APIs. This IS NOT optional
 *   and will break cross-server search if you do not comply.
 *
 */
public final class SearchParams implements Cloneable {
    
    private static final int MAX_OFFSET = 10000000; // 10M
    private static final int MAX_LIMIT = 10000000; // 10M
    
    public enum ExpandResults {
        NONE, /// don't expand any hits
        FIRST, // expand the first hit
        HITS, // for searchConv, expand the members of the conversation that match the search
        ALL; // expand ALL hits

        public static ExpandResults get(String value) throws ServiceException {
            if (value == null)
                return NONE;
            value = value.toUpperCase();
            try {
                return valueOf(value);
            } catch (IllegalArgumentException iae) {
                if (value.equals("1") || value.equals("TRUE"))
                    return FIRST;
                if (value.equals("0") || value.equals("FALSE"))
                    return NONE;
                throw ServiceException.INVALID_REQUEST("unknown 'fetch' value: " + value, null);
            }
        }
    };

    public long getCalItemExpandStart() { return mCalItemExpandStart; }
    public long getCalItemExpandEnd() { return mCalItemExpandEnd; }
    public String getQueryStr() { return mQueryStr; }
    public String getTypesStr() { return mGroupByStr; }
    public byte[] getTypes() { return types; }
    public String getSortByStr() { return mSortByStr; }
    public MailboxIndex.SortBy getSortBy() { return mSortBy; }
    public ExpandResults getFetchFirst() { return mFetchFirst; }
    public boolean getMarkRead() { return mMarkRead; }
    public boolean getWantHtml() { return mWantHtml; }
    public boolean getNeuterImages() { return mNeuterImages; }
    public Set<String> getInlinedHeaders() { return mInlinedHeaders; }
    public OutputParticipants getWantRecipients() { return mRecipients ? OutputParticipants.PUT_RECIPIENTS : OutputParticipants.PUT_SENDERS; }
    public TimeZone getTimeZone() { return mTimeZone; }
    public Locale getLocale() { return mLocale; }
    public boolean getPrefetch() { return mPrefetch; }
    public Mailbox.SearchResultMode getMode() { return mMode; }
    public boolean getEstimateSize() { return mEstimateSize; }
    public String getDefaultField() { return mDefaultField; }

    // offset,limit 
    public int getLimit() { return mLimit; }
    public int getOffset() { return mOffset; }
    
    // cursor parameters:
    public ItemId getPrevMailItemId() { return mPrevMailItemId; }
    public String getPrevSortValueStr() { return mPrevSortValueStr; }
    public long getPrevSortValueLong() { return mPrevSortValueLong; }
    public int getPrevOffset() { return mPrevOffset; }
    public boolean hasEndSortValue() { return mEndSortValueStr != null; }
    public String getEndSortValueStr() { return mEndSortValueStr; }
    public long getEndSortValueLong() { return mEndSortValueLong; }
    

    public void setQueryStr(String queryStr) { mQueryStr = queryStr; }
    public void setOffset(int offset) { mOffset = offset; if (mOffset > MAX_OFFSET) mOffset = MAX_OFFSET; }
    public void setLimit(int limit) { mLimit = limit; if (mLimit > MAX_LIMIT) mLimit = MAX_LIMIT; }
    public void setDefaultField(String field) {
        // yes, it MUST end with the ':'
        if (field.charAt(field.length()-1) != ':')
            field = field + ':';
        mDefaultField = field; 
    }
    
    /**Set the range of dates over which we want to expand out the 
     * instances of any returned CalendarItem objects.
     * @param calItemExpandStart
     */
    public void setCalItemExpandStart(long calItemExpandStart) { mCalItemExpandStart = calItemExpandStart; }
    /**Set the range of dates over which we want to expand out the 
     * instances of any returned CalendarItem objects.
     * @param calItemExpandStart
     */
    public void setCalItemExpandEnd(long calItemExpandEnd) {  mCalItemExpandEnd = calItemExpandEnd; }
    
    /** Since the results are iterator-based, the "limit" is really the same as the chunk size + offset
     * ie, the limit is used to tell the system approximately how many results you want and it tries to get them
     * in a single chunk --- but it isn't until you do the results iteration that the limit is enforced. 
     * @param chunkSize
     */
    public void setChunkSize(int chunkSize) {
        setLimit(chunkSize + mOffset); 
    } 

    public void setTypesStr(String groupByStr) throws ServiceException {
        mGroupByStr = groupByStr;
        types = MailboxIndex.parseTypesString(getTypesStr());
    }
    
    public void setTypes(byte[] _types) { 
        types = _types;
        boolean atFirst = true;
        StringBuilder s = new StringBuilder();
        for (byte b : _types) {
            if (!atFirst)
                s.append(',');
            s.append(MailItem.getNameForType(b));
            atFirst = false;
        }
        mGroupByStr = s.toString();
    }

    public void setSortBy(SortBy sortBy) {
        mSortBy = sortBy;
        mSortByStr = mSortBy.toString(); 
    }
    public void setSortByStr(String sortByStr) { 
        mSortByStr = sortByStr;
        mSortBy = MailboxIndex.SortBy.lookup(sortByStr);
        if (mSortBy == null) {
            mSortBy = SortBy.DATE_DESCENDING;
            mSortByStr = mSortBy.toString();
        }
    }
    public void setFetchFirst(ExpandResults fetch) { mFetchFirst = fetch; }
    public void setMarkRead(boolean read) { mMarkRead = read; }
    public void setWantHtml(boolean html) { mWantHtml = html; }
    public void setNeuterImages(boolean neuter) { mNeuterImages = neuter; }
    public void addInlinedHeader(String name) {
        if (mInlinedHeaders == null) mInlinedHeaders = new HashSet<String>();
        mInlinedHeaders.add(name);
    }
    public void setWantRecipients(boolean recips) { mRecipients = recips; }
    public void setTimeZone(TimeZone tz) { mTimeZone = tz; }
    public void setLocale(Locale loc) { mLocale = loc; }

    public boolean hasCursor() { return mHasCursor; }
    public void setCursor(ItemId prevMailItemId, String prevSort, int prevOffset, String endSort) {
        mHasCursor = true;
        mPrevMailItemId = prevMailItemId;
        mPrevSortValueStr = prevSort;
        try {
            mPrevSortValueLong = Long.parseLong(prevSort);
        } catch (NumberFormatException e) {
            mPrevSortValueLong = 0;
        }
        mPrevOffset = prevOffset;
        mEndSortValueStr = endSort;
        mEndSortValueLong = -1;
        if (mEndSortValueStr != null) {
            try {
                mEndSortValueLong = Long.parseLong(mEndSortValueStr);
            } catch (NumberFormatException e) {
                mEndSortValueLong = Long.MAX_VALUE;
            }
        }
    }
    public void clearCursor() {
        mHasCursor = false;
        mPrevOffset = 0;
        mPrevMailItemId = null;
        mPrevSortValueStr = null;
        mPrevSortValueLong = 0;
        mEndSortValueStr = null;
        mEndSortValueLong = -1;
    }
    public void setPrefetch(boolean truthiness) { mPrefetch = truthiness; }
    public void setMode(Mailbox.SearchResultMode mode) { mMode = mode; }
    
    /**
     * @param estimateSize
     *           if true, the server will attempt to calculate a size estimate for the entire
     *           result set.  Caller must fetch the first hit (via getNext() or getFirstHit()
     *           before the estimate is made.  The estimate will be correct for a DB-only query
     *           and it may be wildly off for a remote or join query. 
     */
    public void setEstimateSize(boolean estimateSize) { mEstimateSize = estimateSize; }
    
    
    /**
     * Encode the necessary parameters into a <SearchRequest> (or similar element) in cases
     * where we have to proxy a search request over to a remote server.
     * 
     * Note that not all parameters are encoded here -- some params (like offset, limit, etc)
     * are changed by the entity doing the search proxying, and so they are set at that level.
     * 
     * @param searchElt
     *            This object's parameters are added as attributes (or sub-elements) of this parameter
     */
    public void encodeParams(Element searchElt) {
        searchElt.addAttribute(MailConstants.A_CAL_EXPAND_INST_START, getCalItemExpandStart());
        searchElt.addAttribute(MailConstants.A_CAL_EXPAND_INST_END, getCalItemExpandEnd());
        searchElt.addAttribute(MailConstants.E_QUERY, getQueryStr(), Element.Disposition.CONTENT);
        searchElt.addAttribute(MailConstants.A_SEARCH_TYPES, getTypesStr());
        searchElt.addAttribute(MailConstants.A_SORTBY, getSortByStr());
        if (getFetchFirst() != null) 
            searchElt.addAttribute(MailConstants.A_FETCH, getFetchFirst().toString());
        searchElt.addAttribute(MailConstants.A_MARK_READ, getMarkRead());
        searchElt.addAttribute(MailConstants.A_WANT_HTML, getWantHtml());
        searchElt.addAttribute(MailConstants.A_NEUTER, getNeuterImages());
        if (getInlinedHeaders() != null) {
            for (String name : getInlinedHeaders())
                searchElt.addElement(MailConstants.A_HEADER).addAttribute(MailConstants.A_ATTRIBUTE_NAME, name);
        }
        searchElt.addAttribute(MailConstants.A_RECIPIENTS, mRecipients);
//        if (getTimeZone() != null) {
//            Element tz = searchElt.addElement(MailConstants.E_CAL_TZ).setText(getTimeZone().getID());
//            tz.addAttribute(MailConstants.A_ID, 
//        }
        if (getLocale() != null) 
            searchElt.addElement(MailConstants.E_LOCALE).setText(getLocale().toString());
        searchElt.addAttribute(MailConstants.A_PREFETCH, getPrefetch());
        searchElt.addAttribute(MailConstants.A_RESULT_MODE, getMode().name());
        searchElt.addAttribute(MailConstants.A_ESTIMATE_SIZE, getEstimateSize());
        searchElt.addAttribute(MailConstants.A_FIELD, getDefaultField());
        
        searchElt.addAttribute(MailConstants.A_QUERY_LIMIT, mLimit);
        searchElt.addAttribute(MailConstants.A_QUERY_OFFSET, mOffset);
        
        // skip limit
        // skip offset
        // skip cursor data
    }
    
    /**
     * Parse the search parameters from a <SearchRequest> or similar element.  
     * 
     * @param requesthttp
     *            The <SearchRequest> itself, or similar element (<SearchConvRequest>, etc)
     * @param requestedAccount 
     *            The account who's mailbox we should search in
     * @param zsc
     *            The SoapContext of the request.  
     * @return
     * @throws ServiceException
     */
    public static SearchParams parse(Element request, ZimbraSoapContext zsc, String defaultQueryStr) throws ServiceException {
        SearchParams params = new SearchParams();
        
        params.setCalItemExpandStart(request.getAttributeLong(MailConstants.A_CAL_EXPAND_INST_START, -1));
        params.setCalItemExpandEnd(request.getAttributeLong(MailConstants.A_CAL_EXPAND_INST_END, -1));
        String query = request.getAttribute(MailConstants.E_QUERY, defaultQueryStr);
        if (query == null)
            throw ServiceException.INVALID_REQUEST("no query submitted and no default query found", null);
        params.setQueryStr(query);
        params.setTypesStr(request.getAttribute(MailConstants.A_SEARCH_TYPES, request.getAttribute(MailConstants.A_GROUPBY, Search.DEFAULT_SEARCH_TYPES)));
        params.setSortByStr(request.getAttribute(MailConstants.A_SORTBY, MailboxIndex.SortBy.DATE_DESCENDING.toString()));
        params.setFetchFirst(ExpandResults.get(request.getAttribute(MailConstants.A_FETCH, null)));
        if (params.getFetchFirst() != ExpandResults.NONE) {
            params.setMarkRead(request.getAttributeBool(MailConstants.A_MARK_READ, false));
            params.setWantHtml(request.getAttributeBool(MailConstants.A_WANT_HTML, false));
            params.setNeuterImages(request.getAttributeBool(MailConstants.A_NEUTER, true));
            for (Element elt : request.listElements(MailConstants.A_HEADER))
                params.addInlinedHeader(elt.getAttribute(MailConstants.A_ATTRIBUTE_NAME));
        }
        params.setWantRecipients(request.getAttributeBool(MailConstants.A_RECIPIENTS, false));

        // <tz>
        Element tzElt = request.getOptionalElement(MailConstants.E_CAL_TZ);
        if (tzElt != null)
            params.setTimeZone(parseTimeZonePart(tzElt));

        // <loc>
        Element locElt = request.getOptionalElement(MailConstants.E_LOCALE);
        if (locElt != null)
            params.setLocale(parseLocale(locElt));
        
        params.setPrefetch(request.getAttributeBool(MailConstants.A_PREFETCH, true));
        params.setMode(Mailbox.SearchResultMode.get(request.getAttribute(MailConstants.A_RESULT_MODE, null)));
        params.setEstimateSize(request.getAttributeBool(MailConstants.A_ESTIMATE_SIZE, false));

        // field
        String field = request.getAttribute(MailConstants.A_FIELD, null);
        if (field != null)
            params.setDefaultField(field);

        params.setLimit(parseLimit(request));
        params.setOffset(parseOffset(request));
        
        Element cursor = request.getOptionalElement(MailConstants.E_CURSOR);
        if (cursor != null) {
            String cursorStr = cursor.getAttribute(MailConstants.A_ID);
            ItemId prevMailItemId = null;
            if (cursorStr != null)
                prevMailItemId = new ItemId(cursorStr, zsc);
            
            int prevOffset = 0;
            String sortVal = cursor.getAttribute(MailConstants.A_SORTVAL);
            
            String endSortVal = cursor.getAttribute(MailConstants.A_ENDSORTVAL, null);
            params.setCursor(prevMailItemId, sortVal, prevOffset, endSortVal);
            
            String addedPart = null;
            
            switch (params.getSortBy()) {
                case DATE_ASCENDING:
                    addedPart = "date:"+quote(">=", sortVal)+(endSortVal!=null ? " date:"+quote("<", endSortVal) : "");
                    break;
                case DATE_DESCENDING:
                    addedPart = "date:"+quote("<=",sortVal)+(endSortVal!=null ? " date:"+quote(">", endSortVal) : "");
                    break;
                case SUBJ_ASCENDING:
                    addedPart = "subject:"+quote(">=", sortVal)+(endSortVal!=null ? " subject:"+quote("<", endSortVal) : "");
                    break;
                case SUBJ_DESCENDING:
                    addedPart = "subject:"+quote("<=", sortVal)+(endSortVal!=null ? " subject:"+quote(">", endSortVal) : "");
                    break;
                case NAME_ASCENDING:
                    addedPart = "from:"+quote(">=", sortVal)+(endSortVal!=null ? " from:"+quote("<", endSortVal) : "");
                    break;
                case NAME_DESCENDING:
                    addedPart = "from:"+quote("<=", sortVal)+(endSortVal!=null ? " from:"+quote(">", endSortVal) : "");
                    break;
            }
            
            if (addedPart != null)
                params.setQueryStr("(" + params.getQueryStr() + ")" + addedPart);
        }
        
        return params;
    }
    
    
    private static java.util.TimeZone parseTimeZonePart(Element tzElt) throws ServiceException {
        String id = tzElt.getAttribute(MailConstants.A_ID);

        // is it a well-known timezone?  if so then we're done here
        ICalTimeZone knownTZ = WellKnownTimeZones.getTimeZoneById(id);
        if (knownTZ != null)
            return knownTZ;

        // custom timezone!
        
        String test = tzElt.getAttribute(MailConstants.A_CAL_TZ_STDOFFSET, null);
        if (test == null)
            throw ServiceException.INVALID_REQUEST("Unknown TZ: \""+id+"\" and no "+MailConstants.A_CAL_TZ_STDOFFSET+" specified", null);
        
        return CalendarUtils.parseTzElement(tzElt);
    }
    
    private static final String LOCALE_PATTERN = "([a-zA-Z]{2})[-_]([a-zA-Z]{2})([-_](.+))?";
    private final static Pattern sLocalePattern = Pattern.compile(LOCALE_PATTERN);
    
    private static Locale parseLocale(Element localeElt) {
        String locStr = localeElt.getText();
        
        if (locStr != null && locStr.length() > 0) {
            Matcher m = sLocalePattern.matcher(locStr);
            if (m.lookingAt()) {
                String lang=null, country=null, variant=null;
                
                if (m.start(1) != -1)
                    lang = locStr.substring(m.start(1), m.end(1));
                
                if (lang == null || lang.length()<=0)
                    return null;
                
                if (m.start(2) != -1)
                    country = locStr.substring(m.start(2), m.end(2));
                
                if (m.start(4) != -1)
                    variant = locStr.substring(m.start(4), m.end(4));
                
                if (variant != null && country != null && variant.length() > 0 && country.length() > 0)
                    return new Locale(lang, country, variant);
                
                if (country != null && country.length() > 0)
                    return new Locale(lang, country);
                
                return new Locale(lang);
            }
        }
        return null;
    }
    
    private static final String quote(String s1, String s2) {
        return "\""+s1+s2+"\"";
    }
    
    private static int parseLimit(Element request) throws ServiceException {
        int limit = (int) request.getAttributeLong(MailConstants.A_QUERY_LIMIT, -1);
        if (limit <= 0)
            limit = 30;
        if (limit > 1000)
            limit = 1000;
        return limit;
    }

    private static int parseOffset(Element request) throws ServiceException {
        // Lookup the offset= and limit= parameters in the soap request
        return (int) request.getAttributeLong(MailConstants.A_QUERY_OFFSET, 0);
    }
    
    public Object clone() {
        SearchParams o = new SearchParams();
        
        o.mDefaultField = mDefaultField;
        o.mQueryStr = mQueryStr;
        o.mOffset = mOffset;
        o.mLimit = mLimit;
        o.mFetchFirst = mFetchFirst;
        o.mMarkRead = mMarkRead;
        o.mWantHtml = mWantHtml;
        o.mNeuterImages = mNeuterImages;
        o.mInlinedHeaders = mInlinedHeaders;
        o.mRecipients = mRecipients;
        o.mCalItemExpandStart = mCalItemExpandStart;
        o.mCalItemExpandEnd = mCalItemExpandEnd;
        o.mTimeZone = mTimeZone;
        o.mLocale = mLocale;
        o.mHasCursor = mHasCursor;
        o.mPrevMailItemId = mPrevMailItemId;
        o.mPrevSortValueStr = mPrevSortValueStr;
        o.mPrevSortValueLong = mPrevSortValueLong;
        o.mPrevOffset = mPrevOffset;
        o.mEndSortValueStr = mEndSortValueStr;
        o.mEndSortValueLong = mEndSortValueLong;
        o.mGroupByStr = mGroupByStr;
        o.mSortByStr = mSortByStr;
        o.mSortBy = mSortBy;
        o.types = types;
        o.mPrefetch = mPrefetch;
        o.mMode = mMode;
        o.mEstimateSize = mEstimateSize;
        
        return o;
    }
    
    private String mDefaultField = "content:";
    private String mQueryStr;
    private int mOffset;
    private int mLimit;
    private ExpandResults mFetchFirst = null;
    private boolean mMarkRead = false;
    private boolean mWantHtml = false;
    private boolean mNeuterImages = false;
    private Set<String> mInlinedHeaders = null;
    private boolean mRecipients = false;
    private long mCalItemExpandStart = -1;
    private long mCalItemExpandEnd = -1;
    
    
    private TimeZone mTimeZone = null; // timezone that the query should be parsed in (for date/time queries)
    private Locale mLocale  = null; 

    private boolean mHasCursor = false;

    /////////////////////
    // "Cursor" Data -- the three pieces of info below are enough for us to find out place in
    // the previous result set, even if entries have been added or removed from the result
    // set:
    private ItemId mPrevMailItemId; // the mail item ID of the last item in the previous result set
    private String mPrevSortValueStr; // the sort value of the last item in the previous result set
    private long mPrevSortValueLong; // the sort value of the last item in the previous result set
    private int mPrevOffset; // the offset of the last item in the previous result set
    private String mEndSortValueStr; // where to end the search. Hits >= this value are NOT included in the result set.
    private long mEndSortValueLong; // where to end the search. Hits >= this value are NOT included in the result set.


    // unparsed -- these need to go away!
    private String mGroupByStr;
    private String mSortByStr;

    // parsed:
    private MailboxIndex.SortBy mSortBy;
    private byte[] types; // types to seach for
    
    private boolean mPrefetch = true;
    private Mailbox.SearchResultMode mMode = Mailbox.SearchResultMode.NORMAL;
    
    private boolean mEstimateSize = false; // ask or a size estimate.  Note that this might have a nontrivial performance impact
}