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
 * Portions created by Zimbra are Copyright (C) 2005, 2006 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s):
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.extension;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.collections.map.ListOrderedMap;

import com.zimbra.common.localconfig.LC;
import com.zimbra.cs.redolog.op.RedoableOp;
import com.zimbra.common.util.ZimbraLog;

public class ExtensionUtil {

    private static List sClassLoaders = new ArrayList();

    private static URL[] dirListToURLs(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        List<URL> urls = new ArrayList<URL>(files.length);
        for (int i = 0; i < files.length; i++) {
            try {
                URL url = files[i].toURL();
                urls.add(url);
                if (ZimbraLog.extensions.isDebugEnabled()) {
                    ZimbraLog.extensions.debug("adding url: " + url);
                }
            } catch (MalformedURLException mue) {
                ZimbraLog.extensions.warn("ExtensionsUtil: exception creating url for " + files[i], mue);
            }
        }
        return urls.toArray(new URL[0]);
    }

    private static ClassLoader sExtParentClassLoader;

    static {
        File extCommonDir = new File(LC.zimbra_extensions_common_directory.value());
        URL[] extCommonURLs = dirListToURLs(extCommonDir);
        if (extCommonURLs == null) {
            // No ext-common libraries are present.
            sExtParentClassLoader = ExtensionUtil.class.getClassLoader();
        } else {
            sExtParentClassLoader = new URLClassLoader(extCommonURLs, ExtensionUtil.class.getClassLoader());    
        }
    }

    public static synchronized void loadAll() {
        File extDir = new File(LC.zimbra_extensions_directory.value());
        if (extDir == null) {
            ZimbraLog.extensions.info(LC.zimbra_extensions_directory.key() + " is null, no extensions loaded");
            return;
        }
        ZimbraLog.extensions.info("Loading extensions from " + extDir.getPath());

        File[] extDirs = extDir.listFiles();
        if (extDirs == null) {
            return;
        }
        for (int i = 0; i < extDirs.length; i++) {
            if (!extDirs[i].isDirectory()) {
                ZimbraLog.extensions.warn("ignored non-directory in extensions directory: " + extDirs[i]);
                continue;
            }

            ZimbraExtensionClassLoader zcl = new ZimbraExtensionClassLoader(dirListToURLs(extDirs[i]), sExtParentClassLoader);
            if (!zcl.hasExtensions()) {
                ZimbraLog.extensions.warn("no " + ZimbraExtensionClassLoader.ZIMBRA_EXTENSION_CLASS + " found, ignored: " + extDirs[i]);
                continue;
            }

            sClassLoaders.add(zcl);
        }
    }

    private static ListOrderedMap sInitializedExtensions = new ListOrderedMap();

    public static synchronized void initAll() {
        ZimbraLog.extensions.info("Initializing extensions");
        for (Iterator clIter = sClassLoaders.iterator(); clIter.hasNext();) {
            ZimbraExtensionClassLoader zcl = (ZimbraExtensionClassLoader)clIter.next();
            List classes = zcl.getExtensionClassNames();
            for (Iterator nameIter = classes.iterator(); nameIter.hasNext();) {
                String name = (String)nameIter.next();
                Class clz;
                try {
                    clz = zcl.loadClass(name);
                    ZimbraExtension ext = (ZimbraExtension)clz.newInstance();
                    try {
                        ext.init();
                        RedoableOp.registerClassLoader(ext.getClass().getClassLoader());
                        String extName = ext.getName();
                        ZimbraLog.extensions.info("Initialized extension " + extName + ": " + name + "@" + zcl);
                        sInitializedExtensions.put(extName, ext);
                    } catch (Throwable t) { 
                        ZimbraLog.extensions.warn("exception in " + name + ".init()", t);
                    }
                } catch (InstantiationException e) {
                    ZimbraLog.extensions.warn("exception occurred initializing extension " + name, e);
                } catch (IllegalAccessException e) {
                    ZimbraLog.extensions.warn("exception occurred initializing extension " + name, e);
                } catch (ClassNotFoundException e) {
                    ZimbraLog.extensions.warn("exception occurred initializing extension " + name, e);
                }

            }
        }
    }

    public static synchronized void destroyAll() {
        ZimbraLog.extensions.info("Destroying extensions");
        List extNames = sInitializedExtensions.asList();
        for (ListIterator iter = extNames.listIterator(extNames.size());
        iter.hasPrevious();)
        {
            String extName = (String) iter.previous();
            ZimbraExtension ext = (ZimbraExtension) getExtension(extName);
            try {
                RedoableOp.deregisterClassLoader(ext.getClass().getClassLoader());
                ext.destroy();
                ZimbraLog.extensions.info("Destroyed extension " + extName + ": " + ext.getClass().getName() + "@" + ext.getClass().getClassLoader());
            } catch (Throwable t) {
                ZimbraLog.extensions.warn("exception in " + ext.getClass().getName() + ".destroy()", t);
            }
        }
        sInitializedExtensions.clear();
    }

    public static synchronized Class loadClass(String extensionName, String className) throws ClassNotFoundException {
        if (extensionName == null)
            return Class.forName(className);
        ZimbraExtension ext = (ZimbraExtension) sInitializedExtensions.get(extensionName);
        if (ext == null)
            throw new ClassNotFoundException("extension " + extensionName + " not found");
        ClassLoader loader = ext.getClass().getClassLoader();
        return loader.loadClass(className);
    }

    public static synchronized ZimbraExtension getExtension(String name) {
        return (ZimbraExtension) sInitializedExtensions.get(name);
    }
}
