#!/usr/bin/perl
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
# Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
# All Rights Reserved.
# 
# Contributor(s):
# 
# ***** END LICENSE BLOCK *****
# 


use strict;
use Migrate;


Migrate::verifyLoggerSchemaVersion(2);

addIndices();

Migrate::updateLoggerSchemaVersion(2,3);

exit(0);

#####################

sub addIndices() {
    Migrate::log("Adding Indices");

	my $sql = <<EOF;
alter table disk_aggregate add index i_device (device);
alter table disk_aggregate add index i_host (host);
alter table disk_aggregate add index i_period_start (period_start);
alter table disk_aggregate add index i_period_end (period_end);
EOF

    Migrate::runLoggerSql($sql);
}
