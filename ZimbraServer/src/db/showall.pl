#!/usr/bin/perl -w
# 
# ***** BEGIN LICENSE BLOCK *****
# Version: MPL 1.1
# 
# The contents of this file are subject to the Mozilla Public License
# Version 1.1 ("License"); you may not use this file except in
# compliance with the License. You may obtain a copy of the License at
# http://www.zimbra.com/license
# 
# Software distributed under the License is distributed on an "AS IS"
# basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
# the License for the specific language governing rights and limitations
# under the License.
# 
# The Original Code is: Zimbra Collaboration Suite Server.
# 
# The Initial Developer of the Original Code is Zimbra, Inc.
# Portions created by Zimbra are Copyright (C) 2005, 2006 Zimbra, Inc.
# All Rights Reserved.
# 
# Contributor(s):
# 
# ***** END LICENSE BLOCK *****
# 

# Prints the output of SHOW CREATE TABLE for all zimbra databases

use strict;
use Migrate;

my @databases = Migrate::runSql("SHOW DATABASES;", 0);
foreach my $database (@databases) {
    $database = lc($database);
    if ($database eq "zimbra" || $database =~ /^mailbox[0-9]+$/) {
	print("Database $database:\n");
	my @tables = Migrate::runSql("SHOW TABLES FROM $database;", 0);
	foreach my $table (@tables) {
	    my $row = (Migrate::runSql("SHOW CREATE TABLE $database.$table;", 0))[0];
	    my $create = (split("\t", $row))[1];
	    $create =~ s/\\n/\n/g;
	    print("\n" . $create . "\n");
	}
    }
}
