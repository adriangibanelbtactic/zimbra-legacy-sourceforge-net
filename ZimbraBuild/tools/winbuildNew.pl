use strict;
use Cwd;

my $migWizPrefix = "ZCSMigWiz";
my $impWizPrefix = "ZCSImportWiz";

my $domWizPrefix = "DominoMigrationWizard";

# the PDBs have different names than the desired EXEs
my $migWizPrefix2 = "MigrationWizard";
my $impWizPrefix2 = "PSTImportWizard";

my $toastPrefix = "ZimbraToast";

my $branch = $ARGV[0];
exit unless $branch;

my $gVersion = "1.0.0";
my $installerVersionString = $gVersion;
my $VTAG = "Unknown";
my $serverVersion = "Unknown";
my $p4user = "build";
my $p4pass = "build1pass";
my $p4client = "build-win-$branch";
my $p4port = "eric:1666";

my $base = 'c:\\src';
my $logdir = $base."\\log";
my $src = "$base\\$branch";
my $scmlog   = $logdir."\\$branch-p4.log";
my $buildlog = $logdir."\\$branch-proj-build.log"; 

my $P4 = "p4 -d $src -c $p4client -u $p4user -P $p4pass -p $p4port";

my $importResult = 1;
my $mapiResult = 1;
my $msiResult = 1;
my $toasterResult = 1;

my @d = localtime();
my $date = sprintf ("%d%02d%02d%02d%02d%02d", $d[5]+1900,$d[4]+1,$d[3],$d[2],$d[1],$d[0]);

my $archive_base = "e:\\drop\\$branch\\$date";
my $latest = "e:\\drop\\$branch\\latest";

my @mapi_components = ("LSLIB32","LSMIME32","LSMSABP32","LSMSCFG32","LSMSSP32","LSMSUTIL32","LSMSXP32", "SharingAddin");

init();
getSource();
buildImport();
#updateImportBN();
buildMapi();
updateMapiBN();
buildInstaller();
buildToaster();
#updateToasterBN();
getServerVersion();
archiveMigWiz();
archiveImportWiz();
archiveDominoMig();
archiveZCO();
archiveToaster();
archiveFinalize();
sendResults(); # sendResults() should happen at the very end
exit (0);

sub doCmd {
  my $cmdLine = shift(@_);
  print $cmdLine . "\n";
  system($cmdLine);
}

sub init {
	doCmd( "del /q $scmlog" );
	doCmd( "del /q $buildlog" );
	doCmd( "rmdir /s /q $src" );
}


sub getSource {
	# rename ($src, $src."$date");
	print "Getting source into $src\n";
	mkdir "$logdir";
	mkdir "$src";
	doCmd ("$P4 sync -f ... > $scmlog");
}

sub buildImport {
	print "Building Import\n";
	doCmd ("$src\\import\\doBuild.bat $branch > $buildlog");
	$importResult = $?;
}

sub buildToaster {
	print "Building Toaster\n";
	my $cwd = getcwd();
	chdir "$src\\ZimbraToastInstaller";
	doCmd ("doBuild.bat > $buildlog");
	$toasterResult = $?;
	chdir $cwd;
}

sub buildMapi {
	print "Building MAPI\n";
	doCmd ("$src\\mapi\\doBuild.bat $branch >> $buildlog");
	$mapiResult = $?;
}

