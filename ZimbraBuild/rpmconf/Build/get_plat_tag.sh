#!/bin/bash
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


if [ -f /etc/redhat-release ]; then

	i=`uname -i`
	if [ "x$i" = "xx86_64" ]; then
		i="_64"
	else 
		i=""
	fi

	grep "Red Hat Enterprise Linux.*release 5" /etc/redhat-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "RHEL5${i}"
		exit 0
	fi

	grep "Red Hat Enterprise Linux.*release 4" /etc/redhat-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "RHEL4${i}"
		exit 0
	fi

	grep "Fedora Core release 5" /etc/redhat-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "FC5${i}"
		exit 0
	fi

	grep "Fedora Core release 4" /etc/redhat-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "FC4${i}"
		exit 0
	fi

	grep "Fedora Core release 3" /etc/redhat-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "FC3${i}"
		exit 0
	fi

	grep "CentOS release 5" /etc/redhat-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "CentOS5{i}"
		exit 0
	fi

	grep "CentOS release 4" /etc/redhat-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "CentOS4${i}"
		exit 0
	fi

fi

if [ -f /etc/SuSE-release ]; then
	grep "SUSE Linux Enterprise Server 10" /etc/SuSE-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "SuSEES10"
		exit 0
	fi
	grep "SUSE LINUX Enterprise Server 9" /etc/SuSE-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "SuSEES9"
		exit 0
	fi
	grep "SUSE LINUX 10.0" /etc/SuSE-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "SuSE10"
		exit 0
	fi
	grep "openSUSE 10.1" /etc/SuSE-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "openSUSE_10.1"
		exit 0
	fi
	grep "openSUSE 10.2" /etc/SuSE-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "openSUSE_10.2"
		exit 0
	fi
fi

if [ -f /etc/debian_version ]; then
	grep "3.1" /etc/debian_version > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "DEBIAN3.1"
		exit 0
	fi
fi

if [ -f /etc/lsb-release ]; then
	grep "DISTRIB_ID=Ubuntu" /etc/lsb-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo -n "UBUNTU"
	fi
	grep "DISTRIB_RELEASE=6" /etc/lsb-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "6"
		exit 0
	else
		echo "UNKNOWN"
		exit 0
	fi
fi

if [ -f /etc/mandriva-release ]; then
	grep "2006" /etc/mandriva-release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "MANDRIVA2006"
		exit 0
	fi
fi

if [ -f /etc/release ]; then
	egrep 'Solaris 10.*X86' /etc/release > /dev/null 2>&1
	if [ $? = 0 ]; then
		echo "SOLARISX86"
		exit 0
	fi
fi

if [ -f /etc/distro-release ]; then
        echo "RPL1"
        exit 0
fi

p=`uname -p`
if [ "x$p" = "xpowerpc" ]; then
	echo "MACOSX"
	exit 0
fi

a=`uname -a | awk '{print $1}'`
if [ "x$a" = "xDarwin" ]; then
	if [ "x$p" = "xi386" ]; then
		echo "MACOSXx86"
		exit 0
	fi
fi

echo "UNKNOWN"
exit 1
