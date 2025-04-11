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

ID=`id -u`

PATH="/bin:/usr/bin:/sbin:/usr/sbin:/opt/zimbra/bin"
export PATH

if [ "x$ID" != "x0" ]; then
  echo "Run as root!"
  exit 1
fi

removeUserFromGroup () {

  if [ $# -lt 2 ]; then
    return
  fi

  groups="$1"
  user="$2"

  if [ x$groups == "xall" ]; then
    groups="$(id -Gnr $user 2> /dev/null)"
  fi

  # Loop to remove the user from each group
  #
  for group in $groups; do
    # get the group number from the name
    gid="$(nireport . /groups name gid | grep -w "^$group" | cut -f 2)"

    # check if the group exists
    strgroup="$(nifind /groups/$group .)"
    # check if the user is listed for the group (not listed in own primary)
    stringroup="$(nireport . /groups name users | grep -w "^$group[[:space:]].*$user")"
    # check if this is the user's primary group
    strprimary="$(nireport . /users name gid | grep -w "^$user[[:space:]]*$gid")"
  
    # ensure that the group exists...
    if [ -z "$strgroup" ]; then
      echo "Group $group does not exist"
    # ...and this is not the user's primary group
    elif [ ! -z "$strprimary" ]; then
      echo "Not removing from primary group $group"
    # ...and that the user is listed in the group
    elif [ -z "$stringroup" ]; then
      echo "User $user not listed in $group"
    else
      # remove user from the group
      dscl . delete /groups/$group users $user
      echo "User $user removed from group $group"
    fi
  done
}

usage() {

  echo "$0 [-u] [-c config] [-m zcs.mpkg | -d zcs.dmg] [-l ZCSLicense.xml] [-s] [-h]"
  echo ""
  echo " -u          Uninstall Zimbra Collaboration Suite"
  echo " -d zcs.dmg  Install ZCS from contents of specified disk image"
  echo " -m zcs.mpkg Install ZCS with specified package"
  echo " -l license  ZCSLicense.xml file"
  echo " -c config   Use install defaults from config file"
  echo " -s          Install software only. Skips zmsetup.pl"
  echo " -h          Usage"
}

MYDIR=`dirname $0`

SOFTWAREONLY="no"
UNINSTALL="no"
ZIMBRA_USER="zimbra"
if [ $# == 0 ]; then
  usage
  exit
fi

while [ $# -ne 0 ]; do
  case $1 in
    -r) shift
      RESTORECONFIG=$1
    ;;
    -u) UNINSTALL="yes"
    ;;
    -s) SOFTWAREONLY="yes"
    ;;
    -h) USAGE="yes"
    ;;
    -v) VERBOSE="yes"
    ;;
    -c) shift
      DEFAULTSFILE=$1
    ;;
    -d) shift
      DMG=$1
    ;;
    -m) shift
      PKG=$1
    ;;
    -l) shift
      LICENSE=$1
    ;;
  esac
  shift
done

if [ "x$USAGE" == "xyes" ]; then
  usage
  exit
fi

