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
# Portions created by Zimbra are Copyright (C) 2005, 2006 Zimbra, Inc.
# All Rights Reserved.
# 
# Contributor(s):
#
# 
# ***** END LICENSE BLOCK *****
# 

package zmupgrade;

use strict;
use lib "/opt/zimbra/libexec/scripts";
use Migrate;
use Net::LDAP;

my $type = `zmlocalconfig -m nokey convertd_stub_name 2> /dev/null`;
chomp $type;
if ($type eq "") {$type = "FOSS";}
else {$type = "NETWORK";}

my $rundir = `dirname $0`;
chomp $rundir;
my $scriptDir = "/opt/zimbra/libexec/scripts";

my $lowVersion = 18;
my $hiVersion = 36;
my $hiLoggerVersion = 5;

# Variables for the combo schema updater
my $comboLowVersion = 20;
my $comboHiVersion  = 27;

my $hn = `su - zimbra -c "zmlocalconfig -m nokey zimbra_server_hostname"`;
chomp $hn;

my $ZMPROV = "/opt/zimbra/bin/zmprov -l --";

my %updateScripts = (
  'ComboUpdater' => "migrate-ComboUpdater.pl",
	'UniqueVolume' => "migrate20051021-UniqueVolume.pl",
	'18' => "migrate20050916-Volume.pl",
	'19' => "migrate20050920-CompressionThreshold.pl",
	'20' => "migrate20050927-DropRedologSequence.pl",    # 3.1.2
	'21' => "migrate20060412-NotebookFolder.pl",
	'22' => "migrate20060515-AddImapId.pl",
	'23' => "migrate20060518-EmailedContactsFolder.pl",
	'24' => "migrate20060708-FlagCalendarFolder.pl",
	'25' => "migrate20060803-CreateMailboxMetadata.pl",
	'26' => "migrate20060810-PersistFolderCounts.pl",    # 4.0.2
	'27' => "migrate20060911-MailboxGroup.pl",           # 4.5.0_BETA1
	'28' => "migrate20060929-TypedTombstones.pl",
  '29' => "migrate20061101-IMFolder.pl",               # 4.5.0_RC1
  '30' => "migrate20061117-TasksFolder.pl",            # 4.5.0_RC1
  '31' => "migrate20061120-AddNameColumn.pl",          # 4.5.0_RC1
  '32' => "migrate20061204-CreatePop3MessageTable.pl", # 4.5.0_RC1
  '33' => "migrate20061205-UniqueAppointmentIndex.pl", # 4.5.0_RC1
  '34' => "migrate20061212-RepairMutableIndexIds.pl",  # 4.5.0_RC1
  '35' => "migrate20061221-RecalculateFolderSizes.pl", # 4.5.0_GA
);

my %loggerUpdateScripts = (
	'0' => "migrateLogger1-index.pl",
	'1' => "migrateLogger2-config.pl",
	'2' => "migrateLogger3-diskindex.pl",
	'3' => "migrateLogger4-loghostname.pl",
	'4' => "migrateLogger5-qid.pl",
);

my %updateFuncs = (
	"3.0.M1" => \&upgradeBM1,
	"3.0.0_M2" => \&upgradeBM2,
	"3.0.0_M3" => \&upgradeBM3,
	"3.0.0_M4" => \&upgradeBM4,
	"3.0.0_GA" => \&upgradeBGA,
	"3.0.1_GA" => \&upgrade301GA,
	"3.1.0_GA" => \&upgrade310GA,
	"3.1.1_GA" => \&upgrade311GA,
	"3.1.2_GA" => \&upgrade312GA,
	"3.1.3_GA" => \&upgrade313GA,
	"3.1.4_GA" => \&upgrade314GA,
	"3.2.0_M1" => \&upgrade32M1,
	"3.2.0_M2" => \&upgrade32M2,
	"4.0.0_RC1" => \&upgrade400RC1,
	"4.0.0_GA" => \&upgrade400GA,
	"4.0.1_GA" => \&upgrade401GA,
	"4.0.2_GA" => \&upgrade402GA,
	"4.0.3_GA" => \&upgrade403GA,
	"4.0.4_GA" => \&upgrade404GA,
	"4.0.5_GA" => \&upgrade405GA,
	"4.1.0_BETA1" => \&upgrade410BETA1,
  "4.5.0_BETA1" => \&upgrade450BETA1,
	"4.5.0_BETA2" => \&upgrade450BETA2,
	"4.5.0_RC1" => \&upgrade450RC1,
	"4.5.0_RC2" => \&upgrade450RC2,
	"4.5.0_GA" => \&upgrade450GA,
	"4.5.1_GA" => \&upgrade451GA,
	"4.5.2_GA" => \&upgrade452GA,
	"4.5.3_GA" => \&upgrade453GA,
	"4.5.4_GA" => \&upgrade454GA,
	"4.5.5_GA" => \&upgrade455GA,
	"4.5.6_GA" => \&upgrade456GA,
	"5.0.0_BETA1" => \&upgrade500BETA1,
	"5.0.0_BETA2" => \&upgrade500BETA2,
	"5.0.0_RC1" => \&upgrade500RC1,
	"5.0.0_RC2" => \&upgrade500RC2,
	"5.0.0_GA" => \&upgrade500GA,
);

my @versionOrder = (
	"3.0.M1", 
	"3.0.0_M2", 
	"3.0.0_M3", 
	"3.0.0_M4", 
	"3.0.0_GA", 
	"3.0.1_GA", 
	"3.1.0_GA", 
	"3.1.1_GA", 
	"3.1.2_GA", 
	"3.1.3_GA", 
	"3.1.4_GA", 
	"3.2.0_M1",
	"3.2.0_M2",
	"4.0.0_RC1",
	"4.0.0_GA",
	"4.0.1_GA",
	"4.0.2_GA",
	"4.0.3_GA",
	"4.0.4_GA",
	"4.0.5_GA",
	"4.1.0_BETA1",
	"4.5.0_BETA1",
	"4.5.0_BETA2",
	"4.5.0_RC1",
	"4.5.0_RC2",
	"4.5.0_GA",
	"4.5.1_GA",
	"4.5.2_GA",
	"4.5.3_GA",
	"4.5.4_GA",
	"4.5.5_GA",
	"4.5.6_GA",
  "5.0.0_BETA1",
  "5.0.0_BETA2",
  "5.0.0_RC1",
  "5.0.0_RC2",
  "5.0.0_GA",
);

my $startVersion;
my $targetVersion;

my @packageList = (
	"zimbra-core",
	"zimbra-ldap",
	"zimbra-store",
	"zimbra-mta",
	"zimbra-snmp",
	"zimbra-logger",
	"zimbra-apache",
	"zimbra-spell",
	);

my %installedPackages = ();
my $platform = `/opt/zimbra/libexec/get_plat_tag.sh`;
chomp $platform;
my $addr_space = (($platform =~ m/\w+_(\d+)/) ? "$1" : "32");

#####################

