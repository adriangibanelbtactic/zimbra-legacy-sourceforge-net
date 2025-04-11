ZmImOverview = function(appCtxt, parent) {
	DwtComposite.call(this, parent, null, Dwt.ABSOLUTE_STYLE);

	this.setScrollStyle(DwtControl.SCROLL);

	this._appCtxt = appCtxt;
	this._groupItems = {};
	this._itemsById = {};
	// this._allItems = new AjxVector();
	this._actionMenuOps = {

		root : [ ZmOperation.NEW_ROSTER_ITEM,
			 ZmOperation.SEP, //-----------
			 ZmOperation.IM_GATEWAY_LOGIN,
			 ZmOperation.SEP, //-----------
			 ZmOperation.IM_TOGGLE_OFFLINE ],

		buddy : [ ZmOperation.IM_NEW_CHAT,
			  ZmOperation.SEP, //-----------
			  ZmOperation.EDIT_PROPS, ZmOperation.DELETE,
			  ZmOperation.SEP, //-----------
			  ZmOperation.IM_CREATE_CONTACT, ZmOperation.IM_ADD_TO_CONTACT, ZmOperation.IM_EDIT_CONTACT
			],

		assistant : [ ZmOperation.IM_NEW_CHAT ],

		group : [ ZmOperation.IM_NEW_GROUP_CHAT,
			  ZmOperation.SEP,
			  ZmOperation.NEW_ROSTER_ITEM ]

	};

	this._actionMenuListener = new AjxListener(this, this._actionMenuListener);
	this._actionMenuPopdownListener = new AjxListener(this, this._actionMenuPopdownListener);

	this._im_dragSrc = new DwtDragSource(Dwt.DND_DROP_MOVE);
	this._im_dragSrc.addDragListener(new AjxListener(this, this._dragListener));

	this._init();
};

ZmImOverview.prototype = new DwtComposite;
ZmImOverview.prototype.constructor = ZmImOverview;

ZmImOverview.prototype._dragListener = function(ev) {
	var data = ev.srcControl.getData("ZmImOverview.data");
	switch (ev.action) {
	    case DwtDragEvent.SET_DATA:
		if (data.buddy)
			ev.srcData = data.buddy;
		break;
	}
};

ZmImOverview.prototype._actionMenuListener = function(ev) {
	var operation = ev.item.getData(ZmOperation.KEY_ID);
	var ctrl = this._appCtxt.getApp("IM").getRosterTreeController();
	var listener = ctrl._listeners[operation];
	if (listener) {
		var data = this._actionedItem.getData("ZmImOverview.data");
		listener.handleEvent({ type   : data.type,
				       buddy  : data.buddy,
				       group  : data.group,
				       dwtObj : ev.dwtObj
				     });
	}
};

ZmImOverview.prototype._actionMenuPopdownListener = function() {
	if (this._actionedItem)
		this._actionedItem._setActioned(false);
};

ZmImOverview.prototype._getActionMenu = function(nodeType, buddy, group) {
	var ops = this._actionMenuOps[nodeType];
	if (ops) {
		var menu = ops._dwtControl;
		if (!menu) {
			menu = ops._dwtControl = new ZmActionMenu({ parent    : this,
								    menuItems : ops });
			for (var i = 0; i < menu.opList.length; ++i) {
				var item = menu.opList[i];
				menu.addSelectionListener(item, this._actionMenuListener);
			}

			menu.addPopdownListener(this._actionMenuPopdownListener);
		}
		if (nodeType == "buddy") {
			// update menu items depending on wether we do
			// or don't have an Abook contact associated
			// with this buddy
 			var contact = buddy.getContact();
 			menu.getOp(ZmOperation.IM_ADD_TO_CONTACT).setVisible(!contact);
 			menu.getOp(ZmOperation.IM_CREATE_CONTACT).setVisible(!contact);
 			menu.getOp(ZmOperation.IM_EDIT_CONTACT).setVisible(!!contact);
		}
		return menu;
	} else {
		console.log("ERROR: no such node type for _getActionMenu: %s", nodeType);
	}
};

