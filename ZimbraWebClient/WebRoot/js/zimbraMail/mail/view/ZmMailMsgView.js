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
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK *****
 */

function ZmMailMsgView(parent, className, posStyle, mode, controller) {

	className = className || "ZmMailMsgView";
	DwtComposite.call(this, parent, className, posStyle);

	this._mode = mode;
	this._controller = controller;

	this._displayImagesId = Dwt.getNextId();
	this._tagRowId = Dwt.getNextId();
	this._tagCellId = Dwt.getNextId();
	this._appCtxt = this.shell.getData(ZmAppCtxt.LABEL);

	this.setScrollStyle(DwtControl.CLIP);

	// customize per "mode"
	if (mode == ZmController.MSG_NEW_WIN_VIEW) {
		return;
	} else if (mode == ZmController.MSG_VIEW) {
		// Add a change listener to taglist to track tag color changes
		this._tagList = this._appCtxt.getTree(ZmOrganizer.TAG);
		this._tagList.addChangeListener(new AjxListener(this, ZmMailMsgView.prototype._tagChangeListener));
	}

	this._setMouseEventHdlrs(); // needed by object manager

	// this manages all the detected objects within the view
	this._objectManager = new ZmObjectManager(this, this._appCtxt);

	this._changeListener = new AjxListener(this, this._msgChangeListener);
	this.addListener(DwtEvent.ONMOUSEDOWN, new AjxListener(this, this._mouseDownListener));
	this.addListener(DwtEvent.ONSELECTSTART, new AjxListener(this, this._selectStartListener));
	this.addListener(DwtEvent.ONCONTEXTMENU, new AjxListener(this, this._contextMenuListener));
	this.addListener(DwtEvent.CONTROL, new AjxListener(this, this._controlEventListener));
}

ZmMailMsgView.prototype = new DwtComposite;
ZmMailMsgView.prototype.constructor = ZmMailMsgView;

// Consts

ZmMailMsgView.HEADER_ID = "h--" + Dwt.getNextId();
ZmMailMsgView.QUOTE_DEPTH_MOD = 3;
ZmMailMsgView.MAX_SIG_LINES = 8;
ZmMailMsgView.SIG_LINE = /^(- ?-+)|(__+)\r?$/;
ZmMailMsgView._inited = false;

ZmMailMsgView.OBJ_SIZE_TEXT = 50; // max. size of text emails that will automatically highlight objects
ZmMailMsgView.OBJ_SIZE_HTML = 50; // similar for HTML emails.

ZmMailMsgView.REPLY_INVITE_EVENT = "inviteReply";
ZmMailMsgView.SHARE_EVENT = "share";

// Public methods

ZmMailMsgView.prototype.toString =
function() {
	return "ZmMailMsgView";
};

ZmMailMsgView.prototype.getController =
function() {
	return this._controller;
}

ZmMailMsgView.prototype.reset =
function() {
	this._htmlBody = null;
	this.getHtmlElement().innerHTML = "";
	if (this._objectManager)
		this._objectManager.reset();
}

ZmMailMsgView.prototype._getInviteToolbar =
function() {
	// TODO: reuse the toolbar
	if (this._inviteToolbar)
		this._inviteToolbar.dispose();

	this._operationButtonIds = [ZmOperation.REPLY_ACCEPT, ZmOperation.REPLY_TENTATIVE, ZmOperation.REPLY_DECLINE];
	this._inviteToolbar = new ZmButtonToolBar(this,	this._operationButtonIds,
						  null, DwtControl.STATIC_STYLE,
						  "ZmInviteToolBar", "DwtButton");
	// get a little space between the buttons.
	var toolbarHtmlEl = this._inviteToolbar.getHtmlElement();
	toolbarHtmlEl.firstChild.cellPadding = "3";
	var inviteToolBarListener = new AjxListener(this, this._inviteToolBarListener);

	for (var i = 0; i < this._operationButtonIds.length; i++) {
		var id = this._operationButtonIds[i];

		// HACK for IE, which doesn't support multiple classnames. If I
		// just change the styles, the activated class overrides the basic
		// activated class definition, thus I have to change what the
		// activated class name will be for the buttons in the toolbar.
		var b = this._inviteToolbar.getButton(id);
		b._activatedClassName = b._className + "-" + DwtCssStyle.ACTIVATED;
		b._triggeredClassName = b._className + "-" + DwtCssStyle.TRIGGERED;

		this._inviteToolbar.addSelectionListener(id, inviteToolBarListener);
	}
	return this._inviteToolbar;
};

ZmMailMsgView.prototype._inviteToolBarListener =
function(ev) {
	ev._inviteReplyType = ev.item.getData(ZmOperation.KEY_ID);;
	ev._inviteComponentId = null;
	this.notifyListeners(ZmMailMsgView.REPLY_INVITE_EVENT, ev);
}

ZmMailMsgView.prototype.addInviteReplyListener =
function (listener) {
	this.addListener(ZmMailMsgView.REPLY_INVITE_EVENT, listener);
};