sub upgrade {
	$startVersion = shift;
	$targetVersion = shift;
	my $startBuild = $startVersion;
	$startBuild =~ s/.*_//;
	my $targetBuild = $targetVersion;
	$targetBuild =~ s/.*_//;

	$startVersion =~ s/_$startBuild//;
	$targetVersion =~ s/_$targetBuild//;

	my $needVolumeHack = 0;
	my $needMysqlTableCheck = 0;

	getInstalledPackages();

	if (stopZimbra()) { return 1; }

	my $curSchemaVersion;
	my $curLoggerSchemaVersion;

	if (isInstalled("zimbra-store")) {

    &verifyMysqlConfig;

    if (startSql()) { return 1; };

		$curSchemaVersion = Migrate::getSchemaVersion();
	}

	if (isInstalled("zimbra-logger") && -d "/opt/zimbra/logger/db/data/zimbra_logger/") {
		if (startLoggerSql()) { return 1; }

		if ($startVersion eq "3.0.0_M2") {
			$curLoggerSchemaVersion = 0;
		} elsif ($startVersion eq "3.0.0_M3" && $startBuild < 285) {
			$curLoggerSchemaVersion = 1;
		} else {
			$curLoggerSchemaVersion = Migrate::getLoggerSchemaVersion();
		}

		if ($curLoggerSchemaVersion eq "") {
			$curLoggerSchemaVersion = 1;
		}
	}

	if ($startVersion eq "3.0.0_GA") {
		main::progress("This appears to be 3.0.0_GA\n");
	} elsif ($startVersion eq "3.0.1_GA") {
		main::progress("This appears to be 3.0.1_GA\n");
	} elsif ($startVersion eq "3.1.0_GA") {
		main::progress("This appears to be 3.1.0_GA\n");
		#$needVolumeHack = 1;
	} elsif ($startVersion eq "3.1.1_GA") {
		main::progress("This appears to be 3.1.1_GA\n");
	} elsif ($startVersion eq "3.1.2_GA") {
		main::progress("This appears to be 3.1.2_GA\n");
	} elsif ($startVersion eq "3.1.3_GA") {
		main::progress("This appears to be 3.1.3_GA\n");
	} elsif ($startVersion eq "3.1.4_GA") {
		main::progress("This appears to be 3.1.4_GA\n");
	} elsif ($startVersion eq "3.2.0_M1") {
		main::progress("This appears to be 3.2.0_M1\n");
	} elsif ($startVersion eq "3.2.0_M2") {
		main::progress("This appears to be 3.2.0_M2\n");
	} elsif ($startVersion eq "4.0.0_RC1") {
		main::progress("This appears to be 4.0.0_RC1\n");
	} elsif ($startVersion eq "4.0.0_GA") {
		main::progress("This appears to be 4.0.0_GA\n");
	} elsif ($startVersion eq "4.0.1_GA") {
		main::progress("This appears to be 4.0.1_GA\n");
	} elsif ($startVersion eq "4.0.2_GA") {
		main::progress("This appears to be 4.0.2_GA\n");
	} elsif ($startVersion eq "4.0.3_GA") {
		main::progress("This appears to be 4.0.3_GA\n");
	} elsif ($startVersion eq "4.0.4_GA") {
		main::progress("This appears to be 4.0.4_GA\n");
	} elsif ($startVersion eq "4.0.5_GA") {
		main::progress("This appears to be 4.0.5_GA\n");
	} elsif ($startVersion eq "4.1.0_BETA1") {
		main::progress("This appears to be 4.1.0_BETA1\n");
	} elsif ($startVersion eq "4.5.0_BETA1") {
		main::progress("This appears to be 4.5.0_BETA1\n");
	} elsif ($startVersion eq "4.5.0_BETA2") {
		main::progress("This appears to be 4.5.0_BETA2\n");
	} elsif ($startVersion eq "4.5.0_RC1") {
		main::progress("This appears to be 4.5.0_RC1\n");
	} elsif ($startVersion eq "4.5.0_RC2") {
		main::progress("This appears to be 4.5.0_RC2\n");
	} elsif ($startVersion eq "4.5.0_GA") {
		main::progress("This appears to be 4.5.0_GA\n");
	} elsif ($startVersion eq "4.5.1_GA") {
		main::progress("This appears to be 4.5.1_GA\n");
	} elsif ($startVersion eq "4.5.2_GA") {
		main::progress("This appears to be 4.5.2_GA\n");
	} elsif ($startVersion eq "4.5.3_GA") {
		main::progress("This appears to be 4.5.3_GA\n");
	} elsif ($startVersion eq "4.5.4_GA") {
		main::progress("This appears to be 4.5.4_GA\n");
	} elsif ($startVersion eq "4.5.5_GA") {
		main::progress("This appears to be 4.5.5_GA\n");
	} elsif ($startVersion eq "4.5.6_GA") {
		main::progress("This appears to be 4.5.6_GA\n");
	} elsif ($startVersion eq "5.0.0_BETA1") {
		main::progress("This appears to be 5.0.0_BETA1\n");
	} elsif ($startVersion eq "5.0.0_BETA2") {
		main::progress("This appears to be 5.0.0_BETA2\n");
	} elsif ($startVersion eq "5.0.0_RC1") {
		main::progress("This appears to be 5.0.0_RC1\n");
	} elsif ($startVersion eq "5.0.0_RC2") {
		main::progress("This appears to be 5.0.0_RC2\n");
	} elsif ($startVersion eq "5.0.0_GA") {
		main::progress("This appears to be 5.0.0_GA\n");
	} else {
		main::progress("I can't upgrade version $startVersion\n\n");
		return 1;
	}

  if ($targetVersion eq "4.5.2_GA") {
    $needMysqlTableCheck=1;
  }

	if (isInstalled("zimbra-store")) {

    doMysqlTableCheck() if ($needMysqlTableCheck);
  
    doBackupRestoreVersionUpdate($startVersion);

		if ($curSchemaVersion < $hiVersion) {
			main::progress("Schema upgrade required\n");
		}

    # fast tracked updater (ie invoke mysql once)
    if ($curSchemaVersion >= $comboLowVersion && $curSchemaVersion < $comboHiVersion) {
      if (runSchemaUpgrade("ComboUpdater")) { return 1; }
      $curSchemaVersion = Migrate::getSchemaVersion();
    }

    # the old slow painful way (ie lots of mysql invocations)
		while ($curSchemaVersion >= $lowVersion && $curSchemaVersion < $hiVersion) {
			if (($curSchemaVersion == 21) && $needVolumeHack) {
				if (runSchemaUpgrade ("UniqueVolume")) { return 1; }
			} 
			if (runSchemaUpgrade ($curSchemaVersion)) { return 1; }
			$curSchemaVersion++;
		}
		stopSql();
	}

	if (isInstalled ("zimbra-logger") && -d "/opt/zimbra/logger/db/data/zimbra_logger/") {
		if ($curLoggerSchemaVersion < $hiLoggerVersion) {
			main::progress("An upgrade of the logger schema is necessary from version $curLoggerSchemaVersion\n");
		}

		while ($curLoggerSchemaVersion < $hiLoggerVersion) {
			if (runLoggerSchemaUpgrade ($curLoggerSchemaVersion)) { return 1; }
			$curLoggerSchemaVersion++;
		}
		stopLoggerSql();
	}

	my $found = 0;

  #migrateLdap();
  # start ldap
	if (isInstalled ("zimbra-ldap")) {
    if (startLdap()) {return 1;} 
  }

	foreach my $v (@versionOrder) {
	  main::progress("Checking $v\n");
		if ($v eq $startVersion) {
			$found = 1;
		}
		if ($found) {
			if (defined ($updateFuncs{$v}) ) {
				if (&{$updateFuncs{$v}}($startBuild, $targetVersion, $targetBuild)) {
					return 1;
				}
			} else {
				main::progress("I don't know how to update $v - exiting\n");
				return 1;
			}
		}
		if ($v eq $targetVersion) {
			last;
		}
	}
	if (isInstalled ("zimbra-ldap")) {
		stopLdap();
	}

	return 0;
}

sub upgradeBM1 {
	main::progress("Updating from 3.0.M1\n");

	my $t = time()+(60*60*24*60);
	my @d = localtime($t);
	my $expiry = sprintf ("%04d%02d%02d",$d[5]+1900,$d[4]+1,$d[3]);
	main::runAsZimbra("zmlocalconfig -e trial_expiration_date=$expiry");

	my $ldh = main::runAsZimbra("zmlocalconfig -m nokey ldap_host");
	chomp $ldh;
	my $ldp = main::runAsZimbra("zmlocalconfig -m nokey ldap_port");
	chomp $ldp;

	main::progress("Updating ldap url configuration\n");
	main::runAsZimbra("zmlocalconfig -e ldap_url=ldap://${ldh}:${ldp}");
	main::runAsZimbra("zmlocalconfig -e ldap_master_url=ldap://${ldh}:${ldp}");

	if ($hn eq $ldh) {
		main::progress("Setting ldap master to true\n");
		main::runAsZimbra("zmlocalconfig -e ldap_is_master=true");
	}

	main::progress("Updating index configuration\n");
	main::runAsZimbra("zmlocalconfig -e zimbra_index_idle_flush_time=600");
	main::runAsZimbra("zmlocalconfig -e zimbra_index_lru_size=100");
	main::runAsZimbra("zmlocalconfig -e zimbra_index_max_uncommitted_operations=200");
	main::runAsZimbra("zmlocalconfig -e logger_mysql_port=7307");

	main::progress("Updating zimbra user configuration\n");
	main::runAsZimbra("zmlocalconfig -e zimbra_user=zimbra");
	my $UID = `id -u zimbra`;
	chomp $UID;
	my $GID = `id -g zimbra`;
	chomp $GID;
	main::runAsZimbra("zmlocalconfig -e zimbra_uid=${UID}");
	main::runAsZimbra("zmlocalconfig -e zimbra_gid=${GID}");
	main::runAsZimbra("zmcreatecert");

	return 0;
}

