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
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.soap;



public class SoapFaultException extends Exception {

    private boolean mIsReceiversFault;
//    private QName mCode;
//    private QName subcode;
    private Element mDetail;
    private Element mFault;

    /**
     * Create a new SoapFaultException.
     */
    public SoapFaultException(String message,
                               //QName subcode,
                               Element detail,
                               boolean isReceiversFault)

    {
        super(message);
        this.mIsReceiversFault = isReceiversFault;
        //this.subcode = subcode;
        this.mDetail = detail;
        this.mFault = null;
    }

    /**
     * Create a new SoapFaultException. Used by subclasses
     * when converting a mFault into a service-specific mFault
     */
    protected SoapFaultException(SoapFaultException sfe)
    {
        super(sfe.getMessage());
        this.mIsReceiversFault = sfe.mIsReceiversFault;
        this.mDetail = sfe.mDetail;
        this.mFault = sfe.mFault;
    }

    /**
     * Create a new SoapFaultException. Used by SoapProtocol only.
     *
     */
    SoapFaultException(String message,
                        Element detail,
                        boolean isReceiversFault,
                        Element fault)
    {
        super(message);
        this.mIsReceiversFault = isReceiversFault;
        this.mDetail = detail;
        this.mFault = fault;
    }

    /**
     * used by transports and stub mCode
     */
    public SoapFaultException(String message, 
                               Throwable cause)
    {
        super(message, cause);
    }

    /**
     * used by transports and stub mCode
     */
    public SoapFaultException(String message,
                               Element fault)
    {
        super(message);
        this.mFault = fault;
    }

    /*
    public QName getSubcode()
    {
        return subcode;
    }
    */

    public Element getDetail()
    {
        return mDetail;
    }

    /**
     * can only be called if mDetail is null.
     */
    protected void initDetail(Element detail)
        throws IllegalStateException
    {
        if (this.mDetail != null) {
            throw new IllegalStateException("mDetail is not null");
        }

        this.mDetail = detail;
    }

    public boolean isReceiversFault()
    {
        return mIsReceiversFault;
    }

    /**
     * Returns the raw soap mFault, if available.
     */
    public Element getFault()
    {
        return mFault;
    }

    /**
     * dump out detailed debugging information about this mFault
     */
    public String dump()
    {
        StringBuffer sb = new StringBuffer();
        sb.append("class=");
        sb.append(getClass().getName());
        sb.append("\n");
        sb.append("message=");
        sb.append(getMessage());
        sb.append("\n");
//        sb.append("mCode=");
//        sb.append(mCode);
//        sb.append("\n");
//        sb.append("subcode=");
//        sb.append(subcode);
//        sb.append("\n");
        sb.append("mIsReceiversFault=");
        sb.append(mIsReceiversFault);
        sb.append("\n");

        sb.append("mDetail=").append(mDetail);
        sb.append("\n");
        sb.append("mFault=").append(mFault);
        sb.append("\n");
        return sb.toString();
    }
}