ZmMailMsgView.prototype._controlEventListener = function(ev) {
	var iframe = this.getElementById(this._iframeId);
	// we get here before we have a chance to initialize the IFRAME
	if (iframe) {
		var act = new AjxTimedAction();
		act.method = ZmMailMsgView._resetIframeHeight;
		act.params.add(this);
		act.params.add(iframe);
		AjxTimedAction.scheduleAction(act, 5);
	}
};

ZmMailMsgView.prototype._getShareToolbar =
function() {
	// TODO: reuse the toolbar
	if (this._shareToolbar)
		this._shareToolbar.dispose();

	var buttonIds = [ZmOperation.SHARE_ACCEPT, ZmOperation.SHARE_DECLINE];
	this._shareToolbar = new ZmButtonToolBar(this,	buttonIds,
											  null, DwtControl.STATIC_STYLE,
											  "ZmShareToolBar", "DwtButton");
	// get a little space between the buttons.
	var toolbarHtmlEl = this._shareToolbar.getHtmlElement();
	toolbarHtmlEl.firstChild.cellPadding = "3";

	var shareToolBarListener = new AjxListener(this, this._shareToolBarListener);
	for (var i = 0; i < buttonIds.length; i++) {
		var id = buttonIds[i];

		// HACK for IE, which doesn't support multiple classnames. If I
		// just change the styles, the activated class overrides the basic
		// activated class definition, thus I have to change what the
		// activated class name will be for the buttons in the toolbar.
		var b = this._shareToolbar.getButton(id);
		b._activatedClassName = b._className + "-" + DwtCssStyle.ACTIVATED;
		b._triggeredClassName = b._className + "-" + DwtCssStyle.TRIGGERED;

		this._shareToolbar.addSelectionListener(id, shareToolBarListener);
	}

	return this._shareToolbar;
}

ZmMailMsgView.prototype._shareToolBarListener =
function(ev) {
	ev._buttonId = ev.item.getData(ZmOperation.KEY_ID);
	ev._share = this._msg.share;
	this.notifyListeners(ZmMailMsgView.SHARE_EVENT, ev);
}

ZmMailMsgView.prototype.addShareListener =
function (listener) {
	this.addListener(ZmMailMsgView.SHARE_EVENT, listener);
}


ZmMailMsgView.prototype.set =
function(msg) {
	this.reset();
	var contentDiv = this.getHtmlElement();
	var oldMsg = this._msg;
	this._msg = msg;
	this._dateObjectHandlerDate = msg.sentDate ? new Date(msg.sentDate) : new Date(msg.date);
	if ((this._appCtxt.get(ZmSetting.CALENDAR_ENABLED)) && msg.isInvite() && msg.needsRsvp()) {
		var invite = msg.getInvite();
		// in the single component case, which I think is going to be 90%
		// of the time, we will just show a single toobar.
		if (!invite.hasMultipleComponents()) {
			// create toolbar
			var topToolbar = this._getInviteToolbar();
			// nuke the old toolbar if it exists b4 appending the new one
			var tEl = topToolbar.getHtmlElement();
			if (tEl && tEl.parentNode)
				tEl.parentNode.removeChild(tEl);
			contentDiv.appendChild(tEl);
		} else {
			// TODO:
			// here we want to show an arrow at the top which should drop down
			// to show all the components that could be replied to.
			// I think I want the toolbar at the top, to be applied to the
			// selected component.
			// We need an inviteComponentView. Ughhh.
		}
	}
	else if (msg.share && msg.share.action == ZmShareInfo.NEW && msg.folderId != ZmFolder.ID_TRASH) {
		// Note: Even if the share message is cc'd to someone else, the
		//		 accept/decline buttons are only seen by the grantee.
		if (msg.share.grantee.id == this._appCtxt.get(ZmSetting.USERID)) {
			var topToolbar = this._getShareToolbar();
			var tEl = topToolbar.getHtmlElement();
			if (tEl && tEl.parentNode) {
				tEl.parentNode.removeChild(tEl);
			}
			contentDiv.appendChild(tEl);
		}
	}
	var respCallback = new AjxCallback(this, this._handleResponseSet, [msg, oldMsg]);
	this._renderMessage(msg, contentDiv, respCallback);
};

ZmMailMsgView.prototype._handleResponseSet =
function(args) {
	var msg		= args[0];
	var oldMsg	= args[1][0];
	if (this._mode == ZmController.MSG_VIEW) {
		this._setTags(msg);
		// Remove listener for current msg if it exists
		if (oldMsg != null)
			oldMsg.removeChangeListener(this._changeListener);
		msg.addChangeListener(this._changeListener);
	} else if (this._mode == ZmController.TRAD_VIEW) {
		if (oldMsg != null)
			oldMsg.list.removeChangeListener(this._listChangeListener);
		msg.list.addChangeListener(new AjxListener(this, this._listChangeListener));
	}

	// reset scroll view to top most
	this.getHtmlElement().scrollTop = 0;
};

// This looks for anchor tags first, to exclude them, and all other tags later.
// ZmMailMsgView.htmlPreprocRegex = /(<[aA][^>]*>)([^<]*)(<\/[aA][^>]*>)|(<[^>]*>)([^<]*)|([^<>]+)/g;