sub upgradeBM2 {
	main::progress("Updating from 3.0.0_M2\n");

	movePostfixQueue ("2.2.3","2.2.5");

	return 0;
}

sub upgradeBM3 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 3.0.0_M3\n");

	# $startBuild -> $targetBuild
	if ($startVersion eq "3.0.0_M2" || $startVersion eq "3.0.M1" || $startBuild <= 346) {
		# Set mode and authhost
		main::runAsZimbra("$ZMPROV ms $hn zimbraMailMode http");
		main::runAsZimbra("$ZMPROV ms $hn zimbraMtaAuthHost $hn");
	}
	if (($startVersion eq "3.0.0_M2" || $startVersion eq "3.0.M1" || $startBuild <= 427) &&
		isInstalled ("zimbra-ldap")) {

		main::progress ("Updating ldap GAL attributes\n");
		main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapAttrMap zimbraId=zimbraId +zimbraGalLdapAttrMap objectClass=objectClass +zimbraGalLdapAttrMap zimbraMailForwardingAddress=zimbraMailForwardingAddress");

		main::progress ("Updating ldap CLIENT attributes\n");
		main::runAsZimbra("$ZMPROV mcf +zimbraAccountClientAttr zimbraIsDomainAdminAccount +zimbraAccountClientAttr zimbraFeatureIMEnabled");
		main::runAsZimbra("$ZMPROV mcf +zimbraCOSInheritedAttr zimbraFeatureIMEnabled");
		main::progress ("Updating ldap domain admin attributes\n");
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraAccountStatus");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr company");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr cn");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr co");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr displayName");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr gn");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr description");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr initials");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr l");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraAttachmentsBlocked");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraAttachmentsIndexingEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraAttachmentsViewInHtmlOnly");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraAuthTokenLifetime");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraAuthLdapExternalDn");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraAdminAuthTokenLifetime");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraContactMaxNumEntries");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureContactsEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureGalEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureHtmlComposeEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureCalendarEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureIMEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureTaggingEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureAdvancedSearchEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureSavedSearchesEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureConversationsEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureChangePasswordEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureInitialSearchPreferenceEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureFiltersEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraForeignPrincipal");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraImapEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraIsDomainAdminAccount");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraMailIdleSessionTimeout");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraMailMessageLifetime");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraMailMinPollingInterval");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraMailSpamLifetime");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraMailTrashLifetime");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraNotes");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPasswordLocked");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPasswordMinLength");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPasswordMaxLength");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPasswordMinAge");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPasswordMaxAge");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPasswordEnforceHistory");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPasswordMustChange");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPop3Enabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefTimeZoneId");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefUseTimeZoneListInCalendar");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefComposeInNewWindow");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefComposeFormat");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefHtmlEditorDefaultFontColor");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefHtmlEditorDefaultFontFamily");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefHtmlEditorDefaultFontSize");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefForwardReplyInOriginalFormat");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefAutoAddAddressEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefShowFragments");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefShowSearchString");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefCalendarFirstDayOfWeek");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefCalendarInitialView");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefCalendarInitialCheckedCalendars");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefCalendarUseQuickAdd");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefCalendarAlwaysShowMiniCal");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefCalendarNotifyDelegatedChanges");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefContactsInitialView");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefDedupeMessagesSentToSelf");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefForwardIncludeOriginalText");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefForwardReplyPrefixChar");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefGroupMailBy");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefImapSearchFoldersEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefIncludeSpamInSearch");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefIncludeTrashInSearch");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefMailInitialSearch");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefMailItemsPerPage");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefContactsPerPage");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefMessageViewHtmlPreferred");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefMailPollingInterval");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefMailSignature");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefMailSignatureEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefMailSignatureStyle");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefNewMailNotificationAddress");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefNewMailNotificationEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefOutOfOfficeReply");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefOutOfOfficeReplyEnabled");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefReplyIncludeOriginalText");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefReplyToAddress");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefSaveToSent");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefSentMailFolder");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefUseKeyboardShortcuts");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraZimletAvailableZimlets");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraZimletUserProperties");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr o");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr ou");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr physicalDeliveryOfficeName");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr postalAddress");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr postalCode");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr sn");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr st");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr telephoneNumber");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr title");
		print ".";
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraMailStatus");
		print "\n";

		main::progress ("Updating ldap server attributes\n");

		main::runAsZimbra("$ZMPROV mcf zimbraLmtpNumThreads 20 ");
		main::runAsZimbra("$ZMPROV mcf zimbraMessageCacheSize 1671168 ");
		main::runAsZimbra("$ZMPROV mcf +zimbraServerInheritedAttr zimbraMessageCacheSize +zimbraServerInheritedAttr zimbraMtaAuthHost +zimbraServerInheritedAttr zimbraMtaAuthURL +zimbraServerInheritedAttr zimbraMailMode");
		main::runAsZimbra("$ZMPROV mcf -zimbraMtaRestriction reject_non_fqdn_hostname");
	}
	if ($startVersion eq "3.0.0_M2" || $startVersion eq "3.0.M1" || $startBuild <= 436) {
		if (isInstalled("zimbra-store")) {
			if (startSql()) { return 1; }
			main::runAsZimbra("perl -I${scriptDir} ${scriptDir}/fixConversationCounts.pl");
			stopSql();
		}

		if (isInstalled("zimbra-ldap")) {
			main::progress ("Updating ldap domain admin attributes\n");
			main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr givenName");
			main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraMailForwardingAddress");
			main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraNewMailNotificationSubject");
			main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraNewMailNotificationFrom");
			main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraNewMailNotificationBody");
			main::runAsZimbra("$ZMPROV mcf +zimbraServerInheritedAttr zimbraMtaMyNetworks");
		}
	}
	return 0;
}

sub upgradeBM4 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 3.0.0_M4\n");
	if (isInstalled("zimbra-ldap")) {
		main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraMailStatus");
		if ($startVersion eq "3.0.0_M2" || $startVersion eq "3.0.M1" || $startVersion eq "3.0.0_M3" ||
			$startBuild <= 41) {
			main::runAsZimbra("$ZMPROV mcf +zimbraAccountClientAttr zimbraFeatureViewInHtmlEnabled");
			main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureViewInHtmlEnabled");
			main::runAsZimbra("$ZMPROV mcf +zimbraCOSInheritedAttr zimbraFeatureViewInHtmlEnabled");
			main::runAsZimbra("$ZMPROV mc default zimbraFeatureViewInHtmlEnabled FALSE");
		}
	}
	if ($startVersion eq "3.0.0_M4" && $startBuild == 41) {
		if (isInstalled("zimbra-store")) {
			if (startSql()) { return 1; }
			main::runAsZimbra("perl -I${scriptDir} ${scriptDir}/migrate20060120-Appointment.pl");
			stopSql();
		}
	}

	return 0;
}

sub upgradeBGA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 3.0.0_GA\n");
	return 0;

	if ( -d "/opt/zimbra/clamav-0.87.1/db" && -d "/opt/zimbra/clamav-0.88" &&
		! -d "/opt/zimbra/clamav-0.88/db" )  {
			`cp -fR /opt/zimbra/clamav-0.87.1/db /opt/zimbra/clamav-0.88`;
	}

	movePostfixQueue ("2.2.5","2.2.8");


}

sub upgrade301GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 3.0.1_GA\n");

	unless(open (G, "$ZMPROV gcf zimbraGalLdapFilterDef |")) {
    Migrate::myquit(1,"Can't open zmprov: $!");
  }
	`$ZMPROV mcf zimbraGalLdapFilterDef ''`;
	while (<G>) {
		chomp;
		s/\(zimbraMailAddress=\*%s\*\)//;
		s/zimbraGalLdapFilterDef: //;
		`$ZMPROV mcf +zimbraGalLdapFilterDef \'$_\'`;
	}

	# This change was made in both main and CRAY
	# CRAY build 202
	# MAIN build 223
	if ( ($startVersion eq "3.0.0_GA" && $startBuild <= 202) ||
		($startVersion eq "3.0.0_M2" || $startVersion eq "3.0.M1" || 
		$startVersion eq "3.0.0_M3" || $startVersion eq "3.0.0_M4")
		) {
		main::runAsZimbra("zmlocalconfig -e postfix_version=2.2.9");
		movePostfixQueue ("2.2.8","2.2.9");

	}
	main::runAsZimbra("$ZMPROV mcf +zimbraCOSInheritedAttr zimbraFeatureSharingEnabled");
	main::runAsZimbra("$ZMPROV mcf +zimbraDomainInheritedAttr zimbraFeatureSharingEnabled");
	main::runAsZimbra("$ZMPROV mc default zimbraFeatureSharingEnabled TRUE");

	return 0;
}

