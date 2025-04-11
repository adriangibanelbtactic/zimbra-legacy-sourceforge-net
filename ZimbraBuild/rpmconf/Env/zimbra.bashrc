# .bashrc

# User specific aliases and functions

alias rm='rm -i'
alias cp='cp -i'
alias mv='mv -i'
alias h='history 40'
alias j='jobs'

# Source global definitions
if [ -f /etc/bashrc ]; then
	. /etc/bashrc
fi

ZIMBRA_HOME=/opt/zimbra
export ZIMBRA_HOME

JAVA_HOME=/opt/zimbra/java
export JAVA_HOME

JAVA_JVM_VERSION=1.5
export JAVA_JVM_VERSION

PATH=/opt/zimbra/bin:/opt/zimbra/zimbramon:/opt/zimbra/postfix-2.2.9/sbin:/opt/zimbra/openldap/bin:${JAVA_HOME}/bin:/opt/zimbra/snmp/bin:${PATH}
export PATH

if [ "x$LD_LIBRARY_PATH" = "x" ]; then
  LD_LIBRARY_PATH=/opt/zimbra/lib:${LD_LIBRARY_PATH}
else
  LD_LIBRARY_PATH=/opt/zimbra/lib
fi
export LD_LIBRARY_PATH

SNMPCONFPATH=/opt/zimbra/conf
export SNMPCONFPATH

PERLLIB=/opt/zimbra/zimbramon/lib/x86_64-linux-thread-multi:/opt/zimbra/zimbramon/lib:/opt/zimbra/zimbramon/lib/i386-linux-thread-multi:/opt/zimbra/zimbramon/lib/i486-linux-gnu-thread-multi:/opt/zimbra/zimbramon/lib/i586-linux-thread-multi:/opt/zimbra/zimbramon/lib/darwin-thread-multi-2level
export PERLLIB

PERL5LIB=$PERLLIB
export PERL5LIB

ulimit -n 10000 > /dev/null 2>&1
umask 0027
