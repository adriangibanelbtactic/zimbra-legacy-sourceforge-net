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
# Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
# All Rights Reserved.
# 
# Contributor(s):
# 
# ***** END LICENSE BLOCK *****
# 
package XmlDoc;

use strict;
use warnings;

BEGIN {
    use Exporter   ();
    our ($VERSION, @ISA, @EXPORT, @EXPORT_OK, %EXPORT_TAGS);

    # set the version for version checking
    $VERSION     = 1.00;
    @ISA         = qw(Exporter);
    @EXPORT      = qw();
    %EXPORT_TAGS = ( );     # eg: TAG => [ qw!name1 name2! ],

    # your exported package globals go here,
    # as well as any optionally exported functions
    @EXPORT_OK   = qw();
}

our @EXPORT_OK;

sub new {
    my $type = shift;
    my $self = {};
    bless $self, $type;
    return $self;
}

sub to_string {
    my $self = shift;
    return $self->{'root'}->to_string(@_);
}
 
sub start {
    my $self = shift;
    my $name = shift;
    my $ns = shift;
    my $element = new XmlElement($name, $ns);
    if (@_) {
	my $attrs = shift;
	$element->attrs($attrs) if defined($attrs);
    }
    if (@_) {
	$element->content(shift);
    }
    if (!defined($self->{'root'})) {
	$self->{'root'} = $element;
    } else {
	my $s = $self->{'stack'};
	my $parent = @{$s}[$#{$s}];
	$parent->add_child($element);
    }
    push(@{$self->{'stack'}}, $element);
    return $self;
}

sub current {
    my $self = shift;
    my $s = $self->{'stack'};
    my $e = @{$s}[$#{$s}] || die "not in an element";
    return $e;
}

sub end {
    my $self = shift;
    my $s = $self->{'stack'};
    my $e = @{$s}[$#{$s}] || die "not in an element";
    if (@_) {
	my $name = shift;
	my $aname = $e->name;
	if ($name ne $aname) {
	    die "name mismatch in end. expecting($name), actual($aname)";
	}
    }
    pop(@{$self->{'stack'}});
 }

sub add {
    my ($self, $name, $ns, $attrs, $text) = @_;
    $self->start($name, $ns, $attrs);
    $self->current->append_content($text) if defined($text);
    $self->end;
}

sub root {
    my $self = shift;
    return $self->{'root'};
}

1;

__END__