sub upgrade310GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 3.1.0_GA\n");
	main::runAsZimbra("$ZMPROV mcf +zimbraCOSInheritedAttr zimbraFeatureSharingEnabled");
	main::runAsZimbra("$ZMPROV mcf +zimbraDomainInheritedAttr zimbraFeatureSharingEnabled");
	main::runAsZimbra("$ZMPROV mcf +zimbraAccountClientAttr zimbraFeatureSharingEnabled");
	main::runAsZimbra("$ZMPROV mc default zimbraFeatureSharingEnabled TRUE");

	main::runAsZimbra("$ZMPROV mcf -zimbraGalLdapFilterDef 'zimbra:(&(|(cn=*%s*)(sn=*%s*)(gn=*%s*)(mail=*%s*)(zimbraMailDeliveryAddress=*%s*)(zimbraMailAlias=*%s*))(|(objectclass=zimbraAccount)(objectclass=zimbraDistributionList)))'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapFilterDef 'zimbraAccounts:(&(|(cn=*%s*)(sn=*%s*)(gn=*%s*)(mail=*%s*)(zimbraMailDeliveryAddress=*%s*)(zimbraMailAlias=*%s*)(zimbraMailAddress=*%s*))(|(objectclass=zimbraAccount)(objectclass=zimbraDistributionList))(!(objectclass=zimbraCalendarResource)))'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapFilterDef 'zimbraResources:(&(|(cn=*%s*)(sn=*%s*)(gn=*%s*)(mail=*%s*)(zimbraMailDeliveryAddress=*%s*)(zimbraMailAlias=*%s*)(zimbraMailAddress=*%s*))(objectclass=zimbraCalendarResource))'");

	# Bug 6077
	main::runAsZimbra("$ZMPROV mcf -zimbraGalLdapAttrMap 'givenName=firstName'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapAttrMap 'gn=firstName'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapAttrMap 'description=notes'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapAttrMap 'zimbraCalResType=zimbraCalResType'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapAttrMap 'zimbraCalResLocationDisplayName=zimbraCalResLocationDisplayName'");

	# bug: 2799
	main::runAsZimbra("$ZMPROV mcf +zimbraCOSInheritedAttr zimbraPrefCalendarApptReminderWarningTime");
	main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefCalendarApptReminderWarningTime");
	main::runAsZimbra("$ZMPROV mc default zimbraPrefCalendarApptReminderWarningTime 5");

	main::runAsZimbra("$ZMPROV mcf +zimbraAccountClientAttr zimbraFeatureMailForwardingEnabled");
	main::runAsZimbra("$ZMPROV mcf +zimbraCOSInheritedAttr zimbraFeatureMailForwardingEnabled");
	main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraFeatureMailForwardingEnabled");
	main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefMailForwardingAddress");
	main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefMailLocalDeliveryDisabled");
	main::runAsZimbra("$ZMPROV mc default zimbraFeatureMailForwardingEnabled TRUE");

	# bug 6077
	main::runAsZimbra("$ZMPROV mcf +zimbraAccountClientAttr zimbraLocale");
	main::runAsZimbra("$ZMPROV mcf +zimbraCOSInheritedAttr zimbraLocale");
	main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraLocale");
	main::runAsZimbra("$ZMPROV mcf +zimbraServerInheritedAttr zimbraLocale");

	# bug 6834
	main::runAsZimbra("$ZMPROV mcf +zimbraServerInheritedAttr zimbraRemoteManagementCommand");
	main::runAsZimbra("$ZMPROV mcf +zimbraServerInheritedAttr zimbraRemoteManagementUser");
	main::runAsZimbra("$ZMPROV mcf +zimbraServerInheritedAttr zimbraRemoteManagementPrivateKeyPath");
	main::runAsZimbra("$ZMPROV mcf +zimbraServerInheritedAttr zimbraRemoteManagementPort");
	main::runAsZimbra("$ZMPROV ms $hn zimbraRemoteManagementCommand /opt/zimbra/libexec/zmrcd");
	main::runAsZimbra("$ZMPROV ms $hn zimbraRemoteManagementUser zimbra");
	main::runAsZimbra("$ZMPROV ms $hn zimbraRemoteManagementPrivateKeyPath /opt/zimbra/.ssh/zimbra_identity");
	main::runAsZimbra("$ZMPROV ms $hn zimbraRemoteManagementPort 22");

	# bug: 6828
	main::runAsZimbra("$ZMPROV mcf -zimbraGalLdapAttrMap zimbraMailAlias=email2");

	if ( ($startVersion eq "3.1.0_GA" && $startBuild <= 303) ||
		($startVersion eq "3.0.0_GA" || $startVersion eq "3.0.0_M2" || $startVersion eq "3.0.M1" || 
		$startVersion eq "3.0.0_M3" || $startVersion eq "3.0.0_M4")
		) {
		if (-f "/opt/zimbra/redolog/redo.log") {
			`mv /opt/zimbra/redolog/redo.log /opt/zimbra/redolog/redo.log.preupgrade`;
		}
		if (-d "/opt/zimbra/redolog/archive") {
			`mv /opt/zimbra/redolog/archive /opt/zimbra/redolog/archive.preupgrade`;
		}
	}

	# bug 7241
	main::runAsZimbra("/opt/zimbra/bin/zmsshkeygen");

	return 0;
}

sub upgrade311GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 3.1.1_GA\n");

	return 0;
}

sub upgrade312GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 3.1.2_GA\n");
	return 0;
}

sub upgrade313GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 3.1.3_GA\n");

  # removing this per bug 10901
	#my @accounts = `su - zimbra -c "$ZMPROV gaa"`;
	#open (G, "| $ZMPROV ") or die "Can't open zmprov: $!";
	#foreach (@accounts) {
	# chomp;
	# print G "ma $_ zimbraPrefMailLocalDeliveryDisabled FALSE\n";
	#}
	#close G;

	return 0;
}

sub upgrade314GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 3.1.4_GA\n");
	if (isInstalled ("zimbra-ldap")) {
  my $a = <<EOF;
# parse text/plain internally
dn: cn=text/plain,cn=mime,cn=config,cn=zimbra
changetype: add
zimbraMimeType: text/plain
cn: text/plain
objectClass: zimbraMimeEntry
zimbraMimeIndexingEnabled: TRUE
zimbraMimeHandlerClass: TextPlainHandler
zimbraMimeFileExtension: text
zimbraMimeFileExtension: txt
description: Plain Text Document
EOF

  open L, ">/tmp/text-plain.ldif";
  print L $a;
  close L;
  my $ldap_pass = `su - zimbra -c "zmlocalconfig -s -m nokey ldap_root_password"`;
  my $ldap_url = `su - zimbra -c "zmlocalconfig -s -m nokey ldap_url"`;
  chomp $ldap_pass;
  chomp $ldap_url;
  main::runAsZimbra("ldapmodify -c -H $ldap_url -D uid=zimbra,cn=admins,cn=zimbra -x -w $ldap_pass -f /tmp/text-plain.ldif");
	}
	return 0;
}

