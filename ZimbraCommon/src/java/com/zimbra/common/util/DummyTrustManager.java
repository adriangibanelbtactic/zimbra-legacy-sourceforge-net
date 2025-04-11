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

package com.zimbra.common.util;

import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

/**
 * Allows Java code to make SSL connections without certificates.  This
 * class is insecure and should only be used for testing.  See SSLNOTES.txt
 * in the JavaMail distribution for more details.
 *  
 * @author bburtin
 */
public class DummyTrustManager implements X509TrustManager {
    
    public void checkClientTrusted(X509Certificate[] cert, String authType) {
        // everything is trusted
    }
    
    public void checkServerTrusted(X509Certificate[] cert, String authType) {
        // everything is trusted
    }
    
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}