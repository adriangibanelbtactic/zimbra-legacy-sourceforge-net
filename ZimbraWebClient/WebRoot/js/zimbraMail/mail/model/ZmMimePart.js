/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: ZPL 1.2
 * 
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.2 ("License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.zimbra.com/license
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is: Zimbra Collaboration Suite Web Client
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */

function ZmMimePart() {
	
	ZmModel.call(this, ZmEvent.S_ATT);
	
	this.children = new AjxVector();
	this.node = new Object();
};

ZmMimePart.prototype = new ZmModel;
ZmMimePart.prototype.constructor = ZmMimePart;

ZmMimePart.prototype.toString = 
function() {
	return "ZmMimePart";
};

ZmMimePart.createFromDom =
function(node, args) {
	var mimePart = new ZmMimePart();
	mimePart._loadFromDom(node, args.attachments, args.bodyParts);
	return mimePart;
};

ZmMimePart.prototype.getContent = 
function() {
	return this.node.content;
};

ZmMimePart.prototype.getContentForType = 
function(contentType) {
	var topChildren = this.children.getArray();

	if (topChildren.length) {
		for (var i = 0; i < topChildren.length; i++) {
			if (topChildren[i].getContentType() == contentType)
				return topChildren[i].getContent();
		}
	} else {
		if (this.getContentType() == contentType)
			return this.getContent();
	}
	return null;
};

ZmMimePart.prototype.setContent = 
function(content) {
	this.node.content = content;
};

ZmMimePart.prototype.getContentDisposition =
function() {
	return this.node.cd;
};

ZmMimePart.prototype.getContentType =
function() {
	return this.node.ct;
};

ZmMimePart.prototype.setContentType =
function(ct) {
	this.node.ct = ct;
};

ZmMimePart.prototype.setIsBody = 
function(isBody) {
	this.node.body = isBody;
};

ZmMimePart.prototype.getFilename =
function() {
	return this.node.filename;
};

ZmMimePart.prototype._loadFromDom =
function(partNode, attachments, bodyParts) {
	for (var i = 0; i < partNode.length; i++) {
		this.node = partNode[i];

		if (this.node.content)
			this._loaded = true;

		if (this.node.cd == "attachment" || 
			this.node.ct == ZmMimeTable.MSG_RFC822 ||
			this.node.filename != null || 
			this.node.ci != null || this.node.cl != null)
		{
			attachments.push(this.node);
		}

		if (this.node.body &&
			(this.node.ct == ZmMimeTable.TEXT_HTML || this.node.ct == ZmMimeTable.TEXT_PLAIN))
		{
			// add subsequent body parts as attachments if already found
			if (ZmMimePart._contentTypeFound(bodyParts, this.node))
				attachments.push(this.node);
			bodyParts.push(this.node);
		}

		// bug fix #4616 - dont add attachments part of a rfc822 msg part
		if (this.node.mp && this.node.ct != ZmMimeTable.MSG_RFC822) {
			var params = {attachments: attachments, bodyParts: bodyParts};
			this.children.add(ZmMimePart.createFromDom(this.node.mp, params));
		}
	}
};

ZmMimePart._contentTypeFound =
function(bodyParts, node) {
	for (var i = 0; i < bodyParts.length; i++) {
		if (bodyParts[i].ct == node.ct)
			return true;
	}
	return false;
};
