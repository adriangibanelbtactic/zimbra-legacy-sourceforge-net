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

// Initialize 
function Com_Zimbra_Photo() {
    this.nameToImage= {
	"Bill Evans" : "bill_evans.jpg",
	"Jimmy Smith" : "jimmy_smith.jpg",
	"Medeski" : "medeski.jpg",
	"Monk" : "monk.jpg",
	"Ray Charles" : "ray_charles.jpg"
    };
}

Com_Zimbra_Photo.prototype.init =
    function() {
	// Pre-load placeholder image
	(new Image()).src = this.getResource('blank_pixel.gif');
    };

Com_Zimbra_Photo.prototype = new ZmZimletBase();
Com_Zimbra_Photo.prototype.constructor = Com_Zimbra_Photo;

Com_Zimbra_Photo.prototype.match =
    function(line, startIndex) {
	var match;

	for (var name in this.nameToImage) {
	    var i = line.indexOf(name, startIndex);
	    // Skip if not found or this match isn't earlier
	    if (i < 0 || (match != null && i >= match.index)) {
		continue;
	    }

	    match = {index: i};
	    match[0] = name;
	}
	return match;
    };

Com_Zimbra_Photo.prototype.toolTipPoppedUp =
    function(spanElement, obj, context, canvas) {
	var image = this.nameToImage[obj];
	// alert("obj = '" + obj + "', image='" + image + "'");
	canvas.innerHTML = '<img src="' +
	this.getResource(image) +
	'"/>';
    };
