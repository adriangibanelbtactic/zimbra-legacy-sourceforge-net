POSTFIX_ROOT := $(shell pwd)

ZIMBRA_HOME ?= /opt/zimbra
POSTFIX_VERSION ?= 2.2.3

P4_ROOT ?= $(shell cd $(POSTFIX_ROOT)/../../..; pwd)

BUILD_PLATFORM ?= $(shell sh $(P4_ROOT)/ZimbraBuild/rpmconf/Build/get_plat_tag.sh)

ifeq ($(BUILD_PLATFORM), DEBIAN3.1)
	DBINC := 
	DBLIB := 
else
	DBINC := -I/opt/zimbra/sleepycat-4.2.52.4/include
	DBLIB := -L/opt/zimbra/sleepycat-4.2.52.4/lib
endif


ifeq ($(BUILD_PLATFORM), MACOSX)
	MYSQL_VERSION ?= standard-4.1.14-apple-darwin8.2.0-powerpc
	MYSQL_CLIENT_VERSION ?= standard-4.1.14-apple-darwin8.2.0-powerpc
	PCRE_DEF := -DHAS_PCRE
	PCRE_INCLUDE := -I/opt/zimbra/lib/include
	PCRE_LIB := -L/opt/zimbra/lib -lpcre
else
	PCRE_DEF := -DHAS_PCRE
	PCRE_INCLUDE := -I/usr/include/pcre
	PCRE_LIB := -lpcre
	MYSQL_VERSION ?= standard-4.1.10a-pc-linux-gnu-i686
	MYSQL_CLIENT_VERSION ?= standard-4.1.10a-clientlibs
endif

all: prep build install tar

prep:
	rm -rf $(ZIMBRA_HOME)/postfix-$(POSTFIX_VERSION)
	mkdir -p $(ZIMBRA_HOME)/postfix-$(POSTFIX_VERSION)/conf
	cp main.cf $(ZIMBRA_HOME)/postfix-$(POSTFIX_VERSION)/conf

build:
	(tar xzf postfix-$(POSTFIX_VERSION).tar.gz; cp makedefs.zimbra \
	postfix-$(POSTFIX_VERSION)/makedefs; \
	cd postfix-$(POSTFIX_VERSION); \
	make makefiles \
	CCARGS='-DDEF_CONFIG_DIR=\"/opt/zimbra/postfix-$(POSTFIX_VERSION)/conf\" \
	-DDEF_SENDMAIL_PATH=\"/opt/zimbra/postfix-$(POSTFIX_VERSION)/sbin\" \
	-DUSE_SASL_AUTH \
	-DHAS_LDAP -DHAS_MYSQL -DUSE_TLS $(DBINC) $(PCRE_DEF) $(PCRE_INCLUDE) \
	-I/opt/zimbra/openldap-2.2.28/include \
	-I/opt/zimbra/mysql-$(MYSQL_VERSION)/include \
	-I/opt/zimbra/cyrus-sasl-2.1.21.ZIMBRA/include/sasl -I/usr/include' \
	AUXLIBS='-L/opt/zimbra/cyrus-sasl-2.1.21.ZIMBRA/lib \
	$(DBLIB) -ldb -L/opt/zimbra/openldap-2.2.28/lib $(PCRE_LIB) \
	-lldap -llber -L/opt/zimbra/mysql-$(MYSQL_VERSION)/lib \
	-lmysqlclient -lz -lm -L/usr/lib -lsasl2 -lpthread -lssl -lcrypto'; \
	make;)

install:
	(cd postfix-$(POSTFIX_VERSION); \
	make upgrade)
	(cd $(ZIMBRA_HOME); ln -s $(ZIMBRA_HOME)/postfix-$(POSTFIX_VERSION) postfix)

tar:
	mkdir -p $(POSTFIX_ROOT)/builds/$(BUILD_PLATFORM)
	(cd $(ZIMBRA_HOME); tar czf \
	$(POSTFIX_ROOT)/builds/$(BUILD_PLATFORM)/postfix-$(POSTFIX_VERSION).tgz \
	postfix-$(POSTFIX_VERSION))
	chmod -R a+w $(POSTFIX_ROOT)/builds/$(BUILD_PLATFORM)

clean:
	rm -rf postfix-$(POSTFIX_VERSION)

allclean: clean zimclean

zimclean:
	rm -rf $(ZIMBRA_HOME)/postfix-$(POSTFIX_VERSION) $(ZIMBRA_HOME)/postfix