sub upgrade32M1 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 3.2.0_M1\n");

	main::runAsZimbra("$ZMPROV mcf +zimbraCOSInheritedAttr zimbraFeatureSharingEnabled");
	main::runAsZimbra("$ZMPROV mcf +zimbraDomainInheritedAttr zimbraFeatureSharingEnabled");
	main::runAsZimbra("$ZMPROV mc default zimbraFeatureSharingEnabled TRUE");

	main::runAsZimbra("$ZMPROV mcf zimbraGalLdapFilterDef 'zimbraAccounts:(&(|(cn=*%s*)(sn=*%s*)(gn=*%s*)(mail=*%s*)(zimbraMailDeliveryAddress=*%s*)(zimbraMailAlias=*%s*)(zimbraMailAddress=*%s*))(|(objectclass=zimbraAccount)(objectclass=zimbraDistributionList))(!(objectclass=zimbraCalendarResource)))'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapFilterDef 'zimbraResources:(&(|(cn=*%s*)(sn=*%s*)(gn=*%s*)(mail=*%s*)(zimbraMailDeliveryAddress=*%s*)(zimbraMailAlias=*%s*)(zimbraMailAddress=*%s*))(objectclass=zimbraCalendarResource))'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapFilterDef 'ad:(&(|(cn=*%s*)(sn=*%s*)(gn=*%s*)(mail=*%s*))(!(msExchHideFromAddressLists=TRUE))(mailnickname=*)(|(&(objectCategory=person)(objectClass=user)(!(homeMDB=*))(!(msExchHomeServerName=*)))(&(objectCategory=person)(objectClass=user)(|(homeMDB=*)(msExchHomeServerName=*)))(&(objectCategory=person)(objectClass=contact))(objectCategory=group)(objectCategory=publicFolder)(objectCategory=msExchDynamicDistributionList)))'");

	# Bug 6077
	main::runAsZimbra("$ZMPROV mcf -zimbraGalLdapAttrMap 'givenName=firstName'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapAttrMap 'gn=firstName'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapAttrMap 'description=notes'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapAttrMap 'zimbraCalResType=zimbraCalResType'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapAttrMap 'zimbraCalResLocationDisplayName=zimbraCalResLocationDisplayName'");

	# bug: 2799
	main::runAsZimbra("$ZMPROV mcf +zimbraCOSInheritedAttr zimbraPrefCalendarApptReminderWarningTime");
	main::runAsZimbra("$ZMPROV mcf +zimbraDomainAdminModifiableAttr zimbraPrefCalendarApptReminderWarningTime");
	main::runAsZimbra("$ZMPROV mc default zimbraPrefCalendarApptReminderWarningTime 5");

	# Bug 7590
	my @coses = `su - zimbra -c "$ZMPROV gac"`;
	foreach my $cos (@coses) {
		chomp $cos;
		main::runAsZimbra("$ZMPROV mc $cos zimbraFeatureSkinChangeEnabled TRUE zimbraPrefSkin steel zimbraFeatureNotebookEnabled TRUE");
	}

	# Bug 7590
	# The existing one whose default we flipped, someone else who cares about it
	# should yes/no the flip.  The attribute is zimbraPrefAutoAddAddressEnabled which
	# used to be FALSE by default and as of Edison we are going TRUE by default for
	# all new installs.

	# bug 7588

	main::runAsZimbra("$ZMPROV mcf -zimbraGalLdapAttrMap gn=firstName");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapAttrMap givenName,gn=firstName ");

	# Bug 5466
  my $acct;
	$acct = (split(/\s+/, `su - zimbra -c "$ZMPROV gcf zimbraSpamIsSpamAccount"`))[-1];
  main::runAsZimbra("$ZMPROV ma $acct zimbraHideInGal TRUE")
	  if ($acct ne "");

	$acct = (split(/\s+/, `su - zimbra -c "$ZMPROV gcf zimbraSpamIsNotSpamAccount"`))[-1];
  main::runAsZimbra("$ZMPROV ma $acct zimbraHideInGal TRUE")
  	if ($acct ne "");

	# Bug 7723
	main::runAsZimbra("$ZMPROV mcf -zimbraGalLdapAttrMap zimbraMailDeliveryAddress,mail=email");

	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapAttrMap zimbraMailDeliveryAddress,zimbraMailAlias,mail=email,email2,email3,email4,email5,email6");


	if ( -d "/opt/zimbra/amavisd-new-2.3.3/db" && -d "/opt/zimbra/amavisd-new-2.4.1" && ! -d "/opt/zimbra/amavisd-new-2.4.1/db" ) {
		`mv /opt/zimbra/amavisd-new-2.3.3/db /opt/zimbra/amavisd-new-2.4.1/db`;
		`chown -R zimbra:zimbra /opt/zimbra/amavisd-new-2.4.1/db`;
	}
	if ( -d "/opt/zimbra/amavisd-new-2.3.3/.spamassassin" && -d "/opt/zimbra/amavisd-new-2.4.1" && ! -d "/opt/zimbra/amavisd-new-2.4.1/.spamassassin" ) {
		`mv /opt/zimbra/amavisd-new-2.3.3/.spamassassin /opt/zimbra/amavisd-new-2.4.1/.spamassassin`;
		`chown -R zimbra:zimbra /opt/zimbra/amavisd-new-2.4.1/.spamassassin`;
	}


	return 0;
}

sub upgrade32M2 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 3.2.0_M2\n");

  # bug 8121
  updateMySQLcnf();

	# Bug 9096
	my $acct = `su - zimbra -c "$ZMPROV gcf zimbraSpamIsSpamAccount"`;
	chomp $acct;
	$acct =~ s/.* //;
	if ($acct ne "") {
		main::runAsZimbra("$ZMPROV ma $acct zimbraIsSystemResource TRUE");
	}
	$acct = `su - zimbra -c "$ZMPROV gcf zimbraSpamIsNotSpamAccount"`;
	chomp $acct;
	$acct =~ s/.* //;
	if ($acct ne "") {
		main::runAsZimbra("$ZMPROV ma $acct zimbraIsSystemResource TRUE");
	}

  # Bug 7850
	my @coses = `su - zimbra -c "$ZMPROV gac"`;
	foreach my $cos (@coses) {
		chomp $cos;
		main::runAsZimbra("$ZMPROV mc $cos zimbraFeatureNewMailNotificationEnabled TRUE zimbraFeatureOutOfOfficeReplyEnabled TRUE");
	}

	return 0;
}

sub upgrade400RC1 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.0.0_RC1\n");

  # Bug 9504
  if (-d "/opt/zimbra/redolog" && ! -e "/opt/zimbra/redolog-pre-4.0") {
	  `mv /opt/zimbra/redolog /opt/zimbra/redolog-pre-4.0`;
    `mkdir /opt/zimbra/redolog`;
    `chown zimbra:zimbra /opt/zimbra/redolog`;
  }

  if (-e "/opt/zimbra/backup" && ! -e "/opt/zimbra/backup-pre-4.0") {
	  `mv /opt/zimbra/backup /opt/zimbra/backup-pre-4.0`;
    `mkdir /opt/zimbra/backup`;
    `chown zimbra:zimbra /opt/zimbra/backup`;
  }

  # Bug 9419
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapFilterDef 'adAutoComplete:(&(|(cn=%s*)(sn=%s*)(gn=%s*)(mail=%s*))(!(msExchHideFromAddressLists=TRUE))(mailnickname=*)(|(&(objectCategory=person)(objectClass=user)(!(homeMDB=*))(!(msExchHomeServerName=*)))(&(objectCategory=person)(objectClass=user)(|(homeMDB=*)(msExchHomeServerName=*)))(&(objectCategory=person)(objectClass=contact))(objectCategory=group)(objectCategory=publicFolder)(objectCategory=msExchDynamicDistributionList)))'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapFilterDef 'externalLdapAutoComplete:(|(cn=%s*)(sn=%s*)(gn=%s*)(mail=%s*)'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapFilterDef 'zimbraAccountAutoComplete:(&(|(cn=%s*)(sn=%s*)(gn=%s*)(mail=%s*)(zimbraMailDeliveryAddress=%s*)(zimbraMailAlias=%s*))(|(objectclass=zimbraAccount)(objectclass=zimbraDistributionList))(!(objectclass=zimbraCalendarResource)))'");
	main::runAsZimbra("$ZMPROV mcf +zimbraGalLdapFilterDef 'zimbraResourceAutoComplete:(&(|(cn=%s*)(sn=%s*)(gn=%s*)(mail=%s*)(zimbraMailDeliveryAddress=%s*)(zimbraMailAlias=%s*))(objectclass=zimbraCalendarResource))'");

  # Bug 9693
	if ($startVersion eq "3.2.0_M1" || $startVersion eq "3.2.0_M2") {
		if (isInstalled("zimbra-store")) {
			if (startSql()) { return 1; }
			main::runAsZimbra("sh ${scriptDir}/migrate20060807-WikiDigestFixup.sh");
			stopSql();
		}
	}
  
	return 0;
}

sub upgrade400GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.0.0_GA\n");
	return 0;
}

sub upgrade401GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.0.1_GA\n");

  # bug 10346
  my $globalWikiAcct = main::getLdapConfigValue("zimbraNotebookAccount");
  next unless $globalWikiAcct;
  main::runAsZimbra("/opt/zimbra/bin/zmprov ma $globalWikiAcct zimbraFeatureNotebookEnabled TRUE");
  
  # bug 10388
  clearTomcatWorkDir();

	return 0;
}

sub upgrade402GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.0.2_GA\n");

  if (isInstalled("zimbra-ldap")) {
    # bug 10401
	  my @coses = `su - zimbra -c "$ZMPROV gac"`;
	  foreach my $cos (@coses) {
		  chomp $cos;
      my $cur_value = 
        main::getLdapCOSValue($cos,"zimbraFeatureMobileSyncEnabled");

      main::runAsZimbra("$ZMPROV mc $cos zimbraFeatureMobileSyncEnabled FALSE")
        if ($cur_value ne "TRUE");
	  }

    # bug 10845
    main::runAsZimbra("$ZMPROV mcf zimbraMailURL /zimbra"); 
  }

  return 0;
}

