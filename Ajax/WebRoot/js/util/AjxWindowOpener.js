/*
 * Copyright (C) 2006, The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


function AjxWindowOpener() {
};


// consts used by frameOpenerHelper.jsp
AjxWindowOpener.HELPER_URL = "";
AjxWindowOpener.PARAM_INSTANCE_ID = "id";
AjxWindowOpener.PARAM_ASYNC = "async";


AjxWindowOpener.openBlank =
function(name, args, callback, async) {

	return AjxWindowOpener.open(AjxWindowOpener.HELPER_URL, name, args, callback, async);
};

AjxWindowOpener.open =
function(url, name, args, callback, async) {
	var newWin;
	if (url && url != "") {
		var async = async === true;
		var wrapper = { callback: callback };
		var id = AjxCore.assignId(wrapper);
		var localUrl = url && url != ""
			? (url + "?id=" + id + "&async=" + async)
			: "";
		newWin = wrapper.window = window.open(localUrl, name, args);
	} else {
		newWin = window.open("", name, args);
		if (callback) {
			var ta = new AjxTimedAction(callback.obj, callback);
			AjxTimedAction.scheduleAction(ta, 0);
		}
	}
	
	return newWin
};

AjxWindowOpener.onWindowOpened = 
function(wrapperId) {
	var wrapper = AjxCore.objectWithId(wrapperId);
	AjxCore.unassignId(wrapperId);

	if (!wrapper.window.closed && wrapper.callback) {
		wrapper.callback.run();
	}
};
