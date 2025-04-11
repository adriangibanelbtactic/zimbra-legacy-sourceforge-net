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
 * Portions created by Zimbra are Copyright (C) 2006, 2007 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */
package org.jivesoftware.wildfire.auth;

import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;

import java.sql.*;

/**
 * Default AuthProvider implementation. It authenticates against the <tt>jiveUser</tt>
 * database table and supports plain text and digest authentication.
 *
 * Because each call to authenticate() makes a database connection, the
 * results of authentication should be cached whenever possible.
 *
 * @author Matt Tucker
 */
public class DefaultAuthProvider implements AuthProvider {

    private static final String LOAD_PASSWORD =
            "SELECT password,encryptedPassword FROM jiveUser WHERE username=?";
    private static final String UPDATE_PASSWORD =
            "UPDATE jiveUser SET password=?, encryptedPassword=? WHERE username=?";

    /**
     * Constructs a new DefaultAuthProvider.
     */
    public DefaultAuthProvider() {

    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username == null || password == null) {
            throw new UnauthorizedException();
        }
        username = username.trim().toLowerCase();
        try {
            if (!password.equals(getPassword(username))) {
                throw new UnauthorizedException();
            }
        }
        catch (UserNotFoundException unfe) {
            throw new UnauthorizedException();
        }
        // Got this far, so the user must be authorized.
    }

    public void authenticate(String username, String token, String digest) throws UnauthorizedException {
        if (username == null || token == null || digest == null) {
            throw new UnauthorizedException();
        }
        username = username.trim().toLowerCase();
        try {
            String password = getPassword(username);
            String anticipatedDigest = AuthFactory.createDigest(token, password);
            if (!digest.equalsIgnoreCase(anticipatedDigest)) {
                throw new UnauthorizedException();
            }
        }
        catch (UserNotFoundException unfe) {
            throw new UnauthorizedException();
        }
        // Got this far, so the user must be authorized.
    }

    public boolean isPlainSupported() {
        return true;
    }

    public boolean isDigestSupported() {
        return true;
    }

    public String getPassword(String username) throws UserNotFoundException {
        if (!supportsPasswordRetrieval()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PASSWORD);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException(username);
            }
            String plainText = rs.getString(1);
            String encrypted = rs.getString(2);
            if (encrypted != null) {
                try {
                    return AuthFactory.decryptPassword(encrypted);
                }
                catch (UnsupportedOperationException uoe) {
                    // Ignore and return plain password instead.
                }
            }
            return plainText;
        }
        catch (SQLException sqle) {
            throw new UserNotFoundException(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void setPassword(String username, String password) throws UserNotFoundException {
        // Determine if the password should be stored as plain text or encrypted.
        boolean usePlainPassword = JiveGlobals.getBooleanProperty("user.usePlainPassword");
        String encryptedPassword = null;
        if (!usePlainPassword) {
            try {
                encryptedPassword = AuthFactory.encryptPassword(password);
                // Set password to null so that it's inserted that way.
                password = null;
            }
            catch (UnsupportedOperationException uoe) {
                // Encryption may fail. In that case, ignore the error and
                // the plain password will be stored.
            }
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PASSWORD);
            if (password == null) {
                pstmt.setNull(1, Types.VARCHAR);
            }
            else {
                pstmt.setString(1, password);
            }
            if (encryptedPassword == null) {
                pstmt.setNull(2, Types.VARCHAR);
            }
            else {
                pstmt.setString(2, encryptedPassword);
            }
            pstmt.setString(3, username);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            throw new UserNotFoundException(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public boolean supportsPasswordRetrieval() {
        return true;
    }
}