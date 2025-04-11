#!/usr/bin/perl -w

##########################################
#
# **** CHANGES TO THIS FILE _MUST_ BE CHECKED INTO P4  *****
#
# This file lives in ZimbraBuild/tools, if you modify this file you
# MUST checkin the new version, otherwise your edits may be overwritten
# at any time.
#
# YOU HAVE BEEN WARNED!
#
############################################

#
# This script is designed to be run as a p4 'form-in' trigger for
# change forms.  Basically, whenever a chagnelist is saved, it looks
# for "bug:" lines and it adds the bugzilla URL to the changelist for
# the bug.
#

##
## Perforce requires messages on stdout
##
open(STDERR, ">&STDOUT") or die "Can't dup stdout";


use lib '/data/p4/bin';

use strict;
use warnings;
use IO::File;
use Data::Dumper;
use WWW::Bugzilla;
use WWW::Mechanize;
use Getopt::Long;
use ZP4;

sub update_changelist_desc($);
sub parse_bug_specifier($);
sub run_with_test_data();

my $debugMode;                  # debug level 1,2 or 3
my $testData;                   # if set, then use test data
my $formFile;                   # form file
my $formType;                   # see the p4 triggers documentation
my $log;

GetOptions("d=s" => \$debugMode,
           "ffile=s" => \$formFile,
           "ftype=s" => \$formType,
           "t" => \$testData,
           "log=s" => \$log
          );

if ((!defined($formFile) && !defined($testData)) || (defined($formFile) && defined($testData)) ||
    (defined($formFile) && !defined($formType))) {
  my $usage = <<END_OF_USAGE;
USAGE: $0 [-d LEVEL] (-t OR (-ffile FILENAME -ftype FORM_TYPE))
    -d LEVEL:     debug level (1,2 or 3)
    -t:           use test data (embedded in this script)
    -ffile:       file to modify (changelist form see p4 documentation for a form-in trigger)
    -ftype:       form type (see p4 docs)
    -log FILENAME log output (for debugging p4)
END_OF_USAGE
  die $usage;
}

ZP4::set_debug_level($debugMode);
ZP4::start_logging($log);

if (defined($formFile)) {
  if ($formType ne "change") {
    ZP4::debug(1, "Not a 'change' form, skipping");
    ZP4::do_exit(0);
  }
  
  my $data = "";
  {
    open IN, "<$formFile" or die "Couldn't open file $formFile for reading: $!";
    local $/;
    $data = <IN>;
    close IN;
  }
  ZP4::debug(2, "File contents:\n$data");
  my $result = update_changelist_desc($data);
  ZP4::debug(1,"---------\nResult:\n$result");

  open OUT, ">$formFile" or die "Couldn't open file $formFile for writing: $!";
  print OUT $result;
  close OUT;
} else {
  run_with_test_data();
}
ZP4::do_exit(0);

######################################################################



#################
# Remove any existing bugzilla URLs from the changelist
# Add the current set of bugzilla URLs to the changelist
#
# Return the changelist as a string
#
sub update_changelist_desc($)  {
  my ($cl) = @_;
  my $bugUrlBlock =  parse_bug_specifier($cl);

  if ($bugUrlBlock eq "NONE") {
    $bugUrlBlock = "";
  }

  ZP4::debug(1, "CL:\n$cl");

  # split the data into three parts - the pre, description, and post
  if (!($cl =~ /(.*\n)(Description:.*)/s)) {
    die "Couldn't parse change description";
  }

  my $pre = $1;
  my $second = $2;

  ZP4::debug(3, "pre:\n$pre");
  ZP4::debug(3, "second:\n$second");
  
  my $description;
  my $post;

  if ($second =~ /(Description:.*)(Files:.*)/s) {
    ZP4::debug(3, "HAS FILES PART!");
    $description = $1;
    $post = $2;
  } else {
    ZP4::debug(3, "NO FILES PART!");
    $description = $second;
  }

  if (!defined $post) {
    $post = "";
  }

  # strip out any old bugzilla URL's from the description part
  $description =~ s/^\s+http:\/\/bugzilla.zimbra.com\/show_bug.cgi\?id=.*//mg;


  ZP4::debug(3, "PRE:\n-----------\n$pre");
  ZP4::debug(2, "DESCRIPTION:\n-----------\n$description");
  ZP4::debug(3, "POST:\n-----------\n$post");

  ZP4::debug(3, "BUGURLBLOCK:\n-----------\n$bugUrlBlock");

  # start building our return value...
  my $toRet = $pre;

  # Walk backward through the description, and find the last nonblank line
  # in the description
  my @lines = split(/\n/, $description);
  my $i = @lines-1;
  my $done = 0;
  while ($i > 0 && ($done == 0)) {
    ZP4::debug(3, "lines is \"$lines[$i]\"");
    if ($lines[$i] ne "" && (!($lines[$i] =~ m/^\s+$/))) {
      $done = 1;
    } else {
      $i--;
    }
  }

  ZP4::debug(3, "i is $i");
  # i is now the index of the first non-blank line in the description
  # copy the non-blank lines into the result
  my $j = 0;
  while ($j <= $i) {
    $toRet .= $lines[$j]."\n";
    $j++;
  }

  # put the rest of the data into the result
  $toRet .= "\n$bugUrlBlock\n$post";

  return $toRet;
}

#################
# Scan the changelist description and find all the "bug:" lines,
# return a bunch of URLs (into bugzilla) separated by \n's
#
sub parse_bug_specifier($) {
  my ($message) = @_;
  my $bugText = "";

  ZP4::debug(3, "In parse_bug_specifier!");
  
  my @lines = split("\n", $message);

  foreach (@lines) {
    ZP4::debug(3, "Line is \"$_\"");

    if (/^[\t\s]+bug\:?\s*none/mgi) {
      ZP4::debug(3, "Bug is NONE");
      return "NONE";
    }
    
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

      # only allow one bug: line, stop after that...
      my $found_bugid = 0;
      
      #	    print "\tRegexing: \"$_\"\n";
      my @matches = /([\d]+)[\s,]+/g;
      foreach (@matches) {
		my $bugid = $_;
        ZP4::debug(3, "found BUGID: $bugid");
		$found_bugid = 1;
        $bugText .= "\thttp://bugzilla.zimbra.com/show_bug.cgi?id=$bugid\n";
      }

      if ($found_bugid == 1) {
		return $bugText;
      }
	}                           # if starts with bug
  }                             # foreach line
  return $bugText;
}

#################
#
#
sub run_with_test_data()
{
  foreach (ZP4::get_test_change_form_data()) {
	print "\n**************************************************\n\n";
    my $toCheck = $_;
    my $result = update_changelist_desc($toCheck);
    print "--------------\nResult:\n$result\n";
  }
}
