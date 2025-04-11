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
 * Created on 2004. 11. 3.
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package com.zimbra.cs.redolog.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.zimbra.cs.redolog.RolloverManager;
import com.zimbra.cs.redolog.logger.FileHeader;
import com.zimbra.cs.redolog.logger.FileLogReader;
import com.zimbra.cs.redolog.op.CreateMessage;
import com.zimbra.cs.redolog.op.RedoableOp;
import com.zimbra.common.util.ByteUtil;
import com.zimbra.cs.util.Zimbra;

/**
 * @author jhahm
 */
public class RedoLogVerify {

    private static Options mOptions = new Options();
    
    static {
        mOptions.addOption("q", "quiet",   false, "quiet mode");
        mOptions.addOption("m", "message",   false, "show message body data");
    }

    private static void usage(String errmsg) {
        if (errmsg != null) { 
            System.err.println(errmsg);
        }
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("RedoLogVerify [options] [log files/directories]",
            "where [options] are:", mOptions,
            "and [log files] are redo log files.");
        System.exit((errmsg == null) ? 0 : 1);
    }

    private static CommandLine parseArgs(String args[]) {
        StringBuffer gotCL = new StringBuffer("cmdline: ");
        for (int i = 0; i < args.length; i++) {
            gotCL.append("'").append(args[i]).append("' ");
        }
        
        CommandLineParser parser = new GnuParser();
        CommandLine cl = null;
        try {
            cl = parser.parse(mOptions, args);
        } catch (ParseException pe) {
            usage(pe.getMessage());
        }
        return cl;
    }


    private static class BadFile {
        public File file;
        public Throwable error;
        public BadFile(File f, Throwable e) { file = f; error = e; }
    }

    private boolean mQuiet;
    private boolean mDumpMessageBody;
    private List<BadFile> mBadFiles;

    private RedoLogVerify(boolean quiet, boolean dumpMsgBody) {
        mQuiet = quiet;
        mDumpMessageBody = dumpMsgBody;
        mBadFiles = new ArrayList<BadFile>();
    }

	public boolean scanLog(File logfile) throws IOException {
		boolean good = false;
		FileLogReader logReader = new FileLogReader(logfile, false);
		logReader.open();
        FileHeader header = logReader.getHeader();
        System.out.println("HEADER");
        System.out.println("------");
        System.out.println(header);
        System.out.println("------");
		long lastPosition = 0;

		try {
			RedoableOp op = null;
			while ((op = logReader.getNextOp()) != null) {
				lastPosition = logReader.position();
                if (!mQuiet)
    				System.out.println(op);
                if (mDumpMessageBody && op instanceof CreateMessage) {
                	CreateMessage cm = (CreateMessage) op;
                    byte[] body = cm.getMessageBody();
                    if (body != null) {
                        if (ByteUtil.isGzipped(body)) {
                        	body = ByteUtil.uncompress(body);
                        }
                        System.out.print(new String(body));
                        System.out.println("<END OF MESSAGE>");
                    }
                }
			}
			good = true;
		} catch (IOException e) {
			// The IOException could be a real I/O problem or it could mean
			// there was a server crash previously and there were half-written
			// log entries.  We can't really tell which case it is, so just
			// assume the second case and truncate the file after the last
			// successfully read item.

			long size = logReader.getSize();
			if (lastPosition < size) {
				long diff = size - lastPosition;
				System.out.println("There were " + diff + " bytes of junk data at the end.");
                throw e;
			}
		} finally {
			logReader.close();
		}
		return good;
	}

    private boolean verifyFile(File file) {
        System.out.println("VERIFYING: " + file.getName());
        boolean good = false;
        try {
            good = scanLog(file);
        } catch (IOException e) {
            mBadFiles.add(new BadFile(file, e));
            System.err.println("Exception while verifying " + file.getName());
            e.printStackTrace();
        }
        System.out.println();
        return good;
    }

    private boolean verifyFiles(File[] files) {
        boolean allGood = true;
        for (File log : files) {
            boolean b = verifyFile(log);
            allGood = allGood && b;
        }
        return allGood;
    }

    private boolean verifyDirectory(File dir) {
        System.out.println("VERIFYING DIRECTORY: " + dir.getName());
        File[] all = dir.listFiles();
        if (all == null || all.length == 0)
            return true;

        List<File> fileList = new ArrayList<File>(all.length);
        for (File f : all) {
            if (!f.isDirectory()) {
                String fname = f.getName();
                if (fname.lastIndexOf(".log") == fname.length() - 4)
                    fileList.add(f);
            }
        }

        File[] files = new File[fileList.size()];
        fileList.toArray(files);
        RolloverManager.sortArchiveLogFiles(files);
        return verifyFiles(files);
    }

    private void listErrors() {
        if (mBadFiles.size() == 0)
            return;
        System.out.println();
        System.out.println();
        System.out.println("-----------------------------------------------");
        System.out.println();
        System.out.println("The following files had errors:");
        System.out.println();
        for (BadFile bf : mBadFiles) {
            System.out.println(bf.file.getName());
            System.out.println("    " + bf.error.getMessage());
        }
    }

	public static void main(String[] cmdlineargs) throws Exception {
        Zimbra.toolSetup();
        CommandLine cl = parseArgs(cmdlineargs);
        String[] args = cl.getArgs();

		if (args.length < 1)
			usage(null);

        //FileLogReader.setSkipBadBytes(true);

		boolean allGood = true;
		RedoLogVerify verify =
            new RedoLogVerify(cl.hasOption('q'), cl.hasOption('m'));

		for (int i = 0; i < args.length; i++) {
			File f = new File(args[i]);
			boolean good = false;
            if (f.isDirectory())
                good = verify.verifyDirectory(f);
            else
                good = verify.verifyFile(f);
            allGood = allGood && good;
			System.out.println();
		}

		if (!allGood) {
		    verify.listErrors();
            System.exit(1);
        }
	}
}
