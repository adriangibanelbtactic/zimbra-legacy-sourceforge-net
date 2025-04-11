%define evolution_version 2.3.7
%define eds_version 1.3.7
%define evo_api_version 2.6

Summary: Zimbra Connector for Evolution %{evo_api_version}
Name: evolution-zimbra
Version: 0.1.0
Release: 1
License: GPL
URL: http://www.zimbra.com/evolution-connector.htm
Group: System Environment/Libraries

Source: http://www.zimbra.com/work/%{name}-%{version}.tar.gz
Vendor: Zimbra
Packager: Scott Herscher <scott.herscher@zimbra.com>
BuildRoot: %{_tmppath}/%{name}-root

Provides:  evolution-zimbra

BuildRequires:  evolution-devel >= %{evolution_version}
BuildRequires:  evolution-data-server-devel >= %{eds_version}
BuildRequires:  glib2 >= 2.8.1

Requires:  evolution >= %{evolution_version}
Requires:  evolution-data-server >= %{eds_version}
Requires:  glib2 >= 2.8.1

%description
Use Zimbra Connector to use all features provided by the
Zimbra mail server in Evolution %{evo_api_version}.

%prep
%setup -q

%build
%configure
make

%install
[ -n $RPM_BUILD_ROOT -a $RPM_BUILD_ROOT != / ] && rm -rf $RPM_BUILD_ROOT
env DESTDIR=$RPM_BUILD_ROOT make -e install

%find_lang %{name}-2.6

%clean
[ -n $RPM_BUILD_ROOT -a $RPM_BUILD_ROOT != / ] && rm -rf $RPM_BUILD_ROOT

%files -f %{name}-2.6.lang
/usr/lib/evolution-data-server-1.2/camel-providers/*
/usr/lib/evolution-data-server-1.2/extensions/*
/usr/lib/evolution/2.6/plugins/*
%{_datadir}/%{name}/%{evo_api_version}/glade/*.glade
%changelog
