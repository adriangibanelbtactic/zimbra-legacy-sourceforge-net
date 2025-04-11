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
 * The Original Code is: Zimbra Collaboration Suite.
 *
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2005 Zimbra, Inc.
 * All Rights Reserved.
 *
 * Contributor(s):
 *
 * ***** END LICENSE BLOCK *****
 */

////////////////////////////////////////////////////////////////
///  Zimlet to handle integration with Snapfish              ///
///  @author Rajesh Segu, <rajesh.segu@zimbra.com>           ///
////////////////////////////////////////////////////////////////


/*TO-DO's:
 *  Change to SNAPFISH API V 2.0 - its configurable , so its easy to change
 *  
 * 
 * 
 * 
 */

function Com_Zimbra_Snapfish(){
}

/// Zimlet handler objects, such as Com_Zimbra_Snapfish, must inherit from
/// ZmZimletBase.  The 2 lines below achieve this.
Com_Zimbra_Snapfish.prototype = new ZmZimletBase();
Com_Zimbra_Snapfish.prototype.constructor = Com_Zimbra_Snapfish;

Com_Zimbra_Snapfish.LOGIN_SERVER = "http://www.sfus7.qa.snapfish.com/externalapi/v2";

Com_Zimbra_Snapfish.SNAPFISH ="SNAPFISH";

//Used for testing. To test just uncomment this and few lines in Com_Zimbra_Snapfish.prototype.login
//Com_Zimbra_Snapfish.USER = 'prakash.segu@gmail.com';
//Com_Zimbra_Snapfish.PWD = 'prakash';

Com_Zimbra_Snapfish.prototype.init = function() {
    
    this.SERVER = this.getConfig("loginServer");
	
    // We MUST use the enterprise URN in order for certain "advanced"
    // features such as adding a contact to an account
    this.XMLNS = "http://www.snapfish.com/externalapi";
	
	this.addButtonToComposerPage();
	
	//Uncomment this if you want "Save to Snapfish" links beside image attachments
	//this.addAttachmentHandler();
	
	this._login = false;
	
	window._snapfishCtxt = this;
};

//Snapfish: Add Attachment Handler to add Links
Com_Zimbra_Snapfish.prototype.addAttachmentHandler = function(){
	this._msgController = AjxDispatcher.run("GetMsgController");
	this._msgController._initializeListView(ZmController.MSG_VIEW);
	this._msgController._listView[ZmController.MSG_VIEW].addAttachmentLinkHandler(ZmMimeTable.IMG_JPEG,"snapfish",this.addSaveToSnapfishLink);
};

Com_Zimbra_Snapfish.prototype.addSaveToSnapfishLink = function(attachment){	
	var html = [
		"<a href='#' class='AttLink' style='text-decoration:underline;' onclick=\"Com_Zimbra_Snapfish.saveToSnapfishLinkClicked('",attachment.ct,"','",attachment.label,"','",attachment.url,"');\">",
		"Save to Snapfish",
		"</a>"
	].join("");
	return html;
};

Com_Zimbra_Snapfish.saveToSnapfishLinkClicked = function(contentType,imageLabel,imageUrl){
	var attachment = [{
		ct:contentType,
		url:imageUrl,
		label:imageLabel		
	}];
	window._snapfishCtxt.msgDropped({attlinks:attachment});
};


//Snapfish: Handle Snapfish Button to Compose View
Com_Zimbra_Snapfish.prototype.addButtonToComposerPage = function(){
	
	// Add the Snapfish Button to the Compose Page
	this._composerCtrl = AjxDispatcher.run("GetComposeController");
    //this._composerCtrl = this._appCtxt.getApp(ZmApp.MAIL).getComposeController();
    this._composerCtrl._snapfish = this;
    if(!this._composerCtrl._toolbar) {
      // initialize the compose controller's toolbar
      this._composerCtrl._initializeToolBar();
    }
    this._toolbar = this._composerCtrl._toolbar;
	
	if(!this._toolbar.getButton(Com_Zimbra_Snapfish.SNAPFISH)){
		// Add button to Compose Mail toolbar
		ZmMsg.snapfishAdd = "Comcast Photo Center";
		ZmMsg.snapfishTooltip = "Attach Image from Comcast Photo Center.";
		var op = {
			id:Com_Zimbra_Snapfish.SNAPFISH , 
			textKey: "snapfishAdd", 
			tooltipKey: "snapfishTooltip", 
			image: "SnapPanelIcon"
		};
		var opDesc = ZmOperation.defineOperation(null, op);
		ZmOperation.addOperation(this._toolbar, opDesc.id, this._toolbar._buttons, 1);
		//fetchSnapfish details defined later.
		this._toolbar.addSelectionListener(opDesc.id, new AjxListener(this._composerCtrl, this.fetchSnapfish));
	}
};

Com_Zimbra_Snapfish.prototype.fetchSnapfish = function(){
	
	if(!this._snapfish._login){	
		this._snapfish.login(this._snapfish.showAlbums);
		return;
	}
	
	this._snapfish.showAlbums();
};


/// Called by the Zimbra framework when some menu item that doesn't have an
/// <actionURL> was selected
Com_Zimbra_Snapfish.prototype.menuItemSelected = function(itemId) {
	switch (itemId) {
	    
	    case "LOGIN":
			this.login(this.showAlbums,true);
			break;
		
		case "GETALBUMS":
			this.showAlbums();
			break;
			
		case "REGISTER":		
			this.register();
			break;
	}
};

Com_Zimbra_Snapfish.prototype.singleClicked = function() {
	this.showAlbums();
};

Com_Zimbra_Snapfish.prototype.doubleClicked = function() {
  this.singleClicked(); 
};

//SnapFish:	Handle Mail Drop

Com_Zimbra_Snapfish.prototype.doDrop = function(obj) {
	
	switch (obj.TYPE) {
		
	    case "ZmMailMsg":
	    //case "ZmConv":   //Need to handle it in ZmZimletContext
	   		this.msgDropped(obj);
			break;
		
	    default:
			this.displayErrorMessage("You somehow managed to drop a \"" + obj.TYPE + "\" but however this Zimlet does't support it for drag'n'drop.");
			break;
	}
};

