-- 
-- ***** BEGIN LICENSE BLOCK *****
-- Version: MPL 1.1
-- 
-- The contents of this file are subject to the Mozilla Public License
-- Version 1.1 ("License"). You may not use this file except in
-- compliance with the License. You may obtain a copy of the License at
-- http://www.zimbra.com/license
-- 
-- Software distributed under the License is distributed on an "AS IS"
-- basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
-- the License for the specific language governing rights and limitations
-- under the License.
-- 
-- The Original Code is: Zimbra Collaboration Suite Server.
-- 
-- The Initial Developer of the Original Code is Zimbra, Inc.
-- Portions created by Zimbra are Copyright (C) 2006, 2007 Zimbra, Inc.
-- All Rights Reserved.
-- 
-- Contributor(s):
-- 
-- ***** END LICENSE BLOCK *****
-- 


-- -----------------------------------------------------------------------
-- directory
-- -----------------------------------------------------------------------

CREATE TABLE directory (
   entry_id    INTEGER NOT NULL GENERATED BY DEFAULT AS IDENTITY,
   entry_type  CHAR(4) NOT NULL,
   entry_name  VARCHAR(128) NOT NULL,
   zimbra_id   CHAR(36),
   modified    SMALLINT NOT NULL,

   CONSTRAINT pk_directory PRIMARY KEY (entry_id),
   CONSTRAINT ui_directory_entry_type_name UNIQUE(entry_type, entry_name)
);

CREATE UNIQUE INDEX ui_directory_zimbra_id ON directory(zimbra_id);


CREATE TABLE directory_attrs (
   entry_id    INTEGER NOT NULL,
   name        VARCHAR(255) NOT NULL,
   value       VARCHAR(10240) NOT NULL,

   CONSTRAINT fk_dattr_entry_id FOREIGN KEY (entry_id) REFERENCES directory(entry_id)
      ON DELETE CASCADE
);

CREATE INDEX i_dattr_entry_id_name ON directory_attrs(entry_id, name);
CREATE INDEX i_dattr_name ON directory_attrs(name);


CREATE TABLE directory_leaf (
   entry_id    INTEGER NOT NULL GENERATED BY DEFAULT AS IDENTITY,
   parent_id   INTEGER NOT NULL,
   entry_type  CHAR(4) NOT NULL,
   entry_name  VARCHAR(128) NOT NULL,
   zimbra_id   CHAR(36) NOT NULL,

   CONSTRAINT pk_dleaf PRIMARY KEY (entry_id),
   CONSTRAINT ui_dleaf_zimbra_id UNIQUE (zimbra_id),
   CONSTRAINT ui_dleaf_parent_entry_type_name UNIQUE (parent_id, entry_type, entry_name),
   CONSTRAINT fk_dleaf_entry_id FOREIGN KEY (parent_id) REFERENCES directory(entry_id)
      ON DELETE CASCADE
);


CREATE TABLE directory_leaf_attrs (
   entry_id    INTEGER NOT NULL,
   name        VARCHAR(255) NOT NULL,
   value       VARCHAR(10240) NOT NULL,

   CONSTRAINT fk_dleafattr_entry_id FOREIGN KEY (entry_id) REFERENCES directory_leaf(entry_id)
      ON DELETE CASCADE
);

CREATE INDEX i_dleafattr_entry_id_name ON directory_leaf_attrs(entry_id, name);
CREATE INDEX i_dleafattr_name ON directory_leaf_attrs(name);
