/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1
 * 
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 ("License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.zimbra.com/license
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is: Zimbra Collaboration Suite Server.
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2006, 2007 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */
package org.jivesoftware.wildfire.update;

/**
 * An Update represents a component that needs to be updated. By component we can refer
 * to the Wildfire server itself or to any of the installed plugins.
 *
 * @author Gaston Dombiak
 */
public class Update {

    /**
     * Name of the component that is outdated. The name could be of the server
     * (i.e. "Wildfire") or of installed plugins.
     */
    private String componentName;
    /**
     * Latest version of the component that was found.
     */
    private String latestVersion;
    /**
     * URL from where the latest version of the component can be downloaded.
     */
    private String url;
    /**
     * Changelog URL of the latest version of the component.
     */
    private String changelog;

    /**
     * Flag that indicates if the plugin was downloaded. This flag only makes sense for
     * plugins since we currently do not support download new wildfire releases.
     */
    private boolean downloaded;

    public Update(String componentName, String latestVersion, String changelog, String url) {
        this.componentName = componentName;
        this.latestVersion = latestVersion;
        this.changelog = changelog;
        this.url = url;
    }

    /**
     * Returns the name of the component that is outdated. When the server is the
     * outdated component then a "Wildfire" will be returned. Otherwise, the name of
     * the outdated plugin is returned.
     *
     * @return the name of the component that is outdated.
     */
    public String getComponentName() {
        return componentName;
    }

    /**
     * Returns the latest version of the component that was found.
     *
     * @return the latest version of the component that was found.
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Returns the URL to the change log of the latest version of the component.
     *
     * @return the URL to the change log of the latest version of the component.
     */
    public String getChangelog() {
        return changelog;
    }

    /**
     * Returns the URL from where the latest version of the component can be downloaded.
     *
     * @return the URL from where the latest version of the component can be downloaded.
     */
    public String getURL() {
        return url;
    }

    /**
     * Returns true if the plugin was downloaded. Once a plugin has been downloaded
     * it may take a couple of seconds to be installed. This flag only makes sense for
     * plugins since we currently do not support download new wildfire releases.
     *
     * @return true if the plugin was downloaded.
     */
    public boolean isDownloaded() {
        return downloaded;
    }

    /**
     * Sets if the plugin was downloaded. Once a plugin has been downloaded
     * it may take a couple of seconds to be installed. This flag only makes sense for
     * plugins since we currently do not support download new wildfire releases.
     *
     * @param downloaded true if the plugin was downloaded.
     */
    public void setDownloaded(boolean downloaded) {
        this.downloaded = downloaded;
    }

    /**
     * Returns the hashCode for this update object.
     * @return hashCode
     */
    public int getHashCode(){
        return hashCode();
    }
}
