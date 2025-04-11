PCRE_ROOT := $(shell pwd)
P4_ROOT ?= $(shell cd $(PCRE_ROOT)/../..; pwd)

BUILD_PLATFORM ?= $(shell sh $(P4_ROOT)/ZimbraBuild/rpmconf/Build/get_plat_tag.sh)

ifeq ($(BUILD_PLATFORM), MACOSX)
	ENVMOD := env LIBS="-lresolv" 
endif

ZIMBRA_HOME ?= /opt/zimbra

all: build_$(BUILD_PLATFORM) 

build_MACOSX:
	mkdir -p $(ZIMBRA_HOME)/lib
	(cd $(ZIMBRA_HOME)/lib; tar xzf $(PCRE_ROOT)/builds/$(BUILD_PLATFORM)/pcre.tgz)

build_FC4: ;

build_FC3: ;

build_RHEL4: ;

build_DEBIAN3.1: ;

build_SUSEES9: ;

clean: ;

allclean: clean