if [ x$UNINSTALL == "xyes" ]; then

  # unload and remove launchd
  echo -n "Unloading and removing zimbra from launchd..."
  launchctl unload -w /System/Library/LaunchDaemons/com.zimbra.zcs.plist 2> /dev/null
  rm /System/Library/LaunchDaemons/com.zimbra.zcs.plist 2> /dev/null
  echo "done."

  tmp="$(id $ZIMBRA_USER 2> /dev/null)"
  if [ $? == 0 ]; then


    # stop the processes nicely?
    echo -n "Stopping all zimbra processes..."
    su $ZIMBRA_USER -c '/opt/zimbra/bin/zmcontrol stop > /dev/null 2>&1'
    # make sure all processes are really stopped
    ps -aux  |grep $ZIMBRA_USER | egrep -v "install|grep" | awk '{print $2}' | xargs kill 2> /dev/null
    echo "done."

    # remove crontab
    echo -n "Removing crontab entry for $ZIMBRA_USER..."
    crontab -u $ZIMBRA_USER -r
    echo "done."
  fi


  # clean up syslog
  echo -n "Cleaning up syslog.conf..."
  if [ ! -e "/etc/syslog.conf.zimbra" ]; then
    cp -f /etc/syslog.conf /etc/syslog.conf.zimbra
  fi
  sed -i .zimbra -e 's:\(.*zimbra\.log.*\):#\1:' /etc/syslog.conf
  if [ $? != 0 ]; then
    echo "failed."
    mv /etc/syslog.conf.zimbra /etc/syslog.conf
  else 
    sed -i .zimbra -e 's:\(.*slapd\.log.*\):#\1:' /etc/syslog.conf
    if [ $? != 0 ]; then
      echo "failed."
      mv /etc/syslog.conf.zimbra /etc/syslog.conf
    else 
      echo "done."
    fi
  fi

  # remove log rotation
  if [ -f "/etc/periodic/daily/600.zimbra" ]; then
    rm /etc/periodic/daily/600.zimbra
  fi

  # clean up sudoers
  echo -n "Cleaning up /etc/sudoers..."
  egrep -v '^%zimbra' /etc/sudoers > /tmp/sudoers.$$
  mv -f /tmp/sudoers.$$ /etc/sudoers 2> /dev/null
  rm -f /tmp/sudoers.$$ 2> /dev/null
  chmod 440 /etc/sudoers
  echo "done".

  # clean up Receipts
  echo -n "Removing /opt/zimbra and packaging receipts..."
  rm -rf /opt/zimbra /Library/Receipts/zimbra-* 2> /dev/null
  echo "done."

  # remove group and user
  echo -n "Deleting group $ZIMBRA_USER..."
  tmp=""
  tmp="$(nifind /groups/$ZIMBRA_USER .)"
  if [ -z "$tmp" ]; then
    echo "group didn't exist."
  else 
    dscl . delete /groups/$ZIMBRA_USER
    echo "done."
  fi

  removeUserFromGroup  "all" $ZIMBRA_USER;

  echo -n "Deleting user $ZIMBRA_USER..."
  tmp="$(nifind /users/$ZIMBRA_USER .)"
  if [ -z "$tmp" ]; then
    echo "didn't exist."
  else 
    dscl . delete /users/$ZIMBRA_USER
    echo "done."
  fi

  echo -n "Deleting /opt/zimbra..."
  if [ -e "/opt/zimbra" ]; then
   rm -rf /opt/zimbra > /dev/null 2>&1
   echo "done."
  else 
   echo "didn't exist."
  fi

  echo -n "Reenabling postfix..."
  launchctl load -w /System/Library/LaunchDaemons/org.postfix.master.plist 2> /dev/null
  echo "done."
fi

if [ x$VERBOSE = "xyes" ]; then
  INSTALLEROPTS="-dumplog -verbose"
else 
  INSTALLEROPTS=""
fi

# Mount the dmg and run the installer
if [ x$PKG != "x" ]; then
  installer $INSTALLEROPTS -lang en -pkg $PKG -target / 2> /tmp/zcs.install.log.$$
elif [ x$DMG != "x" ]; then
  DIR=`basename $DMG .dmg`
  echo "Installing from $DMG"
  hdiutil mount $DMG 2> /tmp/zcs.install.log.$$
  installer $INSTALLEROPTS -lang en -pkg /Volumes/${DIR}/zcs.mpkg -target / 2> /tmp/zcs.install.log.$$
  hdiutil unmount /Volumes/${DIR}
fi

# Install the license file
if [ x"$LICENSE" != "x" -a -r "$LICENSE" ]; then
  cp $LICENSE /opt/zimbra/conf/ZCSLicense.xml
fi

# Configure the installation
if [ x$SOFTWAREONLY == "xno" ] && [ x$DMG != "x" -o x$PKG != "x"  ]; then
  if [ x$DEFAULTSFILE == "x" ]; then
    /opt/zimbra/libexec/zmsetup.pl
  else 
    /opt/zimbra/libexec/zmsetup.pl -c $DEFAULTSFILE
  fi
elif [ x$UNINSTALL != "xyes" ]; then
  echo "You will need to execute /opt/zimbra/libexec/zmsetup.pl"
  echo "to complete your installation."
fi
