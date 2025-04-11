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
 * Created on May 30, 2004
 */
package com.zimbra.cs.account;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.collections.map.LRUMap;
import com.zimbra.common.util.Log;
import com.zimbra.common.util.LogFactory;

import com.zimbra.cs.account.Provisioning.AccountBy;
import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.BlobMetaData;
import com.zimbra.common.util.BlobMetaDataEncodingException;

/**
 * @author schemers
 */
public class AuthToken {

    private static final long DEFAULT_AUTH_LIFETIME = 60*60*12;
    
    // TODO: config
    private static final int AUTHTOKEN_CACHE_SIZE = 5000;
    
    private static final String C_ID  = "id";
    // original admin id
    private static final String C_AID  = "aid";    
	private static final String C_EXP = "exp";
    private static final String C_ADMIN = "admin";
    private static final String C_DOMAIN = "domain";    
	private static LRUMap mCache = new LRUMap(AUTHTOKEN_CACHE_SIZE);
    
    private static Log mLog = LogFactory.getLog(AuthToken.class); 
    
    private String mAccountId;
    private String mAdminAccountId;    
	private long mExpires;
	private String mEncoded;
    private boolean mIsAdmin;
    private boolean mIsDomainAdmin;    
//	private static AuthTokenKey mTempKey;
    
    public String toString() {
        return "AuthToken(acct="+mAccountId+" admin="+mAdminAccountId+" exp="
        +mExpires+" isAdm="+mIsAdmin+" isDomAd="+mIsDomainAdmin+")";
    }
    
    private static AuthTokenKey getCurrentKey() throws AuthTokenException {
        try {
            AuthTokenKey key = AuthTokenKey.getCurrentKey();
            return key;
        } catch (ServiceException e) {
            mLog.fatal("unable to get latest AuthTokenKey", e);
            throw new AuthTokenException("unable to get AuthTokenKey", e);
        }
    }

    /**
     * Return an AuthToken object using an encoded authtoken. Caller should call isExpired on returned
     * authToken before using it.
     * @param encoded
     * @return
     * @throws AuthTokenException
     */
    public synchronized static AuthToken getAuthToken(String encoded) throws AuthTokenException {
        AuthToken at = (AuthToken) mCache.get(encoded);
        if (at == null) {
            at = new AuthToken(encoded);
            if (!at.isExpired())
                mCache.put(encoded, at);
        } else {
            // remove it if expired
            if (at.isExpired())
                mCache.remove(encoded);
        }
        return at;
    }
    
    public static AuthToken getZimbraAdminAuthToken() throws ServiceException {
        Account acct = Provisioning.getInstance().get(AccountBy.adminName, LC.zimbra_ldap_user.value());
        return new AuthToken(acct, true);
    }
    
	private AuthToken(String encoded) throws AuthTokenException {
        try {
            mEncoded = encoded;
            int pos = encoded.indexOf('_');
            if (pos == -1)
                throw new AuthTokenException("invalid authtoken format");

            String ver = encoded.substring(0, pos);
            
            int pos2 = encoded.indexOf('_', pos+1);
            if (pos2 == -1)
                throw new AuthTokenException("invalid authtoken format");
            String hmac = encoded.substring(pos+1, pos2);
            String data = encoded.substring(pos2+1);

            AuthTokenKey key = AuthTokenKey.getVersion(ver);
            if (key == null)
                throw new AuthTokenException("unknown key version");
            
            String computedHmac = getHmac(data, key.getKey());
            if (!computedHmac.equals(hmac))
                throw new AuthTokenException("hmac failure");      

            data = new String(Hex.decodeHex(data.toCharArray()));

            Map map = BlobMetaData.decode(data);
            mAccountId = (String)map.get(C_ID);
            mAdminAccountId = (String)map.get(C_AID);            
            mExpires = Long.parseLong((String)map.get(C_EXP));
            String ia = (String) map.get(C_ADMIN);
            mIsAdmin = "1".equals(ia);
            String da = (String) map.get(C_DOMAIN);            
            mIsDomainAdmin = "1".equals(da);
        } catch (ServiceException e) {
            throw new AuthTokenException("service exception", e);
        } catch (DecoderException e) {
            throw new AuthTokenException("decoding exception", e);
		} catch (BlobMetaDataEncodingException e) {
			throw new AuthTokenException("blob decoding exception", e);
		}
	}

