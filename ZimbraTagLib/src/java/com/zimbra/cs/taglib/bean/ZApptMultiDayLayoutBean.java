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

package com.zimbra.cs.taglib.bean;

import java.util.List;

public class ZApptMultiDayLayoutBean {

    private List<ZApptRowLayoutBean> mAllDayRows;
    private List<ZApptRowLayoutBean> mRows;
    private List<ZApptDayLayoutBean> mDays;
    private int mMaxColumns;

    public ZApptMultiDayLayoutBean(List<ZApptDayLayoutBean> days, List<ZApptRowLayoutBean> allDayRows, List<ZApptRowLayoutBean> rows) {
        mAllDayRows = allDayRows;
        mRows = rows;
        mDays = days;
        mMaxColumns = 0;
        for (ZApptDayLayoutBean day : days) {
            mMaxColumns += day.getColumns().size();
        }
    }

    public List<ZApptRowLayoutBean> getAllDayRows() {
        return mAllDayRows;
    }

    public List<ZApptRowLayoutBean> getRows() {
        return mRows;
    }

    public List<ZApptDayLayoutBean> getDays() {
        return mDays;
    }

    public int getNumDays() {
        return mDays.size();
    }

    public int getMaxColumns() {
        return mMaxColumns;
    }

    public long getScheduleAlldayOverlapCount() {
        int overlap = 0;
        for ( ZApptDayLayoutBean day : mDays) {
            if (!day.getAllDayAppts().isEmpty())
                overlap++;
        }
        return overlap;
    }
}