sub updateToasterBN {
    print "Updating Toaster Build Number/Version\n\n";
    doCmd ("$P4 edit ZimbraToast\\AssemblyInfo.cs >> $scmlog");
    doCmd ("$P4 edit ZimbraCSharpClient\\Properties\\AssemblyInfo.cs >> $scmlog");
    open C, "$P4 change -o |";
    my @CS = <C>;
    close C;
    
    open(V, "$src\\ZimbraToast\\AssemblyInfo.cs") or die "Can't open AssemblyInfo.cs: $!\n";
    my @lines=<V>;
    close V;
    open(V, ">$src\\ZimbraToast\\AssemblyInfo.cs") or die "Can't open AssemblyInfo.cs: $!\n";
    foreach (@lines) {
		chomp;
		if (/AssemblyVersion/) {
		    my ($maj,$min,$b,$foo) = m/AssemblyVersion\(\"(\d+)\.(\d+)\.(\d+)\.(\d+)\"\)/;
		    $gVersion="$maj.$min.$b.$foo";
			$b++;
			$_="[assembly: AssemblyVersion(\"$maj.$min.$b.$foo\")]";
		} elsif (/AssemblyFileVersion/) {
		    my ($maj,$min,$b,$foo) = m/AssemblyVersion\(\"(\d+)\.(\d+)\.(\d+)\.(\d+)\"\)/;
			$b++;
			$_="[assembly: AssemblyFileVersion(\"$maj.$min.$b.$foo\")]";
		}
		print V $_,"\n";
	}
	close V;
	
	open(V, "$src\\ZimbraCSharpClient\\Properties\\AssemblyInfo.cs") or die "Can't open AssemblyInfo.cs: $!\n";
    my @lines=<V>;
    close V;
    open(V, ">$src\\ZimbraCSharpClient\\Properties\\AssemblyInfo.cs") or die "Can't open AssemblyInfo.cs: $!\n";
    foreach (@lines) {
		chomp;
		if (/AssemblyVersion/) {
		    my ($maj,$min,$b,$foo) = m/AssemblyVersion\(\"(\d+)\.(\d+)\.(\d+)\.(\d+)\"\)/;
		    $gVersion="$maj.$min.$b.$foo";
			$b++;
			$_="[assembly: AssemblyVersion(\"$maj.$min.$b.$foo\")]";
		} elsif (/AssemblyFileVersion/) {
		    my ($maj,$min,$b,$foo) = m/AssemblyVersion\(\"(\d+)\.(\d+)\.(\d+)\.(\d+)\"\)/;
			$b++;
			$_="[assembly: AssemblyFileVersion(\"$maj.$min.$b.$foo\")]";
		}
		print V $_,"\n";
	}
	close V;

    print "Submitting change\n";
	open C, "| $P4 submit -i";
	foreach (@CS) {
		s/<enter description here>/bug: 6038\n\tAUTO UPDATE OF Toaster BUILD NUMBER\n\t$gVersion/;
		print C $_;
	}
	close C;
    

}

sub updateMapiBN {
	print "Updating BN\n\n";
	doCmd ("$P4 edit mapi\\src\\INCLUDE\\ZimbraVersion.h >> $scmlog");
	doCmd ("$P4 edit mapiInstaller\\Version.wxs >> $scmlog");
	open C, "$P4 change -o |";
	my @CS = <C>;
	close C;

	open (V, "$src\\mapi\\src\\INCLUDE\\ZimbraVersion.h") or die "Can't open V: $!";
	my @lines=<V>;
	close V;
	open V, ">$src\\mapi\\src\\INCLUDE\\ZimbraVersion.h" or die "Can't open V: $!";

	foreach (@lines) {
		chomp;
		my (undef, $k, $val) = split (' ',$_,3);
		if (/ZIMBRA_VERSION_NUMBER/) {
			my ($maj,$min,$b,$foo) = split ',', $val;
			$gVersion="$maj.$min.$b.$foo";
			$b++;
			$_="#define $k $maj,$min,$b,$foo";
		} elsif (/ZIMBRA_VERSION_STRING/) {
			$val =~ s/"//g;
			my ($maj,$min,$b) = split /\./, $val;
			$installerVersionString = "$maj.$min.$b";
			$b++;
			$_="#define $k \"$maj.$min.$b\"";
		}
		print V $_,"\n";
	}
	close V;
	open (V, "$src\\mapiInstaller\\Version.wxs");
	my @lines=<V>;
	close V;
	open V, ">$src\\mapiInstaller\\Version.wxs" or die "Can't open V: $!";

	foreach (@lines) {
		chomp;
		my (undef, $k, $val) = split (' ',$_,3);
		if (/<?define ZIMBRA_VERSION_STRING/) {
			print V "<?define ZIMBRA_VERSION_STRING=\"$gVersion\"?>\n";
		} elsif (/<?define ZIMBRA_PRODUCT_GUID/) {
			open G, "uuidgen |";
			my $g = <G>;
			close G;
			chomp $g;
			print V "<?define ZIMBRA_PRODUCT_GUID=\"$g\"?>\n";
		} elsif (/<?define ZIMBRA_PACKAGE_GUID/) {
			open G, "uuidgen |";
			my $g = <G>;
			close G;
			chomp $g;
			print V "<?define ZIMBRA_PACKAGE_GUID=\"$g\"?>\n";
		} else {
			print V $_,"\n";
		}
	}
	close V;

	print "Submitting change\n";
	print "version is $gVersion\n";
	open C, "| $P4 submit -i";
	foreach (@CS) {
		s/<enter description here>/bug: 6038\n\tAUTO UPDATE OF BUILD NUMBER\n\t$gVersion/;
		print C $_;
	}
	close C;

}

sub buildInstaller {
	print "Building installer\n";
	if( $mapiResult == 0 ) {
	
		my $installerSrc = "$src\\mapiInstaller\\SourceDir\\";
	
		#ensure the SourceDir exists
		mkdir "$installerSrc";
		
		#copy the new DLL's to be packaged int the installer over to SourceDir of installer project
		foreach my $comp (@mapi_components) {
			doCmd("copy $src\\mapi\\out\\$comp\\dbg\\usa\\*dll $installerSrc");
		}

		#copy the zcologctl to the source dir
		doCmd("copy $src\\mapi\\out\\ZCOLogCtl\\dbg\\usa\\*exe $installerSrc");

		#build the sucker
		doCmd("$src\\mapiInstaller\\doBuild.bat $branch >> $buildlog");
		$msiResult = $?;

		#remove the contents of SourceDir so next build is fresh
		doCmd("del /q $installerSrc\\*");
	}

}

sub getServerVersion {

	open (FOO, "<$src\\RE\\MAJOR");
	$VTAG = <FOO>;
    chomp $VTAG;
	close FOO;

	$VTAG .= ".";
	open (FOO, "<$src\\RE\\MINOR");
	$VTAG .= <FOO>;
    chomp $VTAG;
	close FOO;

	$VTAG .= ".";
	open (FOO, "<$src\\RE\\MICRO");
	$VTAG .= <FOO>;
    chomp $VTAG;
	close FOO;

	$VTAG .= "_";
	open (FOO, "<$src\\RE\\BUILD");
	$VTAG .= <FOO>;
    chomp $VTAG;
	close FOO;
	
	$serverVersion = $VTAG;
	
}


sub archiveMigWiz {

	print "Archiving migration wizard...\n";
	mkdir "$archive_base";

	#
	#  MigrationWizard
	#
	mkdir "$archive_base\\import";
	mkdir "$archive_base\\import\\bin";
	mkdir "$archive_base\\import\\sym";
	doCmd ("copy $src\\import\\Release\\${migWizPrefix}.exe $archive_base\\import\\bin\\${migWizPrefix}-$VTAG.exe");
	doCmd ("copy $src\\import\\Release\\${migWizPrefix}.pdb $archive_base\\import\\sym\\${migWizPrefix}-$VTAG.pdb");
	doCmd ("copy $src\\import\\Release\\${migWizPrefix2}.pdb $archive_base\\import\\sym\\${migWizPrefix2}-$VTAG.pdb");
	doCmd ("copy $src\\import\\Release\\vc70.pdb $archive_base\\import\\sym");
	
}

sub archiveImportWiz {

	print "Archiving import wizard...\n";

	#
	#  ImportWizard
	#
	mkdir "$archive_base\\pstimport";
 	mkdir "$archive_base\\pstimport\\bin";
	mkdir "$archive_base\\pstimport\\sym";
	doCmd ("copy $src\\import\\ReleasePST\\${impWizPrefix}.exe $archive_base\\pstimport\\bin\\${impWizPrefix}-$VTAG.exe");
	doCmd ("copy $src\\import\\ReleasePST\\${impWizPrefix}.pdb $archive_base\\pstimport\\sym\\${impWizPrefix}-$VTAG.pdb");
	doCmd ("copy $src\\import\\ReleasePST\\${impWizPrefix2}.pdb $archive_base\\pstimport\\sym\\${impWizPrefix2}-$VTAG.pdb");
	doCmd ("copy $src\\import\\ReleasePST\\vc70.pdb $archive_base\\pstimport\\sym");
	
}


sub archiveDominoMig {

	print "Archiving domino migration...\n";

	#
	#  DominoMigration
	#
    mkdir "$archive_base\\domino";
    mkdir "$archive_base\\domino\\bin";
    mkdir "$archive_base\\domino\\sym";
	doCmd ("copy $src\\import\\ReleaseDomino\\${domWizPrefix}.exe $archive_base\\domino\\bin\\${domWizPrefix}-$VTAG.exe");
	doCmd ("copy $src\\import\\ReleaseDomino\\${domWizPrefix}.pdb $archive_base\\domino\\sym\\${domWizPrefix}-$VTAG.pdb");

}


sub archiveZCO {

	print "Archiving ZCO...\n";

	#
	#  ZCO
	#
	mkdir "$archive_base\\mapi";
	mkdir "$archive_base\\mapi\\bin";
	mkdir "$archive_base\\mapi\\sym";
	mkdir "$archive_base\\mapi\\msi";
	foreach my $i (@mapi_components) {
		mkdir "$archive_base\\mapi\\sym\\$i";
		doCmd ("copy $src\\mapi\\out\\$i\\dbg\\usa\\*dll $archive_base\\mapi\\bin");
		doCmd ("copy $src\\mapi\\out\\$i\\dbg\\usa\\*pdb $archive_base\\mapi\\sym\\$i");
	}
	mkdir "$archive_base\\mapi\\sym\\ZCOLogCtl";
	mkdir "$archive_base\\mapi\\msi";
	mkdir "$archive_base\\mapi\\sym\\ZMapiProCA";
	doCmd ("copy $src\\mapi\\out\\ZCOLogCtl\\dbg\\usa\\*exe $archive_base\\mapi\\bin");
	doCmd ("copy $src\\mapi\\out\\ZCOLogCtl\\dbg\\usa\\*pdb $archive_base\\mapi\\sym\\ZCOLogCtl");
	doCmd("copy $src\\mapiInstaller\\bin\\Debug\\*.msi $archive_base\\mapi\\msi");
	doCmd("ren $archive_base\\mapi\\msi\\ZimbraOlkConnector.msi ZimbraOlkConnector-${VTAG}_$installerVersionString.msi");
	doCmd("copy $src\\mapiInstaller\\bin\\Debug\\ZMapiProCA.dll $archive_base\\mapi\\bin" );
	doCmd("copy $src\\mapiInstaller\\bin\\Debug\\ZMapiProCA.pdb $archive_base\\mapi\\sym\\ZMapiProCA");
	
}


sub archiveToaster {

	print "Archiving toaster...\n";

	#
	#  Toaster
	#
	mkdir "$archive_base\\toaster";
	mkdir "$archive_base\\toaster\\bin";
	mkdir "$archive_base\\toaster\\sym";
	mkdir "$archive_base\\toaster\\msi";
	doCmd ("copy $src\\ZimbraToast\\bin\\Debug\\Zimbra.Client.pdb        $archive_base\\toaster\\sym\\");
	doCmd ("copy $src\\ZimbraToast\\bin\\Debug\\Zimbra.Client.dll        $archive_base\\toaster\\bin\\");
	doCmd ("copy $src\\ZimbraToast\\bin\\Debug\\ZToast.pdb               $archive_base\\toaster\\sym\\${toastPrefix}-${VTAG}.pdb");
	doCmd ("copy $src\\ZimbraToast\\bin\\Debug\\ZToast.exe               $archive_base\\toaster\\bin\\${toastPrefix}-${VTAG}.exe");
	doCmd ("copy $src\\ZimbraToastInstaller\\bin\\Debug\\ZimbraToast.msi $archive_base\\toaster\\msi\\${toastPrefix}-${VTAG}.msi");
	
}

sub archiveFinalize {
	#
	#  Build log files
	#
	doCmd ("copy $buildlog $archive_base\\");
	doCmd ("copy $scmlog   $archive_base\\");


	#
	#  Save these bits as the latest bits
	#

    #delete everything from latest
	doCmd ("rmdir /s /q $latest" );
    #create the latest directory
	doCmd ("mkdir $latest" );
	#copy the most recent stuff to latest
	doCmd ("xcopy /E $archive_base\\* $latest" );

}

sub sendResults {

	use MIME::Lite;
	
	MIME::Lite->send('smtp', "dogfood.zimbra.com", Timeout=>60);
	
	my $body = "Zimbra Server Version: $serverVersion";

	$body .= "\nMigration Wizard build ";
	if( $importResult == 0 ) { $body .= "succeeded"; } 
	else { $body .= "FAILED"; }

	$body .= "\nMAPI build $gVersion ";
	if( $mapiResult == 0 ) { $body .= "succeeded"; } 
	else { $body .= "FAILED"; }

	$body .= "\nMSI build ";
	if( $msiResult == 0 ) { $body .= "succeeded"; }
	else { $body .= "FAILED"; }
	
	$body .= "\nToaster build ";
	if( $toasterResult == 0 ) { $body .= "succeeded"; }
	else { $body .= "FAILED"; }
	
	my $subject = "windows build $branch-$date ";
	if( $mapiResult != 0 ||  $importResult != 0 || $msiResult != 0 )
	{
		$subject .= "FAILED";
	}

	my $msg = MIME::Lite->new(
		From		=>'Windows Build Server <build-win@zimbra.com>',
		To		=>'build-win@zimbra.com',
		Subject	=>"$subject",
		Data		=>"$body" );

	$msg->attach(
		Type		=>'text/plain',
		Path		=>"$scmlog",
		Filename	=>'p4.log',
		Encoding	=>'quoted-printable');

	$msg->attach(
		Type		=>'text/plain',
		Path		=>"$buildlog",
		Filename	=>'build.log',
		Encoding	=>'quoted-printable');

	if( $mapiResult != 0 ||  $importResult != 0 || $msiResult != 0 )
	{
		$msg->add( 'X-Priority' => '1');
	}
	else
	{
		$msg->add( 'X-Priority' => '5');
	}


	$msg->send;
}
