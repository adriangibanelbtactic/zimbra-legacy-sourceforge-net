MYSQL_ROOT := $(shell pwd)
P4_ROOT ?= $(shell cd $(MYSQL_ROOT)/../..; pwd)

BUILD_PLATFORM ?= $(shell sh $(P4_ROOT)/ZimbraBuild/rpmconf/Build/get_plat_tag.sh)

ifeq ($(BUILD_PLATFORM), )
	BUILD_PLATFORM := "UNKNOWN"
endif

ZIMBRA_HOME ?= /opt/zimbra

MYSQL_VERSION ?= standard-4.1.10a-pc-linux-gnu-i686
MYSQL_CLIENT_VERSION ?= standard-4.1.10a-clientlibs

ifeq ($(BUILD_PLATFORM), MACOSX)
	MYSQL_VERSION := standard-4.1.14-apple-darwin8.2.0-powerpc
	MYSQL_CLIENT_VERSION := standard-4.1.14-clientlibs
endif

all: install symlink

install:
	mkdir -p $(ZIMBRA_HOME)
	(cd $(ZIMBRA_HOME); \
	tar xzf $(MYSQL_ROOT)/$(BUILD_PLATFORM)/mysql-$(MYSQL_VERSION).tar.gz)
	-(cd $(ZIMBRA_HOME)/mysql-$(MYSQL_VERSION)/lib; \
	tar xzf $(MYSQL_ROOT)/$(BUILD_PLATFORM)/mysql-$(MYSQL_CLIENT_VERSION).tgz)

symlink:
	(cd $(ZIMBRA_HOME); ln -s mysql-$(MYSQL_VERSION) mysql)
	ln -s $(ZIMBRA_HOME)/mysql-$(MYSQL_VERSION)/lib $(ZIMBRA_HOME)/mysql-$(MYSQL_VERSION)/lib/mysql

clean: ;

clientlibs:
	rm -rf mysql-4.1.14
	tar xzf mysql-4.1.14.tar.gz
	(cd mysql-4.1.14; ./configure --without-server --libdir=/opt/zimbra/mysql-standard-4.1.14-apple-darwin8.2.0-powerpc/lib --prefix=/opt/zimbra/mysql; make; make install)

allclean: clean
	rm -rf $(ZIMBRA_HOME)/mysql-$(MYSQL_VERSION)
	rm -rf $(ZIMBRA_HOME)/mysql