/**
 * This function trys to filter out all text in between tags, and pass it
 * through the object geneneration methods. What this will not catch is html
 * looks like a string, but is a string with markup in between:
 * <i>http://www.</i><b>yahoo.com</b>
 * This function will grab http://www., and yahoo.com seperately, thus not
 * finding that it's an url.
 */
// Rewritten by mihai@zimbra.com below.
//
// ZmMailMsgView.prototype._preProcessHtml =
// function(html) {
// 	var results;
// 	var resultingHtml = new Array();
// 	var idx = 0;
// 	while ( (results = ZmMailMsgView.htmlPreprocRegex.exec(html)) != null ) {
// 		if (results[1] || results[2] || results[3]){
// 			// we've matched an anchor tag
// 			resultingHtml[idx++] = results[0];
// 		} else {
// 			if (results[5] && results[5] != "") {
// 				resultingHtml[idx++] = results[4];
// 				resultingHtml[idx++] = this._objectManager.findObjects(results[5], false);
// 				resultingHtml[idx++] = results[6];
// 			} else {
// 				resultingHtml[idx++] = results[0];
// 			}
// 		}
// 	}
// 	return resultingHtml.join("");
// };

// Values in this hash MUST be null or RegExp.  If "null" is passed, then that
// CSS rule will be dropped regardless the value.  If a RegExp is passed, then
// the rule is removed only if its value matches the RegExp.  Useful for cases
// like "position", where we can safely support most values except "fixed".
ZmMailMsgView._dangerousCSS = {

// It' doesn't make too much sense to cleanup the style if we're using an IFRAME

// 	// clearly, we can't display background image-s :-(
// 	// in the future we should provide a way for end-users to see them on demand,
// 	// but at this time, ban.
// 	backgroundImage       : null,
// 	backgroundAttachment  : null,

// 	// position: fixed can cause real trouble with browsers that support it
// 	position              : /fixed/i,

// 	// negative margins can get an element out of the containing DIV.
// 	// let's ban them
// 	marginLeft            : /^-/,
// 	marginRight           : /^-/,
// 	marginTop             : /^-/,
// 	marginBottom          : /^-/,

// 	// all of the above being banned, zIndex could as well stay... but better not.
// 	zIndex                : null,

// 	// not sure this is good
// 	whiteSpace            : null

 };

// Dives recursively into the given DOM node.  Creates ObjectHandlers in text
// nodes and cleans the mess in element nodes.  Discards by default "script",
// "link", "object", "style", "applet" and "iframe" (most of them shouldn't
// even be here since (1) they belong in the <head> and (2) are discarded on
// the server-side, but we check, just in case..).
ZmMailMsgView.prototype._processHtmlDoc = function(doc) {
	// var T1 = new Date().getTime();
	var objectManager = this._objectManager,
		node = doc.body;

	// This inner function does the actual work.  BEWARE that it return-s
	// in various places, not only at the end.
	function recurse(node, handlers) {
		var tmp, i, val;
		switch (node.nodeType) {
		    case 1:	// ELEMENT_NODE
			node.normalize();
			tmp = node.tagName.toLowerCase();
			if (/^(img|a)$/.test(tmp)) {
				if (tmp == "a"
				    && (/^((https?|ftps?):\x2f\x2f.+)$/.test(node.href)
					|| /^mailto:(.+)$/.test(node.href))) {
					// tricky.
					tmp = doc.createElement("div");
					tmp.innerHTML = objectManager.findObjects(RegExp.$1);
					tmp = tmp.firstChild;
					// here, tmp is an object span, but it
					// contains the URL (href) instead of
					// the original link text.
					node.parentNode.insertBefore(tmp, node); // add it to DOM
					tmp.innerHTML = "";
					tmp.appendChild(node); // we have the original link now
					return tmp.nextSibling;	// move on
				}
				handlers = false;
			} else if (/^(script|link|object|iframe|applet)$/.test(tmp)) {
				tmp = node.nextSibling;
				node.parentNode.removeChild(node);
				return tmp;
			}
			// fix style
			// node.nowrap = "";
			// node.className = "";

			if (AjxEnv.isIE)
				// strips expression()-s, bwuahahaha!
				// granted, they get lost on the server-side anyway, but assuming some get through...
				// the line below exterminates them.
				node.style.cssText = node.style.cssText;

			// Clear dangerous rules.  FIXME: implement proper way
			// using removeAttribute (kind of difficult as it's
			// (expectedly) quite different in IE from *other*
			// browsers, so for now style.prop="" will do.)
			tmp = ZmMailMsgView._dangerousCSS;
			for (i in tmp) {
				val = tmp[i];
				if (!val || val.test(node.style[i]))
					node.style[i] = "";
			}
			for (i = node.firstChild; i; i = recurse(i, handlers));
			return node.nextSibling;

		    case 3:	// TEXT_NODE
		    case 4:	// CDATA_SECTION_NODE (just in case)
			// generate ObjectHandler-s
			if (handlers && /[^\s\xA0]/.test(node.data)) try {
				var a = null, b = null;
				if (/^[\s\xA0]+/.test(node.data)) {
					a = node;
					node = node.splitText(RegExp.lastMatch.length);
				}
				if (/[\s\xA0]+$/.test(node.data))
					b = node.splitText(node.data.length - RegExp.lastMatch.length);

				tmp = doc.createElement("div");
				tmp.innerHTML = objectManager.findObjects(node.data, true);

				if (a)
					tmp.insertBefore(a, tmp.firstChild);
				if (b)
					tmp.appendChild(b);

				a = node.parentNode;
				while (tmp.firstChild)
					a.insertBefore(tmp.firstChild, node);
				tmp = node.nextSibling;
				a.removeChild(node);
				return tmp;
			} catch(ex) {};
		}
		return node.nextSibling;
	};
	recurse(node, true);
	// alert((new Date().getTime() - T1)/1000);
};