ZmImOverview.prototype._treeSelectionListener = function(ev) {
	if (ev.detail != DwtTree.ITEM_ACTIONED &&
	    ev.detail != DwtTree.ITEM_SELECTED &&
	    ev.detail != DwtTree.ITEM_DBL_CLICKED)
		return;

	var data = ev.item.getData("ZmImOverview.data");
	var type = data.type;
	var group = data.group;
	var buddy = data.buddy;

	if (ev.detail == DwtTree.ITEM_ACTIONED) {
		var menu = this._getActionMenu(type, buddy, group);
		this._actionedItem = ev.item;
		menu.popup(0, ev.docX, ev.docY);
	} else if (ev.detail == DwtTree.ITEM_SELECTED && buddy) {
		var ctrl = this._appCtxt.getApp("IM").getChatListController();
		ctrl.selectChatForRosterItem(buddy);
	} else if (ev.detail == DwtTree.ITEM_DBL_CLICKED) {
		if (buddy) {
			var ctrl = this._appCtxt.getApp("IM").getChatListController();
			ctrl.chatWithRosterItem(buddy);
		} else if (group) {
			ev.item.setExpanded(!ev.item.getExpanded());
		}
	}
};

ZmImOverview.prototype._init = function() {

	if (ZmImOverview.FILTER_SEARCH) {
		// enable the search filter
		var div = this.getHtmlElement();
		var input = div.ownerDocument.createElement("input");
		this.__searchInputEl = input;
		input.autocomplete = "off";
		// input.type = "text"; // DwtSimpleInput-hint gets overriden if we specify type="text"
		input.style.width = "100%";
		input.className = "DwtSimpleInput";
		div.appendChild(input);
		input.onkeydown = AjxCallback.simpleClosure(ZmImOverview.FILTER_SEARCH.inputKeyPress, this);
		input.onfocus = AjxCallback.simpleClosure(ZmImOverview.FILTER_SEARCH.inputFocus, this);
		input.onblur = AjxCallback.simpleClosure(ZmImOverview.FILTER_SEARCH.inputBlur, this);
		input.onblur();
	}

	var roster = this._roster = AjxDispatcher.run("GetRoster");
	var buddyList = roster.getRosterItemList();

	var tree = new DwtTree(this);
	tree.addSelectionListener(new AjxListener(this, this._treeSelectionListener));

	// create the root item
	this._rootItem = new DwtTreeItem(tree, null, null, null, null, "overviewHeader");
	this._rootItem.setData("ZmImOverview.data", { type: "root" });
	this._rootItem.setText(ZmMsg.buddyList);

	// Zimbra Assistant buddy
	var assistant = new ZmAssistantBuddy(buddyList, this._appCtxt);
	this._createBuddy("assistant", assistant);

	var createBuddy = AjxCallback.simpleClosure(this._createBuddy, this, "buddy");

	// ZmRosterItemList might not be initially empty
	buddyList.getVector().foreach(createBuddy);

	buddyList.addItem(assistant, true, 0);

	buddyList.addChangeListener(new AjxListener(this, function(ev) {
		var buddies = AjxVector.fromArray(ev.getItems());
		var fields = ev.getDetail("fields");
		if (ev.event == ZmEvent.E_CREATE) {
			buddies.foreach(createBuddy);
		} else if (ev.event == ZmEvent.E_MODIFY) {
			buddies.foreach(AjxCallback.simpleClosure(this._modifyBuddy,
								  this, fields));
		} else if (ev.event == ZmEvent.E_REMOVE ||
			   ev.event == ZmEvent.E_DELETE) {
			buddies.foreach(this._removeBuddy, this);
		}
	}));
};

