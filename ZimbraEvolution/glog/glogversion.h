/*
 * Copyright (C) 2006-2007 Zimbra, Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of version 2 of the GNU Lesser General Public
 * License as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 *
 */

#if !defined (__GLOG_H__)
#error "Only <glog/glog.h> can be included directly, this file may disappear or change contents."
#endif

#ifndef __GLOGVERSION_H__
#define __GLOGVERSION_H__

G_BEGIN_DECLS


/* put that here, so only one .in file is needed */
/**
 * GLOG_PTR_FORMAT:
 *
 * printf format type used to debug GStreamer types.
 * This can only be used on types whose size is >= sizeof(gpointer).
 */
#ifndef GLOG_DISABLE_PRINTF_EXTENSION
#define GLOG_PTR_FORMAT "P"
#else
#define GLOG_PTR_FORMAT "p"
#endif
/**
 * GLOG_VERSION_MAJOR:
 * 
 * Evaluates to the current major version of glog.
 * <note>
 * Use #GLOG_VERSION_MAJOR, #GLOG_VERSION_MINOR and #GLOG_VERSION_MACRO only 
 * when you want to know what glog version your stuff wascompiled against.
 * Use the glog_version() function if you want to know which version of 
 * glog you are currently linked against.
 * </note>
 */
#define GLOG_VERSION_MAJOR (0)
/**
 * GLOG_VERSION_MINOR:
 * 
 * Evaluates to the current minor version of glog.
 */
#define GLOG_VERSION_MINOR (5)
/**
 * GLOG_VERSION_MICRO:
 * 
 * Evaluates to the current micro version of glog.
 */
#define GLOG_VERSION_MICRO (0)

void    glog_version     (guint *major, guint *minor, guint *micro);


G_END_DECLS

#endif /* __GLOGVERSION_H__ */