ZmMailMsgView.prototype._fixMultipartRelatedImages =
function(msg, idoc, domain) {
	var images = idoc.getElementsByTagName("img");
	var num = 0;
	for (var i = 0; i < images.length; i++) {
		var dfsrc = images[i].getAttribute("dfsrc");
		if (dfsrc) {
			//DBG.println("images "+i+" id="+images[i].id);
			if (dfsrc.substring(0,4) == "cid:") {
				num++;
				var cid = "<" + dfsrc.substring(4) + ">";
				//DBG.printRaw(" cid = "+cid);
				var src = msg.getContentIdAttachUrl(cid,domain);
				if (src) {
					//DBG.printRaw(" src = "+src);
					images[i].src = src;
					images[i].dfsrc = src;
				}
			} else if (dfsrc.indexOf("//") == -1) { // check for content-location verison
				//DBG.printRaw(" cid = "+cid);
				var src = msg.getContentLocationAttachUrl(dfsrc,domain);
				if (src) {
					num++;
					//DBG.printRaw(" src = "+src);
					images[i].src = src;
					images[i].dfsrc = src;
				}
			}
		}
	}
	return (num == images.length);
};

ZmMailMsgView.prototype._createDisplayImageClickClosure =
function(msg, idoc, id, iframe) {
	var self = this;
	var func = function () {
		var images = idoc.getElementsByTagName("img");
		for (var i = 0; i < images.length; i++) {
			if (images[i].getAttribute("dfsrc")) {
				// If we just loop through the images, IE for some reason,
				// doesn't fetch the image. By launching them off in the
				// background we seem to kick IE's engine a bit.
				if (AjxEnv.isIE) {
					var act = new AjxTimedAction();
					act.method = ZmMailMsgView._swapIdAndSrc;
					act.params.add(images[i]);
					act.params.add(i);
					act.params.add(images.length);
					act.params.add(msg);
					act.params.add(idoc);
					AjxTimedAction.scheduleAction(act, 0);
				} else {
					images[i].src = images[i].getAttribute("dfsrc");
				}
			}
		}
		diEl = Dwt.getDomObj(document, id);
		diEl.style.display = "none";
		this._htmlBody = idoc.documentElement.innerHTML;
		ZmMailMsgView._resetIframeHeight([ self, iframe ]);
		msg.setHtmlContent(this._htmlBody);
	};
	return func;
};

ZmMailMsgView._swapIdAndSrc =
function (args) {
	var image = args[0];
	var i = args[1];
	var len = args[2];
	var msg = args[3];
	var idoc = args[4];
	image.src = image.getAttribute("dfsrc");
	if (i == len -1) {
		msg.setHtmlContent(idoc.documentElement.innerHTML);
	}
};

ZmMailMsgView._resetIframeHeight =
function(args) {
	var self = args[0];
	var iframe = args[1];
	var h = self.getH() - 1;
	function substract(el) {
		if (el) {
			if (typeof el == "string")
				el = self.getElementById(el);
			if (el)
				h -= Dwt.getSize(el, true).y;
		}
	};
	substract(self._hdrTableId);
	substract(self._displayImagesId);
	substract(self._highlightObjectsId);
	if (self._inviteToolbar)
		substract(self._inviteToolbar.getHtmlElement());
	iframe.style.height = h + "px";
};

ZmMailMsgView.prototype.highlightObjects = function() {
	// This turns out to work fine for both HTML and Text emails.  For
	// text, however, it's slower than if we were just calling findObjects
	// on the whole text content, but has the advantage that it doesn't
	// scroll the iframe to top.  If anyone thinks that hiliting objects in
	// big text messages is too slow, lemme know.  -mihai@zimbra.com
	var idoc = this.getElementById(this._iframeId).contentWindow.document;
	this._processHtmlDoc(idoc);
};