ZmImOverview.prototype._createBuddy = function(type, buddy) {
	var groups = buddy.getGroups();
	if (groups.length == 0) {
		groups = type == "buddy"
			? [ ZmMsg.buddies ] // default to "Buddies"
			: [ null ]; // add to root item for type == i.e. "assistant"
	}
	var label = buddy.getDisplayName();
	var icon = buddy.getPresence().getIcon();
	for (var i = 0; i < groups.length; ++i) {
		var parent = this.getGroupItem(groups[i]);
		var item = new DwtTreeItem(parent,
					   this.getSortIndex(label, parent),
					   label,
					   icon);
		item.setToolTipContent("-"); // force it to have a tooltip
		item.getToolTipContent = AjxCallback.simpleClosure(buddy.getToolTip, buddy);
		item.setData("ZmImOverview.data", { type: type, buddy: buddy });
		item.setDragSource(this._im_dragSrc);
		parent.setExpanded(true);
		var a = this._itemsById[buddy.getAddress()];
		if (!a)
			a = this._itemsById[buddy.getAddress()] = new AjxVector();
		a.add(item);
		// this._allItems.add(item);
	}
};

ZmImOverview.prototype._modifyBuddy = function(fields, buddy) {
	var items = this._itemsById[buddy.getAddress()];
	if (items) {
		items.foreach(function(item) {
			var doShow    = ZmRosterItem.F_PRESENCE	 in fields;
			var doUnread  = ZmRosterItem.F_UNREAD	 in fields;
			var doName    = ZmRosterItem.F_NAME	 in fields;
			var doTyping  = ZmRosterItem.F_TYPING    in fields;
			if (doShow) {
				item.setImage(buddy.getPresence().getIcon());
			}
			if (doUnread || doName) {
				var txt = buddy.getDisplayName();
				if (buddy.getUnread()) {
					txt += " (" + buddy.getUnread() + ")";
					txt = txt.bold();
				}
				item.setText(txt);
			}
			if (doTyping) {
				item.condClassName(fields[ZmRosterItem.F_TYPING], "ZmRosterItem-typing");
			}
		}, this);
	}
};

ZmImOverview.prototype._removeBuddy = function(buddy) {
	var items = this._itemsById[buddy.getAddress()];
	items.foreach("dispose");
	// this._allItems.remove(item);
};

ZmImOverview.prototype.getGroupItem = function(group) {
	if (!group)
		return this._rootItem;
	var g = this._groupItems[group];
	if (!g) {
		g = this._groupItems[group] = new DwtTreeItem(this._rootItem,
							      this.getSortIndex(group), // index
							      group, // text
							      "ImGroup" // image
							     );
		g.setToolTipContent("-");
		g.getToolTipContent = function() {
			var data = this.getData("ZmImOverview.data");
			return AjxMessageFormat.format(ZmMsg.imGroupItemTooltip, [ data.group, this.getItemCount() ]);
		};
		g.setData("ZmImOverview.data", { type: "group", group: group });
	}
	return g;
};

ZmImOverview.prototype.getSortIndex = function(label, root) {
	var type = "buddy";
	if (root == null) {
		type = "group";
		root = this._rootItem;
	}
	label = label.toLowerCase();
	var items = root.getItems();
	for (var i = 0; i < items.length; ++i) {
		var item = items[i];
		var data = item.getData("ZmImOverview.data");
		var txt = type == "buddy"
			? data.buddy.getDisplayName()
			: data.group;
		// txt can be null if type is "assistant"
		if (txt && txt.toLowerCase() > label)
			break;
	}
	return i;
};

ZmImOverview.prototype.addFilter = function(f) {
	if (!this.__filters)
		this.__filters = [];

	// don't add same filter twice
	for (var i = this.__filters.length; --i >= 0;)
		if (this.__filters[i] === f)
			this.__filters.splice(i, 1);

	this.__filters.push(f);
	this.applyFilters();
};

ZmImOverview.prototype.removeFilter = function(f) {
	if (!this.__filters)
		return;

	for (var i = this.__filters.length; --i >= 0;)
		if (this.__filters[i] === f)
			this.__filters.splice(i, 1);

	// this is needed even if the array is empty in order to
	// redisplay any hidden items
	this.applyFilters();

	if (this.__filters.length == 0) {
		// completely drop it so we don't spend useful
		// time in applyFilters if there are no filters
		this.__filters = null;
	}
};

