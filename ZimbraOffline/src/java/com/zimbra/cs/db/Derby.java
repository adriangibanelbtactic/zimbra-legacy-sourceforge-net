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
package com.zimbra.cs.db;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;

import com.zimbra.common.localconfig.LC;
import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.db.DbPool.Connection;
import com.zimbra.cs.offline.OfflineLC;

public class Derby extends Db {

    private Map<Db.Error, String> mErrorCodes;
    private Map<String, String> mIndexNames;

    Derby() {
        mErrorCodes = new HashMap<Db.Error, String>(6);
        mErrorCodes.put(Db.Error.DEADLOCK_DETECTED,        "40000");
        mErrorCodes.put(Db.Error.DUPLICATE_ROW,            "23505");
        mErrorCodes.put(Db.Error.FOREIGN_KEY_NO_PARENT,    "23503");
        mErrorCodes.put(Db.Error.FOREIGN_KEY_CHILD_EXISTS, "23503");
        mErrorCodes.put(Db.Error.NO_SUCH_DATABASE,         "42Y07");
        mErrorCodes.put(Db.Error.NO_SUCH_TABLE,            "42X05");

        // indexes have different names under Derby
        mIndexNames = new HashMap<String, String>();
        mIndexNames.put("i_type",           "i_mail_item_type");
        mIndexNames.put("i_parent_id",      "fk_mail_item_parent_id");
        mIndexNames.put("i_folder_id_date", "i_mail_item_folder_id_date");
        mIndexNames.put("i_index_id",       "i_mail_item_index_id");
        mIndexNames.put("i_unread",         "i_mail_item_unread");
        mIndexNames.put("i_date",           "i_mail_item_date");
        mIndexNames.put("i_mod_metadata",   "i_mail_item_mod_metadata");
        mIndexNames.put("i_tags_date",      "i_mail_item_tags_date");
        mIndexNames.put("i_flags_date",     "i_mail_item_flags_date");
        mIndexNames.put("i_volume_id",      "i_mail_item_volume_id");
        mIndexNames.put("i_change_mask",    "i_mail_item_change_mask");
        mIndexNames.put("i_name_folder_id", "i_mail_item_name_folder_id");
    }

    @Override
    boolean supportsCapability(Db.Capability capability) {
        switch (capability) {
            case BITWISE_OPERATIONS:         return false;
            case BOOLEAN_DATATYPE:           return false;
            case BROKEN_IN_CLAUSE:           return true;
            case CASE_SENSITIVE_COMPARISON:  return true;
            case CAST_AS_BIGINT:             return true;
            case CLOB_COMPARISON:            return false;
            case DISABLE_CONSTRAINT_CHECK:   return false;
            case LIMIT_CLAUSE:               return false;
            case MULTITABLE_UPDATE:          return false;
            case ON_DUPLICATE_KEY:           return false;
            case ON_UPDATE_CASCADE:          return false;
            case UNIQUE_NAME_INDEX:          return false;
        }
        return false;
    }

    @Override
    boolean compareError(SQLException e, Db.Error error) {
        String code = mErrorCodes.get(error);
        return (code != null && e.getSQLState().equals(code));
    }

    @Override
    String forceIndexClause(String index) {
        String localIndex = mIndexNames.get(index);
        if (localIndex == null) {
            ZimbraLog.misc.warn("could not find derby equivalent from index " + index);
            return "";
        }
        return " -- DERBY-PROPERTIES " + (localIndex.startsWith("fk_") ? "constraint=" : "index=") + localIndex + '\n';
    }

    @Override
    DbPool.PoolConfig getPoolConfig() {
        return new DerbyConfig();
    }
    
    @Override
    public boolean databaseExists(Connection conn, String databaseName)
    throws ServiceException {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        int numSchemas = 0;

        try {
            stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM SYS.SYSSCHEMAS " +
                "WHERE schemaname = ?");
            stmt.setString(1, databaseName.toUpperCase());
            rs = stmt.executeQuery();
            rs.next();
            numSchemas = rs.getInt(1);
        } catch (SQLException e) {
            throw ServiceException.FAILURE("Unable to determine whether database exists", e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
        }

        return (numSchemas > 0);
    }


    public static OutputStream disableDerbyLogFile(){
        return new OutputStream() {
            public void write(int b) {
                // Ignore all log messages
            }
        };
    }
    
    static final class DerbyConfig extends DbPool.PoolConfig {
        DerbyConfig() {
        	if (!OfflineLC.zdesktop_derby_log.booleanValue()) {
        		System.setProperty("derby.stream.error.method", "com.zimbra.cs.db.Derby.disableDerbyLogFile");
        	}
//            System.setProperty("derby.stream.error.file", "/opt/zimbra/log/derby.log");
//            System.setProperty("derby.language.logQueryPlan", "true");

            mDriverClassName = "org.apache.derby.jdbc.EmbeddedDriver";
            mPoolSize = 12;
            mRootUrl = null;
            mConnectionUrl = "jdbc:derby:" + System.getProperty("derby.system.home", LC.zimbra_home.value() + File.separator + "derby"); 
            mLoggerUrl = null;
            mSupportsStatsCallback = false;
            mDatabaseProperties = getDerbyProperties();

            ZimbraLog.misc.debug("Setting connection pool size to " + mPoolSize);
        }

        private static Properties getDerbyProperties() {
            Properties props = new Properties();

            props.put("cacheResultSetMetadata", "true");
            props.put("cachePrepStmts", "true");
            props.put("prepStmtCacheSize", "25");        
            props.put("autoReconnect", "true");
            props.put("useUnicode", "true");
            props.put("characterEncoding", "UTF-8");
            props.put("dumpQueriesOnException", "true");
            props.put("user", LC.zimbra_mysql_user.value());
            props.put("password", LC.zimbra_mysql_password.value());

            return props;
        }
    }

    public static void main(String args[]) {
        // command line argument parsing
        Options options = new Options();
        CommandLine cl = Versions.parseCmdlineArgs(args, options);

        String outputDir = cl.getOptionValue("o");
        File outFile = new File(outputDir, "versions-init.sql");
        
        outFile.delete();
        
        Writer output = null;
        
        try {
            output = new BufferedWriter( new FileWriter(outFile) );

            String redoVer = com.zimbra.cs.redolog.Version.latest().toString();
            String outStr = "-- AUTO-GENERATED .SQL FILE - Generated by the Derby versions tool\n" +
                "INSERT INTO zimbra.config(name, value, description) VALUES\n" +
                "\t('db.version', '" + Versions.DB_VERSION + "', 'db schema version'),\n" + 
                "\t('index.version', '" + Versions.INDEX_VERSION + "', 'index version'),\n" +
                "\t('redolog.version', '" + redoVer + "', 'redolog version');\n";

            output.write(outStr);
            
            if (output != null)
                output.close();
        } catch (IOException e){
            System.out.println("ERROR - caught exception at\n");
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