ZmMailMsgView.prototype._makeHighlightObjectsDiv = function() {
	var self = this;
	function func() {
		var div = self.getElementById(self._highlightObjectsId);
		div.innerHTML = ZmMsg.pleaseWaitHilitingObjects;
		setTimeout(function() {
			self.highlightObjects();
			div.style.display = "none";
			ZmMailMsgView._resetIframeHeight([ self, self.getElementById(self._iframeId) ]);
		}, 3);
		return false;
	};
	// avoid closure memory leaks
	(function() {
		self._highlightObjectsId = Dwt.getNextId();
		var div = self.getDocument().createElement("div");
		div.className = "DisplayImages";
		div.id = self._highlightObjectsId;
		div.innerHTML =
			[ "<table width='100%' cellspacing='0' cellpadding='0'><tr><td style='width:20px'>",
			  AjxImg.getImageHtml("Status") + "</td><td>",
			  ZmMsg.objectsNotDisplayed,
			  " <span style='font: inherit; color:blue; text-decoration:underline'>",
			  ZmMsg.hiliteObjects,
			  "</span></td></tr></table>" ].join("");
		self.getHtmlElement().appendChild(div);
		Dwt.setHandler(div, DwtEvent.ONCLICK, func);
	})();
};

ZmMailMsgView.prototype._makeIframeProxy =
function(container, html, isTextMsg) {
	var displayImages;
	if (!isTextMsg && /<img/i.test(html)) {
		displayImages = this.getDocument().createElement("div");
		displayImages.className = "DisplayImages";
		displayImages.id = this._displayImagesId;
		displayImages.innerHTML =
			[ "<table width='100%' cellspacing='0' cellpadding='0'><tr><td style='width:20px'>",
			  AjxImg.getImageHtml("Status") + "</td><td>",
			  ZmMsg.externalImages,
			  " <span style='font: inherit; color:blue; text-decoration:underline'>",
			  ZmMsg.displayExternalImages,
			  "</span></td></tr></table>" ].join("");
		container.appendChild(displayImages);
	}

	var callback = null;
	var msgSize = html.length / 1024;
	if (isTextMsg) {
		if (msgSize <= ZmMailMsgView.OBJ_SIZE_TEXT && this._objectManager)
			// better process objects directly rather than scanning the DOM afterwards.
			html = this._objectManager.findObjects(html, true);
		else {
			html = AjxStringUtil.convertToHtml(html);
			this._makeHighlightObjectsDiv();
		}
		html = html.replace(/^ /mg, "&nbsp;")
			.replace(/\t/g, "<pre style='display:inline;'>\t</pre>")
			.replace(/\n/g, "<br>");
	} else {
		html = html.replace(/<!--(.*?)-->/g, ""); // remove comments
		// html = html.replace(/<style>/, "<style type='text/css'>");
		// this callback will post-process the HTML after the IFRAME is created
		if (msgSize <= ZmMailMsgView.OBJ_SIZE_HTML)
			callback = new AjxCallback(this, this._processHtmlDoc);
		else
			this._makeHighlightObjectsDiv();
	}

	// pass essential styles to avoid padding/font flickering
	var inner_styles = [ ".MsgBody-text, .MsgBody-text * { font: 10pt monospace; }",
			     "body.MsgBody { padding: 10px; }",
			     ".MsgHeader .Object { white-space: nowrap; }",
			     ".Object a:link, .Object a:active, .Object a:visited { text-decoration: none; }",
			     ".Object a:hover { text-decoration: underline; }",
			     ".Object-activated { text-decoration:underline; }"
		].join(" ");
	var ifw = new DwtIframe(this, "MsgBody", true, html, inner_styles, false, "static", callback);
	this._iframeId = ifw.getIframe().id;

	var idoc = ifw.getDocument();

	// assign the right class name to the iframe body
	idoc.body.className = isTextMsg
		? "MsgBody MsgBody-text"
		: "MsgBody MsgBody-html";

	// import the object styles
	var head = idoc.getElementsByTagName("head")[0];
	var link = idoc.createElement("link");
	link.rel = "stylesheet";
	link.href = "/zimbra/js/zimbraMail/config/style/msgview.css";
	head.appendChild(link);

	ifw.getIframe().style.visibility = "";

	if (!isTextMsg) {
		this._htmlBody = idoc.body.innerHTML;

		// TODO: only call this if top-level is multipart/related?
		var didAllImages = this._fixMultipartRelatedImages(this._msg, idoc, this.getDocument().domain);

		// setup the click handler for the images
		if (displayImages) {
			if (didAllImages) {
				displayImages.style.display = "none";
			} else {
				var func = this._createDisplayImageClickClosure(this._msg, idoc, this._displayImagesId, ifw.getIframe());
				Dwt.setHandler(displayImages, DwtEvent.ONCLICK, func);
			}
		}
	}

	// set height of view according to height of iframe on timer
	var act = new AjxTimedAction();
	act.method = ZmMailMsgView._resetIframeHeight;
	act.params.add(this);
	act.params.add(ifw.getIframe());
	AjxTimedAction.scheduleAction(act, 5);
};

ZmMailMsgView.prototype.resetMsg =
function(newMsg) {
	// Remove listener for current msg if it exists
	if (this._msg != null)
		this._msg.removeChangeListener(this._changeListener);
	// don't want add change listener for new until shown
	this._msg = newMsg;
};