Com_Zimbra_Snapfish.prototype.msgDropped = function(mailMsg){
	
	if(!this._login){	
		this.login();
		return;
	}
	
	var attLinks = mailMsg.attlinks;	
	if(attLinks != null && attLinks.length != 0){
		var soap = this._makeEnvelope("e:GetAlbums");
		soap.set("authcode",this.authCode);
		soap.set("type","0");
		this.rpc(soap, new AjxCallback(this, this._showAttLinks, [ attLinks ]), true);
	}else{
		this.displayErrorMessage("<b>No attachment images found</b>");
	}
};

Com_Zimbra_Snapfish.prototype._showAttLinks = function(attLinks,result){

	var ans = this.xmlToObject(result);
	if (ans && ans.Body && ans.Body.GetAlbumsResponse) {
		ans = ans.Body.GetAlbumsResponse;
		var albums = ans.albums.album;
		
		var view = new DwtComposite(this.getShell());
		
		var albumSelectionPanel = new DwtPropertyEditor(view, false);
		var albumSelectionList = [];
		if(albums.length){
			var albumEnum = [];
			for(i=0;i<albums.length;i++){
				var albumEnumItem = {
					label:	String(albums[i].name),
					value:	String(albums[i].id)
				};
				albumEnum.push(albumEnumItem);
			}
			albumSelectionList = [
				{	label	:	"Album",
					type	:	"enum",
					name	:	"_album",
					value	:	String(albums[0].id),
					item	:	albumEnum
				}
			];	
		}else{
			albumSelectionList = [
				{ 	label	:	"Album",
					type	:	"string",
					name	:	String(albums.name),
					value	:	String(albums.name)
				},
				{	label	:	"AlbumId",
					type	:	"string",
					name	:	"_album",
					value	:	String(albums.id),
					visibile:	false
				}	
			];
		}
		
		albumSelectionPanel.initProperties(albumSelectionList);
		
		var el = albumSelectionPanel.getHtmlElement();
		
		var photoAttachmentsDiv = document.createElement("div");
		photoAttachmentsDiv.id = "photoAttachmentsDiv";
		photoAttachmentsDiv.className = "SnapContainer";
		
		var selectionBoxIds = [];
		var attachment,imageId,checkedId,adiv,div,imgdiv,input;

		for(i=0;i<attLinks.length;i++){
			attachment = attLinks[i];
			
			if(attachment.ct.indexOf("jpeg") == -1)
				continue;
		
			var imageId = Dwt.getNextId();
			var checkedId = Dwt.getNextId();
					
			div = document.createElement("div");
			div.className = "SnapPhoto";
		
			adiv = document.createElement("a");
			adiv.href = "#";
			adiv.onclick = AjxCallback.simpleClosure(this.showPictureInNewWindow, this, attachment.url,AjxStringUtil.htmlEncode(attachment.label));
			adiv.innerHTML = AjxStringUtil.htmlEncode(attachment.label);
		
			imgdiv = document.createElement("img");
			imgdiv.id = imageId;
			imgdiv.border = "0";
			imgdiv.width = 100;
			imgdiv.height = 100;
			imgdiv.alt = imgdiv.title = AjxStringUtil.htmlEncode(attachment.label);
			imgdiv.src = attachment.url;
		
			input = document.createElement("input");
			input.type = "checkbox";
			input.id = checkedId;
			input.value = imageId;
			input.name = "image";
			input.checked = true;
		
			div.appendChild(imgdiv);
			div.appendChild(document.createElement("br"));
			div.appendChild(input);
			div.appendChild(adiv);
			photoAttachmentsDiv.appendChild(div);
			
			selectionBoxIds.push(checkedId);				
		}
		
		if(selectionBoxIds.length == 0){
			photoAttachmentsDiv.innerHTML = "<center> <b>No Images found in the mail. </b></center>";
		}
		
		el.parentNode.appendChild(document.createElement("br"));
		el.parentNode.appendChild(photoAttachmentsDiv);
		el.parentNode.appendChild(document.createElement("br"));
		var photoAttachmentFooterDiv = document.createElement("div");
		photoAttachmentFooterDiv.id = "photoAttachmentFooterDiv";
		el.parentNode.appendChild(photoAttachmentFooterDiv);
		
		this._uploadButtonId = Dwt.getNextId();
		var attachButton = new DwtDialog_ButtonDescriptor(this._uploadButtonId, "Upload", DwtDialog.ALIGN_RIGHT);
	
		this._doneButtonId = Dwt.getNextId();
		var doneButton = new DwtDialog_ButtonDescriptor(this._doneButtonId, "Done", DwtDialog.ALIGN_RIGHT);
	
		var dlgArgs = {title : "Upload to Snapfish",view  : view , standardButtons : [],extraButtons : [attachButton,doneButton]};
		var dlg = this._createDialog(dlgArgs);
		dlg.popup();
		
		this._selectedAttchImageCount = 0;
		
		dlg.setButtonListener(this._uploadButtonId,
			      new AjxListener(this, function() {
			  		
			  		var album = albumSelectionPanel.getProperties();
			  		var albumId = album._album;
			  		
			  		 this._imageLinks = [];
			  		 this._imageLabels = [];
			  		 var src,caption,checkbox;
				     for(i=0;i<selectionBoxIds.length;i++){
				      	checkbox = document.getElementById(selectionBoxIds[i]);
				      	if(checkbox && checkbox.checked){
				      		var imageDiv = document.getElementById(checkbox.value);
				      		this._imageLinks.push(imageDiv.src);
				      		imageDiv.title? this._imageLabels.push(imageDiv.title):this._imageLabels.push("");
				      	}
				     }
					
					if(this._imageLinks.length == 0){
						this.displayErrorMessage("<b>Select images to upload to Snapfish.</b>");
						return;
					}
					
					this._imageLinksIndex = 0;
					this.addImage(this._handleRecursiveUploadToSnapfish,albumId,this._imageLabels[this._imageLinksIndex],this._imageLinks[this._imageLinksIndex]);
					this._imageLinksIndex++;
					
				     dlg.popdown();
				     dlg.dispose();
			      }));
		
		dlg.setButtonListener(
			this._doneButtonId,
			new AjxListener(this, function() {
				dlg.popdown();
				dlg.dispose();
			}));
		
	}else{
		var fault = "";
        if (ans && ans.Body && ans.Body.Fault && ans.Body.Fault.faultstring) {
            fault = ans.Body.Fault.faultstring + "<br />";
        }
        this.displayErrorMessage("<b>Snapfish Upload failed!</b><br />&nbsp;&nbsp;&nbsp;&nbsp;" + fault + "<br />Check your internet connection and review your preferences.");
	}
};