    public AuthToken(Account acct) {
        this(acct, false);
    }

    public AuthToken(Account acct, boolean isAdmin) {
        this(acct, 0, isAdmin, null);
        long lifetime = mIsAdmin || mIsDomainAdmin ?
                    acct.getTimeInterval(Provisioning.A_zimbraAdminAuthTokenLifetime, DEFAULT_AUTH_LIFETIME * 1000) :                                    
                    acct.getTimeInterval(Provisioning.A_zimbraAuthTokenLifetime, DEFAULT_AUTH_LIFETIME * 1000);
        mExpires = System.currentTimeMillis() + lifetime;
    }

    public AuthToken(Account acct, long expires) {
        this(acct, expires, false, null);
    }

    /**
     * @param acct account authtoken will be valid for
     * @param expires when the token expires
     * @param isAdmin true if acct is an admin account
     * @param adminAcct the admin account accessing acct's information, if this token was created by an admin. mainly used
     *        for auditing.
     */
	public AuthToken(Account acct, long expires, boolean isAdmin, Account adminAcct) {
        mAccountId = acct.getId();
        mAdminAccountId = adminAcct != null ? adminAcct.getId() : null;
		mExpires = expires;
        mIsAdmin = isAdmin && "TRUE".equals(acct.getAttr(Provisioning.A_zimbraIsAdminAccount));
        mIsDomainAdmin = isAdmin && "TRUE".equals(acct.getAttr(Provisioning.A_zimbraIsDomainAdminAccount));
		mEncoded = null;
	}

	public String getAccountId() {
		return mAccountId;
	}

    public String getAdminAccountId() {
        return mAdminAccountId;
    }

	public long getExpires() {
		return mExpires;
	}

	public boolean isExpired() {
		return System.currentTimeMillis() > mExpires;
	}

    public boolean isAdmin() {
        return mIsAdmin;
    }

    public boolean isDomainAdmin() {
        return mIsDomainAdmin;
    }
    
	public String getEncoded() throws AuthTokenException {
		if (mEncoded == null) {
			StringBuffer encodedBuff = new StringBuffer(64);
            BlobMetaData.encodeMetaData(C_ID, mAccountId, encodedBuff);
            BlobMetaData.encodeMetaData(C_EXP, Long.toString(mExpires), encodedBuff);
            if (mAdminAccountId != null)
                BlobMetaData.encodeMetaData(C_AID, mAdminAccountId, encodedBuff);                
            if (mIsAdmin)
                BlobMetaData.encodeMetaData(C_ADMIN, "1", encodedBuff);
            if (mIsDomainAdmin)
                BlobMetaData.encodeMetaData(C_DOMAIN, "1", encodedBuff);            
			String data = new String(Hex.encodeHex(encodedBuff.toString().getBytes()));
            AuthTokenKey key = getCurrentKey();
			String hmac = getHmac(data, key.getKey());
			mEncoded = key.getVersion()+"_"+hmac+"_"+data;
		}
		return mEncoded;
	}

	private String getHmac(String data, byte[] key) {
		try {
			ByteKey bk = new ByteKey(key);
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(bk);
			return new String(Hex.encodeHex(mac.doFinal(data.getBytes())));
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("fatal error", e);
		} catch (InvalidKeyException e) {
			throw new RuntimeException("fatal error", e);
		}
	}
	
	static class ByteKey implements SecretKey {
        private static final long serialVersionUID = -7237091299729195624L;
        private byte[] mKey;
		
		ByteKey(byte[] key) {
			mKey = key.clone();
		}
		
		public byte[] getEncoded() {
			return mKey;
		}

		public String getAlgorithm() {
			return "HmacSHA1";
		}

		public String getFormat() {
			return "RAW";
		}		

	}

    public static void main(String args[]) throws ServiceException, AuthTokenException {
        Account a = Provisioning.getInstance().get(AccountBy.name, "user1@example.zimbra.com");
        AuthToken at = new AuthToken(a);
        long start = System.currentTimeMillis();
        String encoded = at.getEncoded();
        for (int i = 0; i < 1000; i++) {
            new AuthToken(encoded);
        }
        long finish = System.currentTimeMillis();
        System.out.println(finish-start);
        
        start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            getAuthToken(encoded);
        }
        finish = System.currentTimeMillis();
        System.out.println(finish-start);
    }
}