ZmMailMsgView.prototype.isDisplayingMsg =
function(msg) {
	return (this._msg == msg);
};

ZmMailMsgView.prototype.getMsg =
function() {
	return this._msg;
};

// Following two overrides are a hack to allow this view to pretend it's a list view
ZmMailMsgView.prototype.getSelection =
function() {
	return this._msg;
};

ZmMailMsgView.prototype.getSelectionCount =
function() {
	return 1;
};

ZmMailMsgView.prototype.getMinHeight =
function() {
	if (!this._headerHeight) {
		var headerObj = Dwt.getDomObj(this.getDocument(), ZmMailMsgView.HEADER_ID);
		this._headerHeight = headerObj ? Dwt.getSize(headerObj).y : 0;
	}
	return this._headerHeight;
};

ZmMailMsgView.prototype.getTitle =
function() {
	return [ZmMsg.zimbraTitle, ": ", this._msg.subject].join("");
};

// returns true if the current message was rendered in HTML
ZmMailMsgView.prototype.hasHtmlBody =
function() {
	return this._htmlBody != null;
};

// returns the html body element w/in the IFRAME's document for html messages
ZmMailMsgView.prototype.getHtmlBodyElement =
function() {
	var htmlBodyEl = null;

	if (this._htmlBody) {
		var iframe = Dwt.getDomObj(this.getDocument(), this._iframeId);
		var idoc = iframe ? Dwt.getIframeDoc(iframe) : null;
		htmlBodyEl = idoc ? idoc.body : null;
	}

	return htmlBodyEl;
};

// Private / Protected methods

ZmMailMsgView.prototype._addAddressHeaderHtml =
function(htmlArr, idx, addrs, prefix) {
	htmlArr[idx++] = "<tr><td class='LabelColName'>";
	htmlArr[idx++] = AjxStringUtil.htmlEncode(prefix);
	htmlArr[idx++] = ": </td><td class='LabelColValue'>";
	for (var i = 0; i < addrs.size(); i++) {
		if (i > 0)
			htmlArr[idx++] = AjxStringUtil.htmlEncode(ZmEmailAddress.SEPARATOR);

		var addr = addrs.get(i);
		if (this._objectManager && addr.address) {
			idx = this._objectManager.generateSpan(this._objectManager.getEmailHandler(), htmlArr, idx, addr, null);
		} else {
			htmlArr[idx++] = addr.address ? addr.address : (AjxStringUtil.htmlEncode(addr.name));
		}
	}
   	htmlArr[idx++] = "</td></tr>";

	return idx;
};

ZmMailMsgView.prototype._renderMessage =
function(msg, container, callback) {
	ZmDateObjectHandler.setCurrentDate(this._dateObjectHandlerDate);

	var idx = 0;
	var htmlArr = new Array();
	this._hdrTableId = Dwt.getNextId();
	htmlArr[idx++] = "<div id='" + ZmMailMsgView.HEADER_ID + "' class='MsgHeader'>";
	var w = AjxEnv.isIE ? "style='width:auto'" : "";
	htmlArr[idx++] = "<table id='" + this._hdrTableId + "' cellspacing=2 cellpadding=2 border=0 " + w + " >";

	// Date
	htmlArr[idx++] = "<tr><td class='LabelColName'>";
	htmlArr[idx++] = AjxStringUtil.htmlEncode(ZmMsg.sent);
	htmlArr[idx++] = ": </td><td>";
	htmlArr[idx++] = msg.sentDate ? (new Date(msg.sentDate)).toLocaleString() : "";
	htmlArr[idx++] = "</td></tr>";

	// From/To
	for (var i = 0; i < ZmMailMsg.ADDRS.length; i++) {
		var type = ZmMailMsg.ADDRS[i];
		// bug fix #3227 - dont bother filtering out BCC - server wont return any if they dont belong
		var addrs = msg.getAddresses(type);
		if (addrs.size() > 0) {
			var prefix = ZmMsg[ZmEmailAddress.TYPE_STRING[type]];
			idx = this._addAddressHeaderHtml(htmlArr, idx, addrs, prefix);
		}
	}

	// Subject
	var subject = msg.getSubject() || ZmMsg.noSubject;
	htmlArr[idx++] = "<tr><td class='LabelColName'>";
	htmlArr[idx++] = AjxStringUtil.htmlEncode(ZmMsg.subject);
	htmlArr[idx++] = ": </td><td class='LabelColValue'>";
	htmlArr[idx++] = this._objectManager ? this._objectManager.findObjects(subject, true) : subject;
	htmlArr[idx++] = "</td></tr>"

	// Attachments
	var attLinks = msg.buildAttachLinks(true, this.getDocument().domain);
	if (attLinks.length > 0) {
		htmlArr[idx++] = "<tr><td class='LabelColName'>";
		htmlArr[idx++] = ZmMsg.attachments;
		htmlArr[idx++] = ": </td><td class='LabelColValue'>";
		for (var i = 0; i<attLinks.length; i++)
			htmlArr[idx++] = attLinks[i].html;
		htmlArr[idx++] = "</td></tr>";
	}

	// Tags are handled in _setTags()

	htmlArr[idx++] = "</table></div>";
	var el = container ? container : this.getHtmlElement();
	el.appendChild(Dwt.parseHtmlFragment(htmlArr.join("")));

	var bodyPart = msg.getBodyPart();
	if (bodyPart) {
		if (bodyPart.ct == ZmMimeTable.TEXT_HTML && this._appCtxt.get(ZmSetting.VIEW_AS_HTML)) {
			this._makeIframeProxy(el, bodyPart.content, false);
		} else {
			// otherwise, get the text part if necessary
			if (bodyPart.ct != ZmMimeTable.TEXT_PLAIN) {
				// try to go retrieve the text part
				var respCallback = new AjxCallback(this, this._handleResponseRenderMessage, [el, bodyPart, callback]);
				msg.getTextPart(respCallback);
			} else {
				this._makeIframeProxy(el, bodyPart.content, true);
			}
		}
	}
};