Com_Zimbra_Snapfish.prototype._handleRecursiveUploadToSnapfish = function(albumId,imageUrl,result){
	
	if(this._imageLinks.length == this._imageLinksIndex){
		this._imageLinks = [];
		this._imageLabels = [];
		this._imageLinksIndex = 0;
		this.showAlbumInfo(albumId);
		return;
	}
	this.addImage(this._handleRecursiveUploadToSnapfish,albumId,this._imageLabels[this._imageLinksIndex],this._imageLinks[this._imageLinksIndex++]);
};

//Snapfish: Display Albums 

Com_Zimbra_Snapfish.prototype.showAlbums = function(){
	
	if(!this._login){	
		this.login(this.showAlbums);
		return;
	}

	this.getAlbums(this._createHtmlForShowAlbums);
};

Com_Zimbra_Snapfish.prototype._createHtmlForShowAlbums = function(ans){
	
	this._createHtmlForSnapfish();
	this._populateAlbumsInSnapfishContainer(ans);
};

Com_Zimbra_Snapfish.prototype._createHtmlForSnapfish = function(/*ans*/){
	
	//var albums = ans.albums.album;
	
	if(this._snapfishDialog){
		this._snapfishDialog.popup();
		return;
	}
	
	if(this._snapfishView && this._snapfishView.getHtmlElement()) {
		this._snapfishView.getHtmlElement().innerHTML = "";
	}
	
	var view = new DwtComposite(this.getShell());
	this._snapfishView = view;
	
	var el = view.getHtmlElement();
	
	//Snapfish Header
	var html = [ 	"<div id='snapfish' class='SnapFish'>", 
						"<div class='SnapHead'>",
							"<div class='SnapAlbumName'>",
				  				"<span id='SnapTitle'>All Albums</span>", "<br>",
				  				"<span><strong><span  id='SnapInfo' class='SnapInfo'>Select an album below attach photos from Comcast Photo Center to your message</span></strong></span>",
				  				"<div id='SnapSelectAll' class='SnapSelectAll'><a href='#'><span></span></a></div>",
							"</div>",
						"</div>",
						"<br>",
						"<div id='SnapContainer' class='SnapContainer'>","</div>",
						"<br>",
						"<div id='SnapFooter' class='SnapFooter'>","</div>",
					"</div>"
				].join("");
	
	el.innerHTML = html;
	
	
	//Global references.
	this._snapfishAlbumNameDiv = document.getElementById("SnapTitle");
	this._snapfishAlbumInfoDiv = document.getElementById("SnapInfo");
	this._snapfishSelectAllDiv = document.getElementById("SnapSelectAll")
	this._snapfishContainerDiv = document.getElementById("SnapContainer");
	this._snapfishFooterDiv    = document.getElementById("SnapFooter");
	
	
	//Comcast PhotoCenter Dialog
	var dialogTitle = "Comcast Photo Center";
	this._attachButtonId = Dwt.getNextId();
	var attachButton = new DwtDialog_ButtonDescriptor(this._attachButtonId, "Attach", DwtDialog.ALIGN_RIGHT);
	
	this._doneButtonId = Dwt.getNextId();
	var doneButton = new DwtDialog_ButtonDescriptor(this._doneButtonId, "Done", DwtDialog.ALIGN_RIGHT);
	
	var dialogArgs = {title : dialogTitle,view  : view , standardButtons : [],extraButtons : [attachButton,doneButton]};
	var dlg = this._createDialog(dialogArgs);
	
	//Done Button Listner
	dlg.setButtonListener(
		this._doneButtonId,
		new AjxListener(this, function() {				
			///If its only static information then its just a simple OK button
			dlg.popdown();
			//dlg.dispose();
	}));
	
	//Attach Button Listner
	dlg.setButtonListener(
		this._attachButtonId,new AjxListener(this,function(){
			
			var imagesToAttach = [];
			var images = this._selectionBoxIds;
			for(i=0;i<images.length;i++){
				var image = document.getElementById(images[i].id);
				if(image.checked){
					imagesToAttach.push(images[i]);
				}
			}
			
			if(imagesToAttach.length == 0){
				this.displayErrorMessage("<b>Select atleast one image and then use Attach</b>");
				return;
			}
			
			//Open Compose View if not open
			if(this._composerCtrl._app._appViewMgr._currentView != ZmController.COMPOSE_VIEW){
				this._appCtxt.getApp(ZmApp.MAIL)._handleLoadNewMessage(false);
			}
			
			//Disabling the Attach Button
			this._snapfishDialog.setButtonEnabled(this._attachButtonId,false);
			
			//Display Progress Info
			this._snapfishFooterDiv.innerHTML = "Attaching "+ imagesToAttach.length +" photos";
			
			this._imagesToAttach = imagesToAttach;
			this._imagesToAttachIndex = 0;
			
			//Attaching the images to the mail recursively
			var image = this._imagesToAttach[this._imagesToAttachIndex];
			this._imagesToAttachIndex++;
			this._attachmentIdsList = [];
			this.attachImage(image.url,image.caption,this._handleAttachImagesToMail);
			
		}));
	
	//Global reference to Comcast Photo Center
	this._snapfishDialog = dlg;
	
	//Populate Albums
	//this._populateAlbumsInSnapfishContainer(ans);
	
	//Show Comcast Photo Center
	dlg.popup();	
	
};

//Snapfish: Attach Images to mail recursively
Com_Zimbra_Snapfish.prototype._handleAttachImagesToMail = function(attachmentId,imageURL,imageCaption){
	
	if(attachmentId) { 
		this._attachmentIdsList.push(attachmentId);
	}
	
	this._snapfishFooterDiv.innerHTML = attachmentId  
					?	(this._imagesToAttachIndex+1)+" of " +this._imagesToAttach.length+" photos attached"
					:	"Failed to attach image <b>"+imageCaption+"</b>";
	
	if(this._imagesToAttach.length == this._imagesToAttachIndex){
		
		//Save MSG as Draft
		this._composerCtrl = this._appCtxt.getApp(ZmApp.MAIL).getComposeController();
		var ajxCallback = new AjxCallback(this, this._composerCtrl._handleResponseSaveDraftListener);
		this._composerCtrl.sendMsg(this._attachmentIdsList.join(","),true,ajxCallback);
		
		
		this._snapfishFooterDiv.innerHTML = this._imagesToAttachIndex+" photos attached to the mail";
		this._snapfishDialog.setButtonEnabled(this._attachButtonId,true);
		
		this._clearImageSelection();
		
		return;
		
	}
	
	var image = this._imagesToAttach[this._imagesToAttachIndex];
	this._imagesToAttachIndex++;
	this.attachImage(image.url,image.caption,this._handleAttachImagesToMail);
	
};



