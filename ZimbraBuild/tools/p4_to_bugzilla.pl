#!/usr/bin/perl -w

use strict;
use IO::File;
use Data::Dumper;
use WWW::Bugzilla;
use WWW::Mechanize;

sub append_to_changelist($$);
sub get_changelist_desc($);
sub check_bug_trigger($);

my $NONE_MODE = 0;
my $DEBUG_MODE = 0;

if (defined($ARGV[0])) {
    if ($ARGV[0] eq "-oknone") {
	shift(@ARGV);
	$NONE_MODE = 1;
    }
}

if (defined($ARGV[0])) {
    if ($ARGV[0] eq "-d") {
	shift(@ARGV);
	$DEBUG_MODE = 2;
    }
}


my $id = $ARGV[0];

if (!defined($id) || $id eq "") {
    $DEBUG_MODE = 1;
}

if ($DEBUG_MODE == 0) {
    my $cl = get_changelist_desc($id);
    print "CL is \n$cl\n";
    run($cl);
} else {
    if ($DEBUG_MODE == 2) {
	my $cl = get_changelist_desc($id);
	run($cl);
	exit(0);
    }
    #
    # NOTE TO REMEMBER -- test bug is #896
    #
    my @TEST_BUG_TEXT;
    push(@TEST_BUG_TEXT,"\tbug: 1, 1, 2, 3  added 50MB crap to source tree\n\tHi 99 mom\n\tbug:100,101\n\tbug:305 10MB of more crap");
    push(@TEST_BUG_TEXT,"\tTest\n\tbug: 896 10MB\n\tfoo\n");
    push(@TEST_BUG_TEXT,"\tTest\n\tbug: none This is a quick checking for 10 20 test.\n");
    my $tmp = <<_TEST_BUG_TEXT_;
\tbug: 6803 (addendum) - fixes sending new messages..

This is a long line of text.  This is a long line of text.  This is a long line of text.  This is a long line of text.  This is a long line of text.  This is a long line of text.  This is a long line of text.  This is a long line of text.  
Another long line of text.  Another long line of text.  Another long line of text.  Another long line of text.  Another long line of text.  Another long line of text.  Another long line of text.  Another long line of text.  

A short line here.
One more short line.

Affected files ...

Yet another long line of text...  Yet another long line of text...  Yet another long line of text...  Yet another long line of text...  Yet another long line of text...  
One more short line
_TEST_BUG_TEXT_
    push(@TEST_BUG_TEXT, $tmp);

    $tmp = <<_TEST_BUG_TEXT3_;
\tbug: 4210, 6019, 6954
      
      Add an IMAP_ID column to the user mailbox database; this defaults to the item ID for most leaf-node types.  The ZIMBRA.MAILBOX table now has a TRACKING_IMAP boolean which is set the first time a user logs in via IMAP.  The TRACKING_SYNC column in ZIMBRA.MAILBOX is now an INTEGER; we will be using this in the future to track trimming the TOMBSTONE table.  Database version is now 23; please reset-the-world or run the script ZimbraServer/src/db/migrate20060515-AddImapId.pl to bring your build into the new world.
      
      When loading non-search folders in IMAP, we now directly fetch only the relevant fixed-width columns from the database (ID, IMAP_ID, TYPE, FLAGS, TAGS, UNREAD) and don't instantiate any MailItems in the Mailbox.  Items that have an IMAP_ID <=0 have a new IMAP ID assigned at folder load time; this state occurs automatically when an item is moved via a non-IMAP interface when TRACKING_IMAP is true for the account.  Certain message attributes that are seldom fetched without fetching the message body (date, size) are no longer precached when the IMAP folder is loaded but are instead retrieved by fetching the item on demand from the Mailbox.

Expose contacts via IMAP as messages with a text/x-vcard Content-Type with UTF-8 content.  The Contacts folder is readable but not writable.

Move IMAP ID up to MailItem.  Also collect blob-writing code in MailItem.setContent(), so we're no longer duplicating the code in Message and Appointment.  Note that Document does not yet share this blob-wrangling code.

vCards are now \r\n-delimited.  Please let me know if this breaks anything...

Affected files ...

... //depot/main/ZimbraServer/src/db/create_database.sql#11 edit
... //depot/main/ZimbraServer/src/db/db.sql#19 edit
... //depot/main/ZimbraServer/src/db/migrate20060515-AddImapId.pl#1 add
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/db/DbMailItem.java#48 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/db/DbMailbox.java#21 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/db/Versions.java#18 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/imap/ImapAppendOperation.java#4 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/imap/ImapFolder.java#22 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/imap/ImapHandler.java#66 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/imap/ImapListOperation.java#6 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/imap/ImapMessage.java#21 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/imap/ImapSession.java#31 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/imap/ImapSessionHandler.java#7 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/imap/OzImapConnectionHandler.java#53 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/index/DBQueryOperation.java#43 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/mailbox/Appointment.java#79 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/mailbox/Contact.java#19 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/mailbox/Document.java#28 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/mailbox/MailItem.java#43 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/mailbox/Mailbox.java#137 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/mailbox/Message.java#37 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/mime/Mime.java#24 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/redolog/op/RedoableOp.java#25 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/redolog/op/SetImapUid.java#8 edit
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/redolog/op/TrackImap.java#1 add
... //depot/main/ZimbraServer/src/java/com/zimbra/cs/service/formatter/VCard.java#7 edit

_TEST_BUG_TEXT3_
    push(@TEST_BUG_TEXT, $tmp);

    foreach (@TEST_BUG_TEXT) {
	print "\n*******************************************************************************************\n\n";
	run($_);
    }
}

