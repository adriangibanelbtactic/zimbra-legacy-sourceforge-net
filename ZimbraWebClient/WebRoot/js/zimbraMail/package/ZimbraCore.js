/*
 * Package: ZimbraCore
 * 
 * Supports: Operation of the web client
 * 
 * Loaded: During initial load of web client
 */
AjxPackage.require("zimbraMail.share.model.ZmEvent");
AjxPackage.require("zimbraMail.share.model.ZmModel");
AjxPackage.require("zimbraMail.share.model.ZmSetting");
AjxPackage.require("zimbraMail.core.ZmAppCtxt");
AjxPackage.require("zimbraMail.core.ZmOperation");
AjxPackage.require("zimbraMail.core.ZmMimeTable");

AjxPackage.require("zimbraMail.share.model.ZmObjectHandler");
AjxPackage.require("zimbraMail.share.model.ZmObjectManager");
AjxPackage.require("zimbraMail.share.model.ZmSettings");
AjxPackage.require("zimbraMail.share.model.ZmKeyMap");
AjxPackage.require("zimbraMail.share.model.ZmTimezone");
AjxPackage.require("zimbraMail.share.model.ZmItem");
AjxPackage.require("zimbraMail.share.model.ZmOrganizer");
AjxPackage.require("zimbraMail.share.model.ZmFolder");
AjxPackage.require("zimbraMail.share.model.ZmSearchFolder");
AjxPackage.require("zimbraMail.share.model.ZmAuthenticate");
AjxPackage.require("zimbraMail.share.model.ZmSearch");
AjxPackage.require("zimbraMail.share.model.ZmSearchResult");
AjxPackage.require("zimbraMail.share.model.ZmTag");
AjxPackage.require("zimbraMail.share.model.ZmTree");
AjxPackage.require("zimbraMail.share.model.ZmTagTree");
AjxPackage.require("zimbraMail.share.model.ZmFolderTree");
AjxPackage.require("zimbraMail.share.model.ZmInvite");
AjxPackage.require("zimbraMail.share.model.ZmList");
AjxPackage.require("zimbraMail.share.model.ZmAccount");
AjxPackage.require("zimbraMail.share.model.ZmZimbraAccount");

AjxPackage.require("zimbraMail.core.ZmApp");

AjxPackage.require("zimbraMail.share.view.ZmPopupMenu");
AjxPackage.require("zimbraMail.share.view.ZmActionMenu");
AjxPackage.require("zimbraMail.share.view.ZmToolBar");
AjxPackage.require("zimbraMail.share.view.ZmButtonToolBar");
AjxPackage.require("zimbraMail.share.view.ZmNavToolBar");
AjxPackage.require("zimbraMail.share.view.ZmSearchToolBar");
AjxPackage.require("zimbraMail.share.view.ZmAutocompleteListView");
AjxPackage.require("zimbraMail.share.view.ZmSplashScreen");
AjxPackage.require("zimbraMail.share.view.ZmTreeView");
AjxPackage.require("zimbraMail.share.view.ZmTagMenu");
AjxPackage.require("zimbraMail.share.view.ZmListView");
AjxPackage.require("zimbraMail.share.view.ZmChicletButton");
AjxPackage.require("zimbraMail.share.view.ZmAppChooser");
AjxPackage.require("zimbraMail.share.view.ZmCurrentAppToolBar");
AjxPackage.require("zimbraMail.share.view.ZmStatusView");
AjxPackage.require("zimbraMail.share.view.ZmOverview");

AjxPackage.require("zimbraMail.share.view.assistant.ZmAssistant");

AjxPackage.require("zimbraMail.share.view.htmlEditor.ZmHtmlEditor");

AjxPackage.require("zimbraMail.share.view.dialog.ZmDialog");
AjxPackage.require("zimbraMail.share.view.dialog.ZmLoginDialog");
AjxPackage.require("zimbraMail.share.view.dialog.ZmNewOrganizerDialog");
AjxPackage.require("zimbraMail.share.view.dialog.ZmNewFolderDialog");
AjxPackage.require("zimbraMail.share.view.dialog.ZmNewSearchDialog");
AjxPackage.require("zimbraMail.share.view.dialog.ZmNewTagDialog");
AjxPackage.require("zimbraMail.share.view.dialog.ZmFolderPropsDialog");
AjxPackage.require("zimbraMail.share.view.dialog.ZmQuickAddDialog");

AjxPackage.require("zimbraMail.share.controller.ZmController");
AjxPackage.require("zimbraMail.share.controller.ZmListController");
AjxPackage.require("zimbraMail.share.controller.ZmTreeController");
AjxPackage.require("zimbraMail.share.controller.ZmTagTreeController");
AjxPackage.require("zimbraMail.share.controller.ZmFolderTreeController");
AjxPackage.require("zimbraMail.share.controller.ZmSearchTreeController");
AjxPackage.require("zimbraMail.share.controller.ZmOverviewController");
AjxPackage.require("zimbraMail.share.controller.ZmSearchController");

AjxPackage.require("zimbraMail.core.ZmLogin");
AjxPackage.require("zimbraMail.core.ZmAppViewMgr");
AjxPackage.require("zimbraMail.core.ZmRequestMgr");
AjxPackage.require("zimbraMail.core.ZmZimbraMail");
AjxPackage.require("zimbraMail.core.ZmNewWindow");

AjxPackage.require("zimbraMail.prefs.ZmPreferencesApp");
AjxPackage.require("zimbraMail.portal.ZmPortalApp");
AjxPackage.require("zimbraMail.mail.ZmMailApp");
AjxPackage.require("zimbraMail.calendar.ZmCalendarApp");
AjxPackage.require("zimbraMail.tasks.ZmTasksApp");
AjxPackage.require("zimbraMail.abook.ZmContactsApp");
AjxPackage.require("zimbraMail.im.ZmImApp");
AjxPackage.require("zimbraMail.notebook.ZmNotebookApp");
AjxPackage.require("zimbraMail.briefcase.ZmBriefcaseApp");
AjxPackage.require("zimbraMail.voicemail.ZmVoiceApp");
AjxPackage.require("zimbraMail.mixed.ZmMixedApp");
