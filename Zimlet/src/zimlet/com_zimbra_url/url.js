/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: ZPL 1.1
 *
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.1 ("License"); you may not use this file except in
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
 * Portions created by Zimbra are Copyright (C) 2006 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK *****
 */

function Com_Zimbra_Url() {
}

Com_Zimbra_Url.prototype = new ZmZimletBase();
Com_Zimbra_Url.prototype.constructor = Com_Zimbra_Url;

Com_Zimbra_Url.prototype.init =
function() {
	
	this._disablePreview = this.getBoolConfig("disablePreview",true);
	// Pre-load placeholder image
	(new Image()).src = this.getResource('blank_pixel.gif');

	this._alexaId = this.getConfig("alexaThumbnailId");	
	if (this._alexaId) {
		this._alexaId = AjxStringUtil.trim(this._alexaId);
		// console.log("Found Alexa ID: %s", this._alexaId);
		this._alexaKey = AjxStringUtil.trim(this.getConfig("alexaThumbnailKey"));
		// console.log("Found Alexa Key: %s", this._alexaKey);
	}
};

// Const
//Com_Zimbra_Url.THUMB_URL = "http://pthumbnails.alexa.com/image_server.cgi?id=" + document.domain + "&url=";
Com_Zimbra_Url.THUMB_URL = "http://images.websnapr.com/?url=";
Com_Zimbra_Url.THUMB_SIZE = 'width="200" height="150"';

Com_Zimbra_Url.prototype.match =
function(line, startIndex) {
	this.RE.lastIndex = startIndex;
	var m = this.RE.exec(line);
	if (!m) {
		return null;
	}

	var last = m[0].charAt(m[0].length - 1);
	if (last == '.' || last == "," || last == '!') {
		var m2 = {index: m.index };
		m2[0] = m[0].substring(0, m[0].length - 1);
		return m2;
	} else {
		return m;
	}
};

Com_Zimbra_Url.prototype._getHtmlContent =
function(html, idx, obj, context) {
	var escapedUrl = obj.replace(/\"/g, '\"');
	if (escapedUrl.substr(0, 4) == 'www.') {
		escapedUrl = "http://" + escapedUrl + "/";
	}
	html[idx++] = "<a target='_blank' href='";
	html[idx++] = escapedUrl;
	html[idx++] = "'>";
	html[idx++] = AjxStringUtil.htmlEncode(obj);
	html[idx++] = "</a>";
	return idx;
};

Com_Zimbra_Url.prototype.toolTipPoppedUp =
function(spanElement, obj, context, canvas) {
	var url = obj;
	if (/^\s*true\s*$/i.test(this.getConfig("stripUrls"))) {
		url = url.replace(/[?#].*$/, "");
	}
	
	if(this._disablePreview){
		this._showUrlThumbnail(url,canvas);
	}else if (this._alexaId)
		this._showAlexaThumbnail(url, canvas);
	else
		this._showFreeThumbnail(url, canvas);
};

Com_Zimbra_Url.prototype._showUrlThumbnail = function(url, canvas){
	canvas.innerHTML = "<b>URL:</b> "+url;
};

Com_Zimbra_Url.prototype._showFreeThumbnail = function(url, canvas) {
	var html = [];
	var i = 0;

	html[i++] = "<img src='";
	html[i++] = this.getResource("blank_pixel.gif");
	html[i++] = "' ";
	html[i++] = Com_Zimbra_Url.THUMB_SIZE;
	html[i++] = " style='background: url(";
	html[i++] = '"';
	html[i++] = Com_Zimbra_Url.THUMB_URL;
	html[i++] = url;
	html[i++] = '"';
	html[i++] = ")'/>";

	canvas.innerHTML = html.join("");
};

Com_Zimbra_Url.ALEXA_THUMBNAIL_CACHE = {};
Com_Zimbra_Url.ALEXA_CACHE_EXPIRES = 10 * 60 * 1000; // 10 minutes

Com_Zimbra_Url.prototype._showAlexaThumbnail = function(url, canvas) {
	canvas.innerHTML = [ "<table style='width: 200px; height: 150px; border-collapse: collapse' cellspacing='0' cellpadding='0'><tr><td align='center'>",
			     ZmMsg.fetchingAlexaThumbnail,
			     "</td></tr></table>" ].join("");

	// check cache first
	var cached = Com_Zimbra_Url.ALEXA_THUMBNAIL_CACHE[url];
	if (cached) {
		var diff = new Date().getTime() - cached.timestamp;
		if (diff < Com_Zimbra_Url.ALEXA_CACHE_EXPIRES) {
			// cached image should still be good, let's use it
			var html = [ "<img src='", cached.img, "' />" ].join("");
			canvas.firstChild.rows[0].cells[0].innerHTML = html;
			return;
		} else {
			// expired
			delete Com_Zimbra_Url.ALEXA_THUMBNAIL_CACHE[url];
		}
	}

	var now = new Date(), pad = Com_Zimbra_Url.zeroPad;
	var timestamp =
		pad(now.getUTCFullYear()  , 4) + "-" +
		pad(now.getUTCMonth() + 1 , 2) + "-" +
		pad(now.getUTCDate()	  , 2) + "T" +
		pad(now.getUTCHours()	  , 2) + ":" +
		pad(now.getUTCMinutes()	  , 2) + ":" +
		pad(now.getUTCSeconds()	  , 2) + ".000Z";
	// console.log("Timestamp: %s", timestamp);
	var signature = this._computeAlexaSignature(timestamp);
	// console.log("Computed signature: %s", signature);
	var args = {
		Service		: "AlexaSiteThumbnail",
		Action		: "Thumbnail",
		AWSAccessKeyId	: this._alexaId,
		Timestamp	: timestamp,
		Signature	: signature,
		Size		: "Large",
		Url		: url
	};
	var query = [];
	for (var i in args)
		query.push(i + "=" + AjxStringUtil.urlComponentEncode(args[i]));
	query = "http://ast.amazonaws.com/xino/?" + query.join("&");
	// console.log("Query URL: %s", query);
	this.sendRequest(null, query, null, new AjxCallback(this, this._alexaDataIn,
							    [ canvas, url, query ]),
			 true);
};

Com_Zimbra_Url.prototype._computeAlexaSignature = function(timestamp) {
	return AjxSHA1.b64_hmac_sha1(this._alexaKey, "AlexaSiteThumbnailThumbnail" + timestamp)
		+ "=";		// guess what, it _has_ to end in '=' :-(
};

Com_Zimbra_Url.prototype._alexaDataIn = function(canvas, url, query, result) {
	var xml = AjxXmlDoc.createFromDom(result.xml);
	var res = xml.toJSObject(true /* drop namespace decls. */,
				 false /* keep case */,
				 true /* do attributes */);
	res = res.Response;
	if (res.ResponseStatus.StatusCode == "Success") {
		if (res.ThumbnailResult.Thumbnail.Exists == "true") {
			var html = [ "<img src='", res.ThumbnailResult.Thumbnail, "' />" ].join("");
			// console.log("HTML: %s", html);
			canvas.firstChild.rows[0].cells[0].innerHTML = html;

			// cache it
			Com_Zimbra_Url.ALEXA_THUMBNAIL_CACHE[url] = {
				img	  : res.ThumbnailResult.Thumbnail,
				timestamp : new Date().getTime()
			};
		} else
			this._showFreeThumbnail(url, canvas);
	} else
		this._showFreeThumbnail(url, canvas);
};

Com_Zimbra_Url.zeroPad = function(number, width) {
	var s = "" + number;
	while (s.length < width)
		s = "0" + s;
	return s;
};
