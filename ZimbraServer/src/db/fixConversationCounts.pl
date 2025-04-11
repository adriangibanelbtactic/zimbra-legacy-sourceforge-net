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
# Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
# All Rights Reserved.
# 
# Contributor(s):
# 
# ***** END LICENSE BLOCK *****
# 


use strict;
use Migrate;


my @mailboxIds = Migrate::getMailboxIds();
foreach my $id (@mailboxIds) {
    fixConversationCount($id);
}

exit(0);

#####################

sub fixConversationCount($) {
    my ($mailboxId) = @_;
    my $sql = <<EOF;
UPDATE mailbox$mailboxId.mail_item
LEFT JOIN (SELECT parent_id conv, COUNT(*) cnt FROM mailbox$mailboxId.mail_item WHERE type = 5 GROUP BY parent_id) c ON id = conv
SET size = IFNULL(cnt, 0)
WHERE type = 4;

DELETE FROM mailbox$mailboxId.mail_item WHERE type = 4 AND size = 0;

EOF
    
    Migrate::log("Updating SIZE for conversation rows in mailbox$mailboxId.mail_item.");
    Migrate::runSql($sql);
}