ZmImOverview.prototype.applyFilters = function(items) {
	var filters = this.__filters;
	if (!filters)
		return;
	this._firstFilterItem = null;
	doItems = function(items) {
		var oneVisible = false;
		for (var j = items.length; --j >= 0;) {
			var item = items[j];
			var display = true;
			for (var k = filters.length; --k >= 0;) {
				var f = filters[k];
				if (f.call(this, item)) {
					display = false;
					break;
				}
			}
			if (!this._firstFilterItem && display)
				this._firstFilterItem = item;
			oneVisible = oneVisible || display;
			item.setVisible(display);
		}
		return oneVisible;
	};
	if (items) {
		doItems.call(this, items);
	} else {
		var root = this._rootItem;
		var groups = root.getItems();
		for (var i = groups.length; --i >= 0;) {
			var group = groups[i];
			var items = group.getItems();
			var oneVisible = doItems.call(this, items) || items.length == 0;
			group.setVisible(oneVisible);
			if (oneVisible)
				group.setExpanded(true);
		}
	}
};

ZmImOverview.FILTER_OFFLINE_BUDDIES = function(item) {
	var rti = item.getData("ZmImOverview.data").buddy;
	var presence = rti.getPresence();
	return presence.getShow() == ZmRosterPresence.SHOW_OFFLINE;
};

// comment this out if we want to disable the search input field
ZmImOverview.FILTER_SEARCH = {
	func : function(item) {
		var search = this.__searchInputEl.value.toLowerCase();
		var rti = item.getData("ZmImOverview.data").buddy;
		if (/^#/.test(search)) {
			// search address -- easy way to display only Y! buddies, for instance.
			return rti.getAddress().indexOf(search.substr(1)) < 0;
		} else {
			return rti.getDisplayName().toLowerCase().indexOf(search) < 0;
		}
	},

	_doKeyPress : function() {
		var search = this.__searchInputEl.value;
		if (!/\S/.test(search) || search == ZmMsg.search)
			this.removeFilter(ZmImOverview.FILTER_SEARCH.func);
		else
			this.addFilter(ZmImOverview.FILTER_SEARCH.func);
	},

	inputFocus : function() {
		Dwt.delClass(this.__searchInputEl, "DwtSimpleInput-hint", "DwtSimpleInput-focused");
		if (this.__searchInputEl.value == ZmMsg.search)
			this.__searchInputEl.value = "";
		else try {
			this.__searchInputEl.select();
		} catch(ex) {};
	},

	inputBlur : function() {
		Dwt.delClass(this.__searchInputEl, "DwtSimpleInput-focused", "DwtSimpleInput-hint");
		if (!/\S/.test(this.__searchInputEl.value))
			this.__searchInputEl.value = ZmMsg.search;
	},

	inputKeyPress : function(ev) {
		if (!ev)
			ev = window.event;

		if (this.__searchInputTimeout)
			clearTimeout(this.__searchInputTimeout);

		if (ev.keyCode == 27) {
			this.__searchInputEl.value = "";
			ZmImOverview.FILTER_SEARCH._doKeyPress.call(this);
			ZmImOverview.FILTER_SEARCH.inputBlur.call(this);
			this.__searchInputEl.blur();
		}

		if (ev.keyCode == 13) {
			// filter right now
			ZmImOverview.FILTER_SEARCH._doKeyPress.call(this);

			if (!/\S/.test(this.__searchInputEl.value))
				return;

			// initiate chat with the first item, if found
			if (this._firstFilterItem) {
				var rti = this._firstFilterItem.getData("ZmImOverview.data").buddy;
				var clc = this._appCtxt.getApp("IM").getChatListController();
				clc.chatWithRosterItem(rti);

				// and clear value to reset filters
				this.__searchInputEl.value = "";

				ZmImOverview.FILTER_SEARCH.inputBlur.call(this);
				this.__searchInputEl.blur();
			}
		}

		this.__searchInputTimeout = setTimeout(
			AjxCallback.simpleClosure(
				ZmImOverview.FILTER_SEARCH._doKeyPress, this
			), 500);
	}
};