ZmMailMsgView.prototype._handleResponseRenderMessage =
function(args) {
	var el			= args[0];
	var bodyPart	= args[1];
	var callback	= args[2];
	var result		= args[3];

	var content = result.getResponse();

	// if no text part, check if theres a calendar part and generate some canned 
	// text, otherwise, get the html part if one exists
	if (content == null) {
		if (bodyPart.ct == ZmMimeTable.TEXT_CAL)
			content = this._msg.isInvite() ? this._msg.getInvite().getCannedText() : null;
		else if (bodyPart.ct == ZmMimeTable.TEXT_HTML)
			content = bodyPart.content;
	}

	this._makeIframeProxy(el, (content || ""), true);
}

ZmMailMsgView.prototype._setTags =
function(msg) {
	if (!this._appCtxt.get(ZmSetting.TAGGING_ENABLED) || (this._mode != ZmController.MSG_VIEW)) return;

	var table = Dwt.getDomObj(this.getDocument(), this._hdrTableId);
	var tagRow = Dwt.getDomObj(this.getDocument(), this._tagRowId);
	var hadTags = (tagRow != null);
	var numTags = msg.tags.length;
	var hasTags = (numTags > 0);
	if (!hadTags && hasTags) {
		tagRow = table.insertRow(-1);
		tagRow.id = this._tagRowId;
		var tagCell = tagRow.insertCell(-1);
		tagCell.className = "LabelColName";
		tagCell.innerHTML = ZmMsg.tags + ": ";
		tagCell = tagRow.insertCell(-1);
		tagCell.className = "LabelColValue";
		tagCell.id = this._tagCellId;
	} else if (hadTags && !hasTags) {
		table.deleteRow(-1);
		return;
	} else if (!hasTags) {
		return;
	}

	// get sorted list of tags for this msg
	var ta = new Array();
	for (var i = 0; i < numTags; i++)
		ta[i] = this._tagList.getById(msg.tags[i]);
	ta.sort(ZmTag.sortCompare);

	if (numTags > 0) {
		var html = new Array();
		var idx = 0;
		for (var i = 0; i < numTags; i++) {
			var colorInfo = ZmTag.COLOR_MINI_ICON[ta[i].color];
			var txtWidth = Dwt.getHtmlExtent(ta[i].name).x;
			html[idx++] = "<table cellpadding=0 cellspacing=0 style='display:inline; width:";
			html[idx++] = txtWidth + colorInfo[1];
			html[idx++] = "'><tr><td style='width:";
			html[idx++] = colorInfo[1];
			html[idx++] = "'>";
			var fieldId = this._tagCellId + ZmDoublePaneView._TAG_IMG + ta[i].id;
			html[idx++] = AjxImg.getImageHtml(colorInfo, null, ["id='", fieldId, "'"].join(""), true);
			html[idx++] = "</td><td style='cursor:default;width:'";
			html[idx++] = txtWidth;
			html[idx++] = "'>"
			html[idx++] = AjxStringUtil.htmlEncode(ta[i].name);
			html[idx++] = "</td></tr></table>";
		}
	}
	var tagCell = Dwt.getDomObj(this.getDocument(), this._tagCellId);
	tagCell.innerHTML = html.join("");
};

ZmMailMsgView.prototype._msgChangeListener =
function(ev) {
	if (ev.type != ZmEvent.S_MSG)
		return;
	if (ev.event == ZmEvent.E_TAGS || ev.event == ZmEvent.E_REMOVE_ALL)
		this._setTags(this._msg);
};

ZmMailMsgView.prototype._listChangeListener =
function(ev) {
	// bug fix #3398 - check list size before nuking the msg view
	if (ev.source.size() == 0 && (ev.event == ZmEvent.E_DELETE || ev.event == ZmEvent.E_MOVE))
		this.reset();
};

ZmMailMsgView.prototype._mouseDownListener =
function(ev) {
	if (ev.button == DwtMouseEvent.LEFT) {
		// reset mouse event to propagate event to browser (allows text selection)
		ev._stopPropagation = false;
		ev._returnValue = true;
	}
};