Com_Zimbra_Snapfish.prototype._showSelectAll = function(enable,checkboxIds){
	
	if(typeof enable == "undefined"){
		enable = true;
	}
	
	if(this._snapfishSelectAllDiv){
		
		if(enable){
			this._snapfishSelectAllDiv.innerHTML = "";
			var adiv = document.createElement("a");
			adiv.href = "#";
			adiv.onclick =  AjxCallback.simpleClosure(this._handleSelectAll,this,checkboxIds);
			var span = document.createElement("span");
			span.innerHTML = "Select All"
			
			adiv.appendChild(span);
			this._snapfishSelectAllDiv.appendChild(adiv);
				
		}else{
			this._snapfishSelectAllDiv.innerHTML = "";
		}
	}
	
};

Com_Zimbra_Snapfish.prototype._handleSelectAll = function(checkboxIds){
	
	if(!checkboxIds){
		return;
	}
	
	var checkbox;
	
	for(i=0; i<checkboxIds.length ;i++){
		checkbox = document.getElementById(checkboxIds[i]);
		checkbox? checkbox.checked = true: "" ;
	}
	
	this._selectedImageCount = checkboxIds.length;
	
	if(this._snapfishFooterDiv){	
		var message = (this._selectedImageCount == 0)?"": (this._selectedImageCount+" photos selected");
		this._snapfishFooterDiv.innerHTML = message;
	}
	
};

Com_Zimbra_Snapfish.prototype._clearImageSelection = function(){
	var images = this._selectionBoxIds;
	for(i=0;i<images.length;i++){
		var image = document.getElementById(images[i].id);
		image.checked = false;
	}
	this._selectedImageCount = 0;
};

Com_Zimbra_Snapfish.prototype._populateAlbumsInSnapfishContainer = function(ans){
	
	var div,imgdiv,adiv, html = "";
	
	//Diable Attach Button
	this._snapfishDialog.setButtonEnabled(this._attachButtonId,false);
	
	//Clearing prior Content if any
	var container = this._snapfishContainerDiv;
	container.innerHTML = "";
	this._snapfishFooterDiv.innerHTML = "";
	
	//Setting the Albums Info
	this._snapfishAlbumNameDiv.innerHTML = "All Albums";
	this._snapfishAlbumInfoDiv.innerHTML = "Select an album below attach photos from Comcast Photo Center to your message";
	
	/* Sample Container Div
	<div class="SnapPhoto albumCover">
	<div>
	<img src="snap.jpg" border="0" alt="PhotoTitle" title="Photo Title">
	<br>
	<a href="#">Photo Tite #3</a>
	</div>
	</div>
	*/
	
	var albums = ans.albums.album;
	albums = albums.length?albums:[albums];
	for(i=0;i<albums.length;i++){
		div = document.createElement("div");
		div.id = String(albums[i].id);
		div.className = "SnapPhoto albumCover";
		
		adiv = document.createElement("a");
		adiv.href = "#";
		adiv.onclick = AjxCallback.simpleClosure(/*this._showAlbumInfoInSnapfishContainer*/this.showAlbumInfo, this, String(albums[i].id));
		adiv.innerHTML = albums[i].name;
		
		imgdiv = document.createElement("img");
		imgdiv.border = "0";
		imgdiv.alt = imgdiv.title = String(albums[i].name);
		imgdiv.src = albums[i].firsttnurl;
		
		div.appendChild(imgdiv);
		div.appendChild(document.createElement("br"));
		div.appendChild(adiv);
		container.appendChild(div);
	}
	
	this._showSelectAll(false);
};


Com_Zimbra_Snapfish.prototype.showAlbumInfo = function(albumId){	
	this.getAlbumInfo(albumId,this._createHtmlForShowAlbumInfo);
};

Com_Zimbra_Snapfish.prototype._createHtmlForShowAlbumInfo = function(ans){
	this._createHtmlForSnapfish();
	this._populateAlbumInfoInSnapfishContainer(ans);
};


Com_Zimbra_Snapfish.prototype._populateAlbumInfoInSnapfishContainer = function(ans){
	
	var div,imgdiv,adiv, span,input,html = "";
	
	//Enable Attach Button
	this._snapfishDialog.setButtonEnabled(this._attachButtonId,true);
	
	//Clearing prior Content if any
	var container = this._snapfishContainerDiv;
	container.innerHTML = "";
	this._snapfishFooterDiv.innerHTML = "";
	
	//<a href="#">All albums</a> > Album #1
	
	this._snapfishAlbumNameDiv.innerHTML = "";
	
	
	span = document.createElement("span");
	adiv = document.createElement("a");
	adiv.href="#";
	//this.getAlbums(this._createHtmlForShowAlbums);
	adiv.onclick = AjxCallback.simpleClosure(this.getAlbums,this,this._populateAlbumsInSnapfishContainer);
	adiv.innerHTML = "All Albums";
	span.appendChild(adiv);
	
	this._snapfishAlbumNameDiv.appendChild(span);
	
	span = document.createElement("span");
	span.innerHTML = " >> " + String(ans.name);
	this._snapfishAlbumNameDiv.appendChild(span);
	
	this._snapfishAlbumInfoDiv.innerHTML = "Select photos below and click 'Attach' to include them in your message";
	
	/*
	<div class="SnapPhoto">
	<img src="snap.jpg" border="0" alt="PhotoTitle" title="Photo Title">
	<input type="checkbox"checked>
	<a href="#">Photo Tite #1</a>
	</div>
	*/
	var selectionBoxIds = [];
	var selectAllBoxIds =[];
	var pictures = ans.pictures.picture;
	pictures = pictures.length?pictures:[pictures];
	
	for(i=0;i<pictures.length;i++){
	
		div = document.createElement("div");
		div.id = String(pictures[i].id)+"_div";
		div.className = "SnapPhoto";
		
		adiv = document.createElement("a");
		adiv.href = "#";
		adiv.onclick = AjxCallback.simpleClosure(this.showPictureInNewWindow, this, String(pictures[i].srurl),String(pictures.caption));
		adiv.innerHTML = pictures[i].caption;
		
		imgdiv = document.createElement("img");
		imgdiv.border = "0";
		imgdiv.alt = imgdiv.title = String(pictures[i].caption);
		imgdiv.src = pictures[i].tnurl;
		
		input = document.createElement("input");
		input.type = "checkbox";
		input.id = String(pictures[i].id);
		input.name = "image";
		input.onclick = AjxCallback.simpleClosure(this._updateImageSelectCount,this,input.id);
		
		div.appendChild(imgdiv);
		div.appendChild(document.createElement("br"));
		div.appendChild(input);
		div.appendChild(adiv);
		container.appendChild(div);
		
		selectionBoxIds.push({id:String(pictures[i].id),url:String(pictures[i].srurl),caption:String(pictures[i].caption)});
		selectAllBoxIds.push(String(pictures[i].id));
	}
	
	this._selectionBoxIds = selectionBoxIds;
	
	this._selectedImageCount = 0;
	
	this._showSelectAll(true,selectAllBoxIds);
};