sub upgrade403GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.0.3_GA\n");

  #8081 remove amavis tmpfs
  if ( -f "/etc/fstab" ) {
    my $mount = (split(/\s+/, `egrep -e '^/dev/shm.*amavisd.*tmpfs' /etc/fstab`))[1];
    if ($mount ne "" ) {
      `umount $mount > /dev/null 2>&1`;
      `sed -i.zimbra -e 's:\\(^/dev/shm.*amavis.*\\):#\\1:' /etc/fstab`;
      if ($? != 0) {
        `mv /etc/fstab.zimbra /etc/fstab`;
      }
    }
  }

  if (isInstalled("zimbra-ldap")) {
    # bug 11315
    my $remoteManagementUser = 
      main::getLdapConfigValue("zimbraRemoteManagementUser");
    main::runAsZimbra("$ZMPROV mcf zimbraRemoteManagementUser zimbra") 
      if ($remoteManagementUser eq "");

    my $remoteManagementPort = 
      main::getLdapConfigValue("zimbraRemoteManagementPort");
    main::runAsZimbra("$ZMPROV mcf zimbraRemoteManagementPort 22") 
     if ($remoteManagementPort eq "");

    my $remoteManagementPrivateKeyPath = 
      main::getLdapConfigValue("zimbraRemoteManagementPrivateKeyPath");
    main::runAsZimbra("$ZMPROV mcf zimbraRemoteManagementPrivateKeyPath /opt/zimbra/.ssh/zimbra_identity") 
      if ($remoteManagementPrivateKeyPath eq "");

    my $remoteManagementCommand = 
      main::getLdapConfigValue("zimbraRemoteManagementCommand");
    main::runAsZimbra("$ZMPROV mcf zimbraRemoteManagementCommand /opt/zimbra/libexec/zmrcd") 
      if ($remoteManagementCommand eq "");
  }

  return 0;
}

sub upgrade404GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.0.4_GA\n");
	return 0;
}

sub upgrade405GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.0.5_GA\n");
	return 0;
}

sub upgrade410BETA1 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.1.0_BETA1\n");

  # bug 9622
  clearRedologDir("/opt/zimbra/redolog", $targetVersion);
  clearBackupDir("/opt/zimbra/backup", $targetVersion);

  # migrate amavis data 
  migrateAmavisDB("2.4.3");

	return 0;
}

sub upgrade450BETA1 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.5.0_BETA1\n");
  if (isInstalled("zimbra-ldap")) {
    main::runAsZimbra("$ZMPROV mc default zimbraPrefUseKeyboardShortcuts TRUE");
  }
	return 0;
}

sub upgrade450BETA2 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.5.0_BETA2\n");
	return 0;
}

sub upgrade450RC1 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.5.0_RC1\n");
  if (isInstalled("zimbra-ldap")) {
    # bug 12031
	  my @coses = `su - zimbra -c "$ZMPROV gac"`;
	  foreach my $cos (@coses) {
		  chomp $cos;
		  main::runAsZimbra("$ZMPROV mc $cos zimbraFeaturePop3DataSourceEnabled TRUE zimbraPrefReadingPaneEnabled TRUE zimbraPrefUseRfc2231 FALSE zimbraFeatureIdentitiesEnabled TRUE zimbraPasswordLockoutDuration 1h zimbraPasswordLockoutEnabled FALSE zimbraPasswordLockoutFailureLifetime 1h zimbraPasswordLockoutMaxFailures 10");
	  }

    # bah-bye timezones
    # replaced by /opt/zimbra/conf/timezones.ics
    my $ldap_pass = `su - zimbra -c "zmlocalconfig -s -m nokey ldap_root_password"`;
    my $ldap_master_url = `su - zimbra -c "zmlocalconfig -s -m nokey ldap_master_url"`;
    my $ldap; 
    chomp($ldap_master_url);
    chomp($ldap_pass);
    unless($ldap = Net::LDAP->new($ldap_master_url)) { 
      main::progress("Unable to contact $ldap_master_url: $!\n"); 
      return 1;
    }
    my $dn = 'cn=timezones,cn=config,cn=zimbra';
    my $result = $ldap->bind("uid=zimbra,cn=admins,cn=zimbra", password => $ldap_pass);
    unless($result->code()) {
      $result = DeleteLdapTree($ldap,$dn);
      main::progress($result->code() ? "Failed to delete $dn: ".$result->error() : "Deleted $dn\n");
    }
    $result = $ldap->unbind;
  }
	return 0;
}

sub upgrade450RC2 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.5.0_RC2\n");
  if (isInstalled("zimbra-ldap")) {
    main::runAsZimbra("$ZMPROV mcf zimbraSmtpSendAddOriginatingIP TRUE");
  }

  if (isInstalled("zimbra-logger")) {
	  main::setLocalConfig("stats_img_folder", "/opt/zimbra/logger/db/work");
  }
    
	return 0;
}

sub upgrade450GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.5.0_GA\n");
	return 0;
}
sub upgrade451GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.5.1_GA\n");
  if (isInstalled("zimbra-store")) {
    my $tomcat_java_options = main::getLocalConfig("tomcat_java_options");
    $tomcat_java_options .= " -Djava.awt.headless=true"
      unless ($tomcat_java_options =~ /java\.awt\.headless/);
    main::detail("Modified tomcat_java_options=$tomcat_java_options");
    main::setLocalConfig("tomcat_java_options", "$tomcat_java_options");
  }
	return 0;
}
sub upgrade452GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.5.2_GA\n");
	return 0;
}
sub upgrade453GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.5.3_GA\n");
  if (isInstalled("zimbra-store")) {
    # bug 14160
    my ($maxMessageSize, $zimbraMessageCacheSize, $systemMemorySize, $newcache);
    my $tomcatHeapPercent = main::getLocalConfig("tomcat_java_heap_memory_percent");
    $tomcatHeapPercent = 40 if ($tomcatHeapPercent eq "");
    $maxMessageSize = main::getLdapConfigValue("zimbraMtaMaxMessageSize");
    $zimbraMessageCacheSize = main::getLdapConfigValue("zimbraMessageCacheSize");
    $systemMemorySize = main::getSystemMemory();

    my $tomcatHeapSize = ($systemMemorySize*($tomcatHeapPercent/100));
    $newcache = int($tomcatHeapSize*.05*1024*1024*1024);
   
    main::runAsZimbra("$ZMPROV mcf zimbraMessageCacheSize $newcache")
      if ($newcache > $zimbraMessageCacheSize);
    
  }
	return 0;
}
sub upgrade454GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.5.4_GA\n");
  if (isInstalled("zimbra-ldap")) {
    main::setLocalConfig("ldap_log_level", "32768");
  }
	return 0;
}
sub upgrade455GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.5.5_GA\n");
	return 0;
}
sub upgrade456GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 4.5.6_GA\n");
  # bug 16425 rewrite default perms on localconfig.xml
  main::setLocalConfig("upgrade_dummy", "1");
  main::deleteLocalConfig("upgrade_dummy");

  # bug 17879
  if (isInstalled("zimbra-store")) {
    updateMySQLcnf();
  }

	return 0;
}
sub upgrade500BETA1 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 5.0.0_BETA1\n");
	return 0;
}

sub upgrade500BETA2 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 5.0.0_BETA2\n");

  # bug 16425 rewrite default perms on localconfig.xml
  main::setLocalConfig("upgrade_dummy", "1");
  main::deleteLocalConfig("upgrade_dummy");
	return 0;
}
sub upgrade500RC1 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 5.0.0_RC1\n");
	return 0;
}
sub upgrade500RC2 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 5.0.0_RC2\n");
	return 0;
}
sub upgrade500GA {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 5.0.0_GA\n");
	return 0;
}

sub upgrade35M1 {
	my ($startBuild, $targetVersion, $targetBuild) = (@_);
	main::progress("Updating from 3.5.0_M1\n");
	return 0;
}

sub stopZimbra {
	main::progress("Stopping zimbra services\n");
	my $rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/zmcontrol stop > /dev/null 2>&1\"");
	$rc = $rc >> 8;
	if ($rc) {
		main::progress("Stop failed - exiting\n");
		return $rc;
	}
	return 0;
}