ZmMailMsgView.prototype._selectStartListener =
function(ev) {
	// reset mouse event to propagate event to browser (allows text selection)
	ev._stopPropagation = false;
	ev._returnValue = true;
};

ZmMailMsgView.prototype._contextMenuListener =
function(ev) {
	// reset mouse event to propagate event to browser (allows context menu)
	ev._stopPropagation = false;
	ev._returnValue = true;
};

ZmMailMsgView.prototype.preventSelection =
function() {
	return false;
};

ZmMailMsgView.prototype.preventContextMenu =
function(target) {
	if (AjxEnv.isSafari) {
		// XXX: for some reason Safari is returning false on getSelection()
		//      even when something is selected w/in msg view. Just return false
		//      to allow copying text :(
		return false;
	} else {
		var bObjFound = target.id.indexOf("OBJ_") == 0;
		var bSelection = false;

		// determine if anything has been selected (IE and mozilla do it differently)
		if (this.getDocument().selection) { // IE
			bSelection = this.getDocument().selection.type == "Text";
		} else if (getSelection()) { 		// mozilla
			if (getSelection().toString().length)
				bSelection = true;
		}
		// if something has been selected and target is not a custom object,
		return bSelection && !bObjFound ? false : true;
	}
};

ZmMailMsgView.prototype._tagChangeListener =
function(ev) {
	if (ev.type != ZmEvent.S_TAG)
		return;

	var fields = ev.getDetail("fields");
	if (ev.event == ZmEvent.E_MODIFY && (fields && fields[ZmOrganizer.F_COLOR])) {
		var img = Dwt.getDomObj(this.getDocument(), this._tagCellId +  ZmDoublePaneView._TAG_IMG + ev.source.id);
		if (img)
			AjxImg.setImage(img, ZmTag.COLOR_MINI_ICON[ev.source.color]);
	}

	if (ev.event == ZmEvent.E_DELETE || ev.event == ZmEvent.MODIFY)
		this._setTags(this._msg);
};

ZmMailMsgView.getPrintHtml =
function(msg, preferHtml) {
	if (!(msg instanceof ZmMailMsg))
		return;

	if (!msg.isLoaded()) {
		var soapDoc = AjxSoapDoc.create("GetMsgRequest", "urn:zimbraMail", null);
		var msgNode = soapDoc.set("m");
		msgNode.setAttribute("id", msg.id);
		if (preferHtml)
			msgNode.setAttribute("html", "1");
		var command = new ZmCsfeCommand();
		var resp = command.invoke({soapDoc: soapDoc}).Body.GetMsgResponse;
		msg._loadFromDom(resp.m[0]);
	}

	var html = new Array();
	var idx = 0;

	html[idx++] = "<div style='width: 100%; background-color: #EEEEEE'>";
	html[idx++] = "<table border=0 width=100%><tr>";

	// print SUBJECT and DATE
	html[idx++] = "<td><font size=+1>" + msg.getSubject() + "</font></td>";
	html[idx++] = "<td align=right><font size=+1>";
	html[idx++] = msg.sentDate
		? (new Date(msg.sentDate)).toLocaleString()
		: (new Date(msg.date)).toLocaleString();
	html[idx++] = "</font></td>";
	html[idx++] = "</tr></table>";
	html[idx++] = "<table border=0 width=100%>";

	// print all address types
	for (var j = 0; j < ZmMailMsg.ADDRS.length; j++) {
		var addrs = msg.getAddresses(ZmMailMsg.ADDRS[j]);
		var len = addrs.size();
		if (len > 0) {
			html[idx++] = "<tr>";
			html[idx++] = "<td valign=top style='font-size: 14px'>";
			html[idx++] = ZmMsg[ZmEmailAddress.TYPE_STRING[ZmMailMsg.ADDRS[j]]];
			html[idx++] = ": </td><td width=100% style='font-size: 14px'>";
			for (var i = 0; i < len; i++) {
				html[idx++] = i > 0 ? AjxStringUtil.htmlEncode(ZmEmailAddress.SEPARATOR) : "";
				html[idx++] = addrs.get(i).address;
			}
			html[idx++] = "</td>";
			html[idx++] = "</tr>";
		}
	}
	html[idx++] = "</table>";
	html[idx++] = "</div>";

	// finally, print content
	var content = null;
	var bodyPart = msg.getBodyPart();
	if (bodyPart) {
		html[idx++] = "<div style='padding: 10px; font-size: 12px'>";
		if (bodyPart.ct == ZmMimeTable.TEXT_HTML && preferHtml) {
			// TODO - html should really sit in its own iframe but not so easy to do...
			html[idx++] = bodyPart.content;
		} else {
			content = bodyPart.ct != ZmMimeTable.TEXT_PLAIN
				? msg.getTextPart()
				: bodyPart.content;
			html[idx++] = "<span style='font-family: courier'>";
			html[idx++] = AjxStringUtil.nl2br(AjxStringUtil.htmlEncode(content, true));
			html[idx++] = "</span>";
		}
		html[idx++] = "</div>";
	}

	return html.join("");
};