sub run
{
    my $cl = shift();

    print "Changelist is:\n $cl\n---------------\n\n";
    my $bugUrls = check_bug_trigger($cl);
    
    if ($bugUrls ne "") {
	if ($NONE_MODE == 1 && $bugUrls eq "NONE") {
	    if ($DEBUG_MODE == 0) {
		exit 0;
	    } else {
		print "Allowing \"Bug: none\" entry b/c -oknone switch is set\n";
	    }
	}
	if ($DEBUG_MODE == 0) {
	    append_to_changelist($id, $bugUrls);
	} else {
	    print "...SKIPPING append_to_changelist (debug mode)...\n";
	    print "--------------\nBugURLS:\n$bugUrls\n";
	}
    } else {
	# Trigger an error on the commit.
	print "No bug found in submission\n";
	exit 1; 
    }
}

#####################################

sub append_to_changelist($$)
{
    my ($id, $urls) = @_;

    my $cmd = "/usr/local/p4/bin/p4 -p eric:1666 -c DEPOT -u review_daemon -P Reviewer change -o $id 2>&1";
    my $clText = `$cmd`;
    
    print "Got full changelist text for $id:\n$clText\n";
    $clText =~ s/^Description:\n/Description:\n$urls/m;
    
    print "\nUpdated changelist text for $id:\n$clText\n";
    
    $cmd = "/usr/local/p4/bin/p4 -p eric:1666 -c DEPOT  -u review_daemon -P Reviewer change -i > /tmp/p4rewrite.out 2>&1";

    open CMD, "|$cmd";
    print CMD $clText."\n";
    close CMD;
}

sub get_changelist_desc($)
{
    my ($changelistId) = @_;

    my $cmd = "/usr/local/p4/bin/p4 -p eric:1666 -c FOO -u review_daemon -P Reviewer describe -s $changelistId";

    return `$cmd`;
}

sub check_bug_trigger($) {
    my ($message) = @_;
    my $bugText = "";

#    print "Message:\n$message\n";
#    if ($message =~ /schemers/) {
#	if (open(FH, ">/tmp/p4_schemers.txt")) {
#	    print FH $message;
#	    close(FH);
#	}
#    }
    my $fitText = fitToWidth($message, 70);

    if ($NONE_MODE == 1) {
	if ($message=~/^[\t\s]+bug\:?\s*none/mgi) {
	    return "NONE";
	}
    }


    my @lines = split("\n", $message);

    foreach (@lines) {
#	print "Line is \"$_\"\n";
	if (/^[\t\s]+bug/i) {
	    #
	    # Chop the bug: part off the front
	    # 
	    s/^[\t\s]+bug:?\s*//i;

	    #
	    # End the line at the first nondigit...
	    #
#	    s/\d+[^\d,\s]/CENSORED/g;
	    s/[\d]*[^\d\s,]/END/;
	    
	    my @foo = split("END");
	    $_ = $foo[0];
	    
#	    print "LINE IS NOW: \"$_\"\n";

	    #
	    # add space to the end, easier to write regex below
	    #
	    $_ = $_ . " ";

	    # only allow one bug: line
	    my $found_bugid = 0;

#	    print "\tRegexing: \"$_\"\n";
	    my @matches = /([\d]+)[\s,]+/g;
	    foreach(@matches) {
		my $bugid = $_;
#		print "BUGID: $bugid\n";
		
		$found_bugid = 1;
		
		if ($DEBUG_MODE == 0) {
		    eval{update_bug($bugid, $fitText);};
		}
		if ($@){print "error: $@\n";} else {
		    $bugText .= "\thttp://bugzilla.zimbra.com/show_bug.cgi?id=$bugid\n";
		}
		
	    }

	    if ($found_bugid == 1) {
		return $bugText;
	    }
		
	}
    }
    return $bugText;
}