Com_Zimbra_Snapfish.prototype._updateImageSelectCount = function(selected){
	
	var imageSelectBox = document.getElementById(selected);
	
	var newImageCount = (imageSelectBox && imageSelectBox.checked)? ++this._selectedImageCount : --this._selectedImageCount;
	
	var message = (newImageCount == 0)?"": (newImageCount+" photos selected");
	
	this._snapfishFooterDiv.innerHTML = message;
	
};



Com_Zimbra_Snapfish.prototype.showPictureInNewWindow = function(imageUrl,imageName){
	if(!imageName){
		imageName = "Image";
	}
	window.open(imageUrl,"Snapfish:"+imageName,"height=420,width=420,toolbar=no,scrollbars=yes,menubar=no");
};

//--------------------------------------------- BASIC FRAMEWORK --------------------------------//

//Snapfish: Attach Image to Zimbra
Com_Zimbra_Snapfish.prototype.attachImage = function(imageURL,imageCaption,callback){
	
	if(!callback){
		callback = false;
	}
	
	var reqParams = [
			"upload=1","&",
			"fmt=raw","&",
			"filename=",imageCaption
	].join("");
	
	var serverURL = ZmZimletBase.PROXY + AjxStringUtil.urlComponentEncode(imageURL) + "&" + reqParams;
	
	var ajxCallback = new AjxCallback(this,this.done_attachImage,[callback,imageURL,imageCaption]);
	AjxRpc.invoke(reqParams,serverURL,null,ajxCallback,true);
	
};

Com_Zimbra_Snapfish.prototype.done_attachImage = function(callback,imageURL,imageCaption,result){

	var uploadResponse = result.text;
	
	if(!uploadResponse){
		this.displayErrorMessage("Filed to add the image <b>"+imageCaption+"</b> to this mail");
		callback.call(this,null,imageURL,imageCaption);
		return;
	}
	var response = AjxStringUtil.split(uploadResponse,',');
	if(response.length <= 2){
		this.displayErrorMessage("Filed to add the image <b>"+imageCaption+"</b> to this mail");
		callback.call(this,null,imageURL,imageCaption);
		return;
	}
	
	var len = response[2].length;
	
	var attachmentId = response[2];
	//Fix: In my local I got response attachmentId as "'23234.....2333'\r\n" and thus the fix
	if(attachmentId.indexOf("\r") != -1){
		attachmentId = attachmentId.substring(1,len-3);
	}else{
		attachmentId = attachmentId.substring(1,len-2);
	}
	
	if(callback){		
		callback.call(this,attachmentId,imageURL,imageCaption);	
	}
};

///Snapfish: Add Image to Snapfish
//xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

Com_Zimbra_Snapfish.prototype.addAlbum = function(callback,albumName,imageCaption,imageUrl){
	
	this._albumName = albumName;
	if(imageUrl){
		this.uploadImageFromURL(callback,null,imageCaption,imageUrl);
	}else{
		this.uploadImageFromLocal(callback,null,imageCaption);
	}
};

Com_Zimbra_Snapfish.prototype.addImage = function(callback,albumId,imageCaption,imageURL){
	if(imageURL){
		this.uploadImageFromURL(callback,albumId,imageCaption,imageURL);
	}else{
		this.uploadImageFromLocal(callback,albumId,imageCaption);
	}
};

Com_Zimbra_Snapfish.prototype.uploadImageFromURL = function(callback,albumId,imageCaption,imageUrl){
	this.uploadImage(callback,albumId,imageCaption,imageUrl);
};

Com_Zimbra_Snapfish.prototype.uploadImageFromLocal = function(callback,albumId,imageCaption){
	this.uploadImage(callback,albumId,imageCaption);
};

Com_Zimbra_Snapfish.prototype.uploadImage = function(callback,albumId,imageCaption,imageUrl){
	
	var startUploadSessionURL = this.smartHost+"/startsession.suup";
	
	var reqParams = ["authcode=",this.authCode,"&ExpectedImages=1&Src=TST"];
	
	if(albumId){
		reqParams.push("&AlbumID="+albumId);
	}else{
		if(this._albumName && this._albumName != ""){
			reqParams.push("&AlbumCaption="+this._albumName);
		}else{
			reqParams.push("&AlbumCaption=Album_"+Dwt.getNextId());
		}
	}
	reqParams = reqParams.join("");
	
	var url = [startUploadSessionURL,"?",reqParams].join("");
	
	callback = new AjxCallback(this,this.startUploadImage,[callback,albumId,imageCaption,imageUrl]);
	
	this.sendRequest(reqParams, url , null , callback, false);
};