sub startLdap {
	main::progress("Checking ldap status\n");
	my $rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/ldap status > /dev/null 2>&1\"");
	$rc = $rc >> 8;
	if ($rc) {
		main::progress("Starting ldap\n");
		$rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/libexec/zmldapapplyldif > /dev/null 2>&1\"");
		$rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/ldap status > /dev/null 2>&1\"");
		$rc = $rc >> 8;
		if ($rc) {
			$rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/ldap start > /dev/null 2>&1\"");
			$rc = $rc >> 8;
			if ($rc) {
				main::progress("ldap startup failed with exit code $rc\n");
			  system("su - zimbra -c \"/opt/zimbra/bin/ldap start 2>&1 | grep failed\"");
				return $rc;
			}
		}
	}
	return 0;
}

sub stopLdap {
	main::progress("Stopping ldap\n");
	my $rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/ldap stop > /dev/null 2>&1\"");
	$rc = $rc >> 8;
	if ($rc) {
		main::progress("LDAP stop failed with exit code $rc\n");
		return $rc;
	}
  sleep 5; # give it a chance to shutdown.
	return 0;
}

sub isSqlRunning {
	my $rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/mysqladmin status > /dev/null 2>&1\"");
	$rc = $rc >> 8;
  return($rc ? undef : 1);
}

sub startSql {

	unless (isSqlRunning()) {
		main::progress("Starting mysql\n");
		my $rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/mysql.server start > /dev/null 2>&1\"");
    my $timeout = sleep 10;
    while (!isSqlRunning() && $timeout <= 1200 ) {
		  $rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/mysql.server start > /dev/null 2>&1\"");
      $timeout += sleep 10;
    }
		$rc = $rc >> 8;
		if ($rc) {
			main::progress("mysql startup failed with exit code $rc\n");
			return $rc;
		}
	}
	return(isSqlRunning() ? 0 : 1);
}

sub stopSql {

  if (isSqlRunning()) {
	  main::progress("Stopping mysql\n");
	  my $rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/mysql.server stop > /dev/null 2>&1\"");
    my $timeout = sleep 10;
    while (isSqlRunning() && $timeout <= 120 ) {
		  $rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/mysql.server stop > /dev/null 2>&1\"");
      $timeout += sleep 10;
    }
	  $rc = $rc >> 8;
	  if ($rc) {
		  main::progress("mysql stop failed with exit code $rc\n");
		  return $rc;
	  }
  }
  return(isSqlRunning() ? 1 : 0);
}

sub isLoggerSqlRunning {
	my $rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/logmysqladmin status > /dev/null 2>&1\"");
	$rc = $rc >> 8;
  return($rc ? undef : 1);
}

sub startLoggerSql {
	unless (isLoggerSqlRunning()) {
		main::progress("Starting logger mysql\n");
		my $rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/logmysql.server start > /dev/null 2>&1\"");
		$rc = $rc >> 8;
		if ($rc) {
			main::progress("logger mysql startup failed with exit code $rc\n");
			return $rc;
		}
    my $timeout = sleep 10;
    while (!isLoggerSqlRunning() && $timeout <= 120 ) {
		  system("su - zimbra -c \"/opt/zimbra/bin/logmysql.server start > /dev/null 2>&1\"");
      $timeout += sleep 10;
    }
	}
	return(isLoggerSqlRunning() ? 0 : 1);
}

sub stopLoggerSql {
  if (isLoggerSqlRunning()) {
	  main::progress("Stopping logger mysql\n");
	  my $rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/logmysql.server stop > /dev/null 2>&1\"");
	  $rc = $rc >> 8;
	  if ($rc) {
		  main::progress("logger mysql stop failed with exit code $rc\n");
		  return $rc;
	  }
    my $timeout = sleep 10;
    while (isLoggerSqlRunning() && $timeout <= 120 ) {
		  $rc = 0xffff & system("su - zimbra -c \"/opt/zimbra/bin/logmysql.server stop > /dev/null 2>&1\"");
      $timeout += sleep 10;
    }
  }
  return(isLoggerSqlRunning() ? 1 : 0);
}


sub runSchemaUpgrade {
	my $curVersion = shift;

	if (! defined ($updateScripts{$curVersion})) {
		main::progress ("Can't upgrade from version $curVersion - no script!\n");
		return 1;
	}

	if (! -x "${scriptDir}/$updateScripts{$curVersion}" ) {
		main::progress ("Can't run ${scriptDir}/$updateScripts{$curVersion} - not executable!\n");
		return 1;
	}

	main::progress ("Running ${scriptDir}/$updateScripts{$curVersion}\n");
  open(MIG, "su - zimbra -c \"perl -I${scriptDir} ${scriptDir}/$updateScripts{$curVersion}\" 2>&1|");
  while (<MIG>) {
    main::progress($_);
  }
  close(MIG);
  my $rc = $?;
	if ($rc != 0) {
		main::progress ("Script failed with code $rc: $! - exiting\n");
		return $rc;
	}
	return 0;
}

sub runLoggerSchemaUpgrade {
	my $curVersion = shift;

	if (! defined ($loggerUpdateScripts{$curVersion})) {
		main::progress ("Can't upgrade from version $curVersion - no script!\n");
		return 1;
	}

	if (! -x "${scriptDir}/$loggerUpdateScripts{$curVersion}" ) {
		main::progress ("Can't run ${scriptDir}/$loggerUpdateScripts{$curVersion} - no script!\n");
		return 1;
	}

	main::progress ("Running ${scriptDir}/$loggerUpdateScripts{$curVersion}\n");
	my $rc = 0xffff & system("su - zimbra -c \"perl -I${scriptDir} ${scriptDir}/$loggerUpdateScripts{$curVersion}\"");
	$rc = $rc >> 8;
	if ($rc) {
		main::progress ("Script failed with code $rc - exiting\n");
		return $rc;
	}
	return 0;
}

sub getInstalledPackages {

	foreach my $p (@packageList) {
		if (isInstalled($p)) {
			$installedPackages{$p} = $p;
		}
	}

}