sub update_bug_original($$) {
    my $BUGSERVER = 'bugzilla.liquidsys.com';
    my $BUGEMAIL = 'cvsuser@zimbra.com';
    my $BUGPASSWORD = "test123";

    my ($bugid, $message) = @_;

    my $bz = WWW::Bugzilla->new(    server => $BUGSERVER,
				    email => $BUGEMAIL,
				    password => $BUGPASSWORD, 
				    bug_number => $bugid );

    $bz->additional_comments($message);

    my $out = $bz->commit;
}


sub update_bug($$) {

	my ($bugid, $msg) = @_;

#    my $BUGSERVER = 'bugzilla.zimbra.com';
    my $BUGSERVER = '72.3.250.100';
    my $BUGEMAIL = 'cvsuser@zimbra.com';
    my $BUGPASSWORD = "test123";

	# the bugzilla form is now buried by flickerbox
	# we can't use WWW::Bugzilla anymore
	my $mech = WWW::Mechanize->new();


	# Login as the cvs user
	#
	$mech->get("http://$BUGSERVER/query.cgi?GoAheadAndLogIn=1");

	# Find the bugzilla form on the page
	$mech->form_number(2);

	$mech->field('Bugzilla_login', $BUGEMAIL);
	$mech->field('Bugzilla_password', $BUGPASSWORD);

	$mech->submit_form();

	# Change the specified bugid
	#
	$mech->get("http://$BUGSERVER/show_bug.cgi?id=$bugid");

	if ($mech->title() eq "Invalid Bug ID") {die "Invalid bug ID: $bugid";}

	# Find the bugzilla form on the page
	$mech->form_number(2);

	$mech->field('comment', $msg);

	$mech->submit_form();

}

sub fitToWidth($$) {
    my ($text, $width) = @_;
    my $fitText = '';

    my @lines = split("\n", $text);
    my $foundFileList = 0;
    foreach my $line (@lines) {
		chomp($line);
		if ($foundFileList) {
			$fitText .= $line . "\n";
			next;
		}

		if ($line =~ /^\s*Affected files \.\.\./) {
			$foundFileList = 1;
			$fitText .= $line . "\n";
			next;
		}

		# Note: Each line starts with a tab character.
		# p4 must be doing that for some reason.
		if (length($line) > $width) {
			while (length($line) > $width) {
			# Find nearest whitespace before $width.
			my $splitAt = $width - 1;
			while ($splitAt > 0) {
				my $ch = substr($line, $splitAt, 1);
				if ($ch =~ /^\s$/) {
				last;
				}
				$splitAt--;
			}
			if ($splitAt == 0) {
				$splitAt = $width;
			}

			# Include all trailing whitespaces on current line.
			while ($splitAt < length($line)) {
				my $ch = substr($line, $splitAt, 1);
				if ($ch =~ /^\s$/) {
				$splitAt++;
				next;
				} else {
				last;
				}
			}

			my $piece = substr($line, 0, $splitAt);
			if (length($line) > $splitAt) {
				$line = "\t" . substr($line, $splitAt);
			} else {
				$line = '';
			}
			$fitText .= $piece . "\n";
			}
			$fitText .= $line . "\n" if ($line ne '');  # last remaining piece
		} else {
			$fitText .= $line . "\n";
		}
	}

    return $fitText;
}