Com_Zimbra_Snapfish.prototype.startUploadImage = function(callback,albumId,imageCaption,imagePath,result){
	
	if(typeof imagePath == "undefined" || imagePath == null){
		imagePath = "";
	}
	
	var ans = this.xmlToObject(result);
	if (ans && ans.Body && ans.Body.StartSessionResults ) {
		ans = ans.Body.StartSessionResults.SessionParams;
		this.uploadSessionId = String(ans.SessionId);
		this.uploadAlbumId = String(ans.AlbumId);
		
		this.displayStatusMessage("Snapfish: Upload session initiated");
		//Start Upload
		var snapfishProxyURL = this.getResource('snapfish.jsp');
		var targetURL = [this.smartHost,"/uploadimage.suup"].join("");
		var mode="uploadLocal";
		if(imagePath.match(/((telnet:)|((https?|ftp|gopher|news|file):\/\/)|(www\.[\w\.\_\-]+))[^\s\xA0\(\)\&lt;\>\[\]\{\}\'\"]*/) != null){
			mode = "uploadURL";
			imagePath = AjxStringUtil.urlComponentEncode(imagePath);
		}
		//Handle UploadURL
		if("uploadURL" == mode){
		
			var reqParams = [	"mode=",mode,"&",
								"url=",targetURL,"&",
								"imagePath=",imagePath,"&",
								"Src=","TST","&",
								"authcode=",this.authCode,"&",
								"AlbumID=",this.uploadAlbumId ,"&",
								"SessionId=",this.uploadSessionId,"&",
								"SequenceNumber=","1"
						];
						
			if(imageCaption && imageCaption != ""){
				reqParams.push("&caption="+imageCaption);
			}
			
			reqParams = reqParams.join("");
			
			var url = [	snapfishProxyURL,"?",reqParams].join("");
		
			callback = new AjxCallback(this,this.done_uploadImage,[callback,albumId,imageCaption,imagePath]);
			AjxRpc.invoke(reqParams, url, null, callback, false);
		}else{		
			this._createUploadHtml(callback,albumId,imageCaption);
		}
	}else{
		var fault = "";
        if (ans && ans.Body && ans.Body.Fault && ans.Body.Fault.faultstring) {
            fault = ans.Body.Fault.faultstring + "<br />";
        }
        this.displayErrorMessage("<b>Snapfish Upload failed!</b><br />&nbsp;&nbsp;&nbsp;&nbsp;" + fault + "<br />Check your internet connection and review your preferences.");
	}
};

Com_Zimbra_Snapfish.prototype._createUploadHtml = function(callback,albumId,imageCaption) {
	
	var imagePath = "Local File System";
	
	var targetURL = [this.smartHost,"/uploadimage.suup"].join("");

	var reqParams = [	"mode=uploadLocal","&",
						"url=",targetURL,"&",
						"imagePath=",imagePath,"&",
						"Src=","TST","&",
						"authcode=",this.authCode,"&",
						"AlbumID=",this.uploadAlbumId,"&",
						"SessionId=",this.uploadSessionId,"&",
						"SequenceNumber=","1" 
						];
	
	if(imageCaption && imageCaption != ""){
		reqParams.push("&caption="+imageCaption);
	}
			
	reqParams = reqParams.join("");
	
	var snapfishProxyURL = this.getResource('snapfish.jsp') + "?" + reqParams;
	
	var container = document.createElement("DIV");
	container.style.marginLeft = "1em";
	container.style.marginBottom = "0.5em";
	
	var formId = Dwt.getNextId();
	
	container.innerHTML = [
			"<form enctype='multipart/form-data' target='",this.getUploadFrameId(),"' id='",formId,"' method='POST' action='",snapfishProxyURL,"' >",
			"<table cellspacing='4' cellpadding='0' border='0'>",
			"<tr><td>","<input name='localfile' type='file' size='30'>","</td></tr>",
			"</table>",	
			"</form>"
	].join("");
	
	var view = new DwtComposite(this.getShell());
	
	var element = view.getHtmlElement();
	element.appendChild(container);
	
	var dialogTitle = 'Upload to Snapfish';
	var dialogArgs = {title : dialogTitle,view  : view };
	var dlg = this._createDialog(dialogArgs);
	
	dlg.setButtonListener(
			DwtDialog.CANCEL_BUTTON,
			new AjxListener(this, function() {
				dlg.popdown();
				dlg.dispose();
			}));
	
	dlg.setButtonListener(
		DwtDialog.OK_BUTTON,
		new AjxListener(this, function() {
		
		this._uploadForm = document.getElementById(formId);	
		window._snapfish = AjxCallback.simpleClosure(this.done_uploadImage,this,callback,albumId,imageCaption,imagePath);		

		this._uploadForm.submit();

		dlg.popdown();
		dlg.dispose();
		}));			
	dlg.popup();
};

Com_Zimbra_Snapfish.prototype.done_uploadImage = function(callback,albumId,imageCaption,imagePath,result){
	
	//alert(document.getElementById(this.getUploadFrameId()).contentWindow.document.documentElement.firstChild.childNodes[1].innerHTML);
	
	if(callback == null){
		callback = false;
	}
	var ans,images,success=false;
	var fault = "";

    if(typeof result == "string"){
        DBG.println(AjxDebug.DBG3, result);
        ans = AjxXmlDoc.createFromXml(result).toJSObject(true, false);
		if(ans && ans.body && ans.body.uploadimageresults){
			ans = ans.body.uploadimageresults.sessionparams;
			images = ans.successfulimagenames.name;
			success = true;
		}else{
			if (ans && ans.body && ans.body.fault && ans.body.fault.faultstring) {
            	fault = ans.body.fault.faultstring + "<br />";
        	}
		}
	}else{
		ans = this.xmlToObject(result);
		
		if (ans && ans.Body && ans.Body.UploadImageResults) {
			ans = ans.Body.UploadImageResults.SessionParams;
			images = ans.SuccessfulImageNames.Name;
			DBG.dumpObj(images);
			success = true;
		}else{
			if (ans && ans.Body && ans.Body.Fault && ans.Body.Fault.faultstring) {
            	fault = ans.Body.Fault.faultstring + "<br />";
        	}
		}
	}
	DBG.dumpObj(AjxDebug.DBG3, ans);
	if(callback && success){
		callback.call(this,albumId,imagePath,images);
	}
	
	if(!success){   
		if(fault == ""){
			fault = " Server Exception during file upload ";
		}
        this.displayErrorMessage("<b>Snapfish Upload failed!</b><br />&nbsp;&nbsp;&nbsp;&nbsp;" + fault + "<br />Check your internet connection and review your preferences.");
	}
};

///xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

///Snapfish: Get Albums

Com_Zimbra_Snapfish.prototype.getAlbums = function(callback){
	var soap = this._makeEnvelope("e:GetAlbums");
	soap.set("authcode",this.authCode);
	soap.set("type","0");
	if (callback == null)
		callback = false;
	this.rpc(soap, new AjxCallback(this, this.done_getAlbums, [ callback ]), true);
};

Com_Zimbra_Snapfish.prototype.done_getAlbums = function(callback,result){
	
	var ans = this.xmlToObject(result);
	if (ans && ans.Body && ans.Body.GetAlbumsResponse) {
		ans = ans.Body.GetAlbumsResponse;

		
	if (callback){
		callback.call(this,ans);
	}
		
	this.displayStatusMessage("SnapFish: Got Albums Info.");
	
    } else {
        var fault = "";
        if (ans && ans.Body && ans.Body.Fault && ans.Body.Fault.faultstring) {
            fault = ans.Body.Fault.faultstring + "<br />";
        }
        this.displayErrorMessage("<b>Snapfish: Fetching albums failed!</b><br />&nbsp;&nbsp;&nbsp;&nbsp;" + fault + "<br />Check your internet connection and review your preferences.");
    }
};



///Snapfish: Get Album Info

Com_Zimbra_Snapfish.prototype.getAlbumInfo = function(albumID,callback){
	var soap = this._makeEnvelope("e:GetAlbumInfo");
	soap.set("authcode",this.authCode);
	soap.set("AlbumId",albumID);
	if (callback == null)
		callback = false;
	this.rpc(soap, new AjxCallback(this, this.done_getAlbumInfo, [ callback ]), true);
};

Com_Zimbra_Snapfish.prototype.done_getAlbumInfo = function(callback,result){
	var ans = this.xmlToObject(result);
	if (ans && ans.Body && ans.Body.GetAlbumInfoResponse) {
		ans = ans.Body.GetAlbumInfoResponse;
		
		if(callback){
			callback.call(this,ans);
		}
		this.displayStatusMessage("SnapFish: "+ans.name+" Album fetched.");
		
	}else {
        var fault = "";
        if (ans && ans.Body && ans.Body.Fault && ans.Body.Fault.faultstring) {
            fault = ans.Body.Fault.faultstring + "<br />";
        }
        this.displayErrorMessage("<b>Snapfish Get Album Info failed!</b><br />&nbsp;&nbsp;&nbsp;&nbsp;" + fault + "<br />Check your internet connection and review your preferences.");
    }
};


//Snapfish: Get User Info

Com_Zimbra_Snapfish.prototype.getUserInfo = function(callback){
	var soap = this._makeEnvelope("e:GetUserInfo");
	soap.set("authcode", this.authCode);
	this.rpc(soap, new AjxCallback(this, this.done_getUserInfo, [ callback ]), true);
};

Com_Zimbra_Snapfish.prototype.done_getUserInfo = function(callback,result){
	
	if (!callback) {
		callback = false;
    }
	
	var ans = this.xmlToObject(result);
	if (ans && ans.Body && ans.Body.UserInfoResponse) {
		
		ans = ans.Body.UserInfoResponse;
		
		this.userName = ans.userInfo.email;
		this.fName    = ans.userInfo.fname;
		this.lName	  = ans.userInfo.lname;
		
		if(callback)
			callback.call(this);
	}else{
		var fault = "";
        if (ans && ans.Body && ans.Body.Fault && ans.Body.Fault.faultstring) {
            fault = ans.Body.Fault.faultstring + "<br />";
        }
        this.displayErrorMessage("<b>Snapfish login failed!</b><br />&nbsp;&nbsp;&nbsp;&nbsp;" + fault + "<br />Check your internet connection and review your preferences.");
	}
	
};

// Snapfish: Login

/// Login to Snapfish.  The given callback will be called in the case of a
/// successful login.  Note that callback is a plain function (not AjxCallback)

Com_Zimbra_Snapfish.prototype.login = function(callback,force) {
	
	if(!force){
		force = false;
	}
	
    //Uncomment for testing purpose. Need not register everytime.
    //this.setUserProperty("username",Com_Zimbra_Snapfish.USER);
	//this.setUserProperty("password",Com_Zimbra_Snapfish.PWD);
    var user = this.getUserProperty("username");
	var pwd = this.getUserProperty("password");
	if (!user || user == "" || !pwd || pwd == "" || force ) {
		this.displayStatusMessage("Please fill your Snapfish Login credentials first");
		this.createPropertyEditor(new AjxCallback(this, this.login, [ callback ]));
	} else {
		var soap = this._makeEnvelope("e:Login");
		soap.set("subscriberid","1000000");
		soap.set("email", user);
		soap.set("password", pwd);
		this.rpc(soap, new AjxCallback(this, this.done_login, [ callback ]), true);
		//this._do_login(callback, user, passwd);
	}
};

Com_Zimbra_Snapfish.prototype.done_login = function(callback, result) {
	
	if (!callback) {
		callback = false;
    }
	
	var ans = this.xmlToObject(result);
	if (ans && ans.Body && ans.Body.LoginResponse) {
		ans = ans.Body.LoginResponse;
		this.authCode = String(ans.authcode);
		this.podHost = String(ans.podhost);
		this.adHost = String(ans.adhost);
		this.smartHost = String(ans.smarthost);
		this.priceVersion = String(ans.priceversion);
		this.displayStatusMessage("SnapFish: logged in.");
		this.userName = this.getUserProperty("username");
		
		this._login = true;
		
		if (callback)
			callback.call(this);
    } else {
        var fault = "";
        if (ans && ans.Body && ans.Body.Fault && ans.Body.Fault.faultstring) {
            fault = ans.Body.Fault.faultstring + "<br />";
        }
        this.setUserProperty("username","",true);
        this.setUserProperty("password","",true);
        //var user = this.getUserProperty("username");
		//var passwd = this.getUserProperty("password");
        this.displayErrorMessage("<b>Snapfish login failed!</b><br />&nbsp;&nbsp;&nbsp;&nbsp;" + fault + "<br />Check your internet connection and review your preferences.");
    }
};

//SOAP METHOD: register
///Just in case if SnapFish needs a registration process.
Com_Zimbra_Snapfish.prototype.register = function(callback){
	if(!callback){
		callback = false;
	}
	
	var view = new DwtComposite(this.getShell());
	var acctEditor = new DwtPropertyEditor(view, true);
	
	var acct_props = [

		{ label    : "Email Id",
		  name     : "email",
		  type     : "string",
		  value    : "",
		  required : true },

		{ label    : "Password",
		  name     : "pwd",
		  type     : "password",
		  value    : "",
		  required : true },
		  
		{ label    : "Password",
		  name     : "cpwd",
		  type     : "password",
		  value    : "",
		  required : true },  

		{ label    : "First Name",
		  name     : "fname",
		  type     : "string",
		  value    : "",
		  required : true },
		  
		{ label    : "Last Name",
		  name     : "lname",
		  type     : "string",
		  value    : "",
		  required : true } 
	];
	
	acctEditor.initProperties(acct_props);
	
	var dialogTitle = "Create Account in Snapfish";						
	var dialog_args = {title : dialogTitle,view  : view};
	var dlg = this._createDialog(dialog_args);
	acctEditor.setFixedLabelWidth();
 	acctEditor.setFixedFieldWidth();
	dlg.popup();
	
	dlg.setButtonListener(
		DwtDialog.OK_BUTTON,
		new AjxListener(this, function() {
			
			if(!acctEditor.validateData()){
				this.displayErrorMessage("<b>Please fill in all the required fields for registration.</b>");
				return;
			}
			
			var acct = acctEditor.getProperties();
			
			if(acct.pwd != acct.cpwd){
				this.displayErrorMessage("<b>Please enter the same passwords.</b>");
				return;
			}
			
			var soap = this._makeEnvelope("e:RegisterUser");
			soap.set("subscriberid","1000000");
			soap.set("cobrandid","1000");
			soap.set("deviceid","TST");
			var userData = {};
			userData["email"]= acct.email;
			userData["fname"] = acct.fname;
			userData["lname"] = acct.lname;
			userData["password"] = acct.pwd;
			soap.set("UserInfo",userData);
			
			this.setUserProperty("username",acct.email);
			this.setUserProperty("password",acct.pwd);
			this.rpc(soap, new AjxCallback(this, this.done_register, [ callback ]), true);
			
			dlg.popdown();
			dlg.dispose();
		}));
		
		dlg.setButtonListener(
			DwtDialog.CANCEL_BUTTON,
			new AjxListener(this, function() {
				dlg.popdown();
				dlg.dispose();
			}));
	
	
	///Create a Property Editor to Register for SnapFish
	/*var user = Com_Zimbra_Snapfish.USER;
	var password=Com_Zimbra_Snapfish.PWD;
	var fName = "Rajesh";
	var lName = "Segu";
	var soap = this._makeEnvelope("e:RegisterUser");
	soap.set("subscriberid","1000000");
	soap.set("cobrandid","1000");
	soap.set("deviceid","TST");
	var userData = {};
	userData["email"]= user;
	userData["fname"] = fName;
	userData["lname"] = lName;
	userData["password"] = password;
	soap.set("UserInfo",userData);
	
	this.setUserProperty("username",user);
	this.setUserProperty("password",password);
	this.rpc(soap, new AjxCallback(this, this.done_register, [ callback ]), true);*/
};

Com_Zimbra_Snapfish.prototype.done_register = function(callback,result){
	
	var ans = this.xmlToObject(result);
	if (ans && ans.Body && ans.Body.RegisterUserResponse) {
		ans = ans.Body.RegisterUserResponse;
		this.authCode = String(ans.authcode);
		this.podHost = String(ans.podhost);
		this.adHost = String(ans.adhost);
		this.smartHost = String(ans.smarthost);
		this.priceVersion = String(ans.priceversion);
		this.displayStatusMessage("SnapFish: logged in.");
		if (callback)
			callback.call(this);
    } else {
        var fault = "";
        if (ans && ans.Body && ans.Body.Fault && ans.Body.Fault.faultstring) {
            fault = ans.Body.Fault.faultstring + "<br />";
        }
        this.displayErrorMessage("<b>Snapfish registration failed!</b><br />&nbsp;&nbsp;&nbsp;&nbsp;" + fault + "<br />Check your internet connection and review your preferences.");
    }
};

///Snapfish: Utilities

Com_Zimbra_Snapfish.prototype.getUploadFrameId = function(){
	if(!this._uploadFrame){
		this.getUploadFrame();
	}
	return this._uploadFrameId;
};

Com_Zimbra_Snapfish.prototype.getUploadFrame = function() {
	if (!this._uploadFrame) {
		var iframeId = Dwt.getNextId();
		var html = [ "<iframe name='", iframeId, "' id='", iframeId,
			     "' src='", (AjxEnv.isIE && location.protocol == "https:") ? appContextPath+"/public/blank.html" : "javascript:\"\"",
			     "' style='position: absolute; top: 0; left: 0; visibility: hidden'></iframe>" ];
		var div = document.createElement("div");
		div.innerHTML = html.join("");
		document.body.appendChild(div.firstChild);
		this._uploadFrameId = iframeId;
		this._uploadFrame = document.getElementById(iframeId);
	}
	return this._uploadFrame;
};


Com_Zimbra_Snapfish.prototype.toString = function(){
	return "Com_Zimbra_Snapfish";
};

Com_Zimbra_Snapfish.prototype._makeEnvelope = function(method) {
	var soap = AjxSoapDoc.create(method, this.XMLNS,"e","http://schemas.xmlsoap.org/soap/envelope/");
	var envEl = soap.getDoc().firstChild;
	envEl.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
	envEl.setAttribute("xmlns:xsd", "http://www.w3.org/2001/XMLSchema");
	return soap;
};

Com_Zimbra_Snapfish.prototype.xmlToObject = function(result) {
    try {
        var xd = new AjxXmlDoc.createFromDom(result.xml).toJSObject(true, false);
    } catch(ex) {
        this.displayErrorMessage(ex, result.text, "Problem contacting Snapfish");
    }
    return xd;
};

/// Utility function that calls the SForce server with the given SOAP data
Com_Zimbra_Snapfish.prototype.rpc = function(soap, callback, passErrors) {
	this.sendRequest(soap, this.SERVER, {SOAPAction: "m", "Content-Type": "text/xml"}, callback, false, passErrors);
};