sub isInstalled {
	my $pkg = shift;

	my $pkgQuery;

	my $good = 1;
	if ($platform eq "DEBIAN3.1" || $platform eq "UBUNTU6") {
		$pkgQuery = "dpkg -s $pkg | egrep '^Status: ' | grep 'not-installed'";
	} elsif ($platform =~ /MACOSX/) {
		my @l = sort glob ("/Library/Receipts/${pkg}*");
		if ( $#l < 0 ) { return 0; }
		$pkgQuery = "test -d $l[$#l]";
		$good = 0;
 } elsif ($platform =~ /RPL/) {
    $pkgQuery = "conary q $pkg";
    $good = 0;
	} else {
		$pkgQuery = "rpm -q $pkg";
		$good = 0;
	}

	my $rc = 0xffff & system ("$pkgQuery > /dev/null 2>&1");
	$rc >>= 8;
	return ($rc == $good);

}

sub movePostfixQueue {

	my $fromVersion = shift;
	my $toVersion = shift;

	if ( -d "/opt/zimbra/postfix-$fromVersion/spool" ) {
		main::progress("Moving postfix queues\n");
		my @dirs = qw /active bounce corrupt defer deferred flush hold incoming maildrop/;
		`mkdir -p /opt/zimbra/postfix-$toVersion/spool`;
		foreach my $d (@dirs) {
			if (-d "/opt/zimbra/postfix-$fromVersion/spool/$d/") {
				main::progress("Moving $d\n");
				`cp -Rf /opt/zimbra/postfix-$fromVersion/spool/$d/* /opt/zimbra/postfix-$toVersion/spool/$d`;
				`chown -R postfix:postdrop /opt/zimbra/postfix-$toVersion/spool/$d`;
			}
		}
	}

	`/opt/zimbra/bin/zmfixperms.sh`;
}

sub updateMySQLcnf {

  my $mycnf = "/opt/zimbra/conf/my.cnf";
  my $mysql_pidfile = main::getLocalConfig("mysql_pidfile");
  $mysql_pidfile = "/opt/zimbra/db/mysql.pid" if ($mysql_pidfile eq "");
  if (-e "$mycnf") {
    unless (open(MYCNF, "$mycnf")) {
      Migrate::myquit(1, "${mycnf}: $!\n");
    }
    my @CNF = <MYCNF>;
    close(MYCNF);
    my $i=0;
    my $mycnfChanged = 0;
    my $tmpfile = "/tmp/my.cnf.$$";;
    my $zimbra_user = `zmlocalconfig -m nokey zimbra_user 2> /dev/null` || "zmbra";;
    open(TMP, ">$tmpfile");
    foreach (@CNF) {
      if (/^port/ && $CNF[$i+1] !~ m/^user/) {
        print TMP;
        print TMP "user         = $zimbra_user\n";
        $mycnfChanged=1;
        next;
      } elsif (/^err-log/ && $CNF[$i+1] !~ m/^pid-file/) {
        print TMP;
        print TMP "pid-file = ${mysql_pidfile}\n";
        $mycnfChanged=1;
        next;
      }
      print TMP;
      $i++;
    }
    close(TMP);
  
    if ($mycnfChanged) {
      `mv $mycnf ${mycnf}.${startVersion}`;
      `cp -f $tmpfile $mycnf`;
      `chmod 644 $mycnf`;
    } 
  }
}

sub clearTomcatWorkDir {

  my $workDir = "/opt/zimbra/tomcat/work";
  return unless (-d "$workDir");
  system("find $workDir -type f -exec rm -f {} \\\;");

}

sub clearRedologDir($$) {
  my ($redologDir, $version) = @_;
  if (-d "$redologDir" && ! -e "${redologDir}/${version}") {
    `mkdir ${redologDir}/${version}`;
	  `mv ${redologDir}/* ${redologDir}/${version}/ > /dev/null 2>&1`;
    `chown zimbra:zimbra $redologDir > /dev/null 2>&1`;
  }
  return;
}

sub clearBackupDir($$) {
  my ($backupDir, $version) = @_;
  if (-e "$backupDir" && ! -e "${backupDir}/${version}") {
    `mkdir ${backupDir}/${version}`;
	  `mv ${backupDir}/* ${backupDir}/${version} > /dev/null 2>&1`;
    `chown zimbra:zimbra $backupDir > /dev/null 2>&1`;
  }
  return;
}

sub doMysqlTableCheck {

  my $updateSQL = "/opt/zimbra/mysql/share/mysql/mysql_fix_privilege_tables.sql";
  if (-e "$updateSQL") {
    main::progress("Verifying mysql tables\n");
    my $db_pass = main::getLocalConfig("mysql_root_password");
    my $mysql = "/opt/zimbra/bin/mysql";
    my $cmd = "$mysql --force --user=root --password=$db_pass --database=mysql --batch < $updateSQL";
    main::progress("Executing $cmd\n");
    main::runAsZimbra("$cmd > /tmp/mysql_fix_perms.out 2>&1");
  }
}

sub doBackupRestoreVersionUpdate($) {
  my ($startVersion) = @_;

  my ($prevRedologVersion,$currentRedologVersion,$prevBackupVersion,$currentBackupVersion);
  $prevRedologVersion = &Migrate::getRedologVersion;
  $currentRedologVersion = `su - zimbra -c "zmjava com.zimbra.cs.redolog.util.GetVersion"`;
  chomp($currentRedologVersion);

  return unless ($currentRedologVersion);

  main::progress("Redolog Version: $prevRedologVersion New Redolog Version: $currentRedologVersion\n");
  Migrate::insertRedologVersion($currentRedologVersion)
    if ($prevRedologVersion eq "");
  Migrate::updateRedologVersion($prevRedologVersion,$currentRedologVersion)
    if ($prevRedologVersion != $currentRedologVersion);

  if (-f "/opt/zimbra/lib/ext/backup/zimbrabackup.jar") {
    $prevBackupVersion = &Migrate::getBackupVersion; 
    $currentBackupVersion = `su - zimbra -c "zmjava com.zimbra.cs.backup.util.GetVersion"`;
    chomp($currentBackupVersion);

    return unless ($currentBackupVersion);

    main::progress("Backup Version: $prevBackupVersion New Backup Version: $currentBackupVersion\n");
    Migrate::insertBackupVersion($currentBackupVersion)
      if ($prevBackupVersion eq "");
    Migrate::updateBackupVersion($prevBackupVersion,$currentBackupVersion)
      if ($prevBackupVersion != $currentBackupVersion);
  }

  # clear both directories if the backup version changed.  we aren't going
  # to automatically clear the redolog if the backup version didn't change 
  # because it invalidates all the backups.
  if ($prevBackupVersion != $currentBackupVersion) {
    main::progress("Moving /opt/zimbra/backup/* to /opt/zimbra/backup/${startVersion}-${currentBackupVersion}.\n");
    clearBackupDir("/opt/zimbra/backup", "${startVersion}-${currentBackupVersion}");
    main::progress("Moving /opt/zimbra/redolog/* to /opt/zimbra/redolog/${startVersion}-${currentRedologVersion}.\n");
    clearRedologDir("/opt/zimbra/redolog", "${startVersion}-${currentRedologVersion}");
  }

}

sub migrateLdap {
	if (isInstalled ("zimbra-ldap")) {
		if (-f "/opt/zimbra/openldap-data/ldap.bak") {
			main::progress("Migrating ldap data\n");
			if (-d "/opt/zimbra/openldap-data.prev") {
				`mv /opt/zimbra/openldap-data.prev /opt/zimbra/openldap-data.prev.$$`;
			}
			`mv /opt/zimbra/openldap-data /opt/zimbra/openldap-data.prev`;
			`mkdir /opt/zimbra/openldap-data`;
			`touch /opt/zimbra/openldap-data/DB_CONFIG`;
			`chown -R zimbra:zimbra /opt/zimbra/openldap-data`;
			main::runAsZimbra("/opt/zimbra/openldap/sbin/slapadd -f /opt/zimbra/conf/slapd.conf -l /opt/zimbra/openldap-data.prev/ldap.bak");
		}
		main::runAsZimbra("/opt/zimbra/openldap/sbin/slapindex -f /opt/zimbra/conf/slapd.conf");
		if (startLdap()) {return 1;} 
	}
  return;
}

# DeleteLdapTree
# Requires Net::LDAP ref and DN
# Returns Net::LDAP::Search ref
sub DeleteLdapTree {
  my ($handle, $dn) = @_;
    
  # make sure it exists and get all the entries
  my $result = $handle->search( base => $dn, scope => 'one', filter => "(objectclass=*)");
  return $result if ($result->code());

  # loop through the entries and recursively delete them
  foreach my $entry($result->all_entries) {
    my $ref = DeleteLdapTree($handle, $entry->dn());
    return $ref if ($ref->code());
  }

  $result = $handle->delete($dn);
  return $result;
}

sub migrateAmavisDB($) {
  my ($toVersion) = @_;
  my $amavisdBase = "/opt/zimbra/amavisd-new";
  my $toDir = "${amavisdBase}-$toVersion";
  main::progress("Migrating amavisd-new to version $toVersion\n");
  foreach my $fromVersion qw(2.4.1 2.3.3 2.3.1) {
    my $fromDir = "${amavisdBase}-$fromVersion";
    main::progress("Checking $fromDir/db\n");
    if ( -d "$fromDir/db" && -d "$toDir" && ! -e "$toDir/db/cache.db") {
      main::progress("Migrating amavis-new db from version $fromVersion to $toVersion\n");
      `rm -rf $toDir/db > /dev/null 2>&1`;
      `mv $fromDir/db $toDir/db`;
      `chown zimbra:zimbra $toDir/db`; 
    }
    main::progress("Checking $fromDir/.spamassassin\n");
    if (-d "$fromDir/.spamassassin/" && -d "$toDir" && ! -e "$toDir/.spamassassain/bayes_toks" ) {
      main::progress("Migrating amavis-new .spamassassin from version $fromVersion to $toVersion\n");
      `rm -rf $toDir/.spamassassin > /dev/null 2>&1`;
      `mv $fromDir/.spamassassin $toDir/.spamassassin`;
      `chown zimbra:zimbra $toDir/.spamassassin`; 
    }
  }
}


sub verifyMysqlConfig {
  my $mysqlConf = "/opt/zimbra/conf/my.cnf";
  main::progress("Verifying $mysqlConf\n");
  return if ($addr_space eq "64");
  return unless (-e "$mysqlConf");

  open(CONF, "$mysqlConf") or main::progress("Couldn't read $mysqlConf: $!\n");
  my @lines = <CONF>;
  close(CONF);
  foreach (@lines) {
    if (my ($buffer_size) = m/innodb_buffer_pool_size\s+=\s+(\d+)/) {
      if ($buffer_size > 2000000000) {
        main::progress("innodb_buffer_pool_size must be less then 2GB on a 32bit system\n");
        Migrate::myquit(1,"Please correct $mysqlConf and rerun zmsetup.pl");
      }
    }
  }
  return;
}

1
