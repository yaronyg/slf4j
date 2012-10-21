/*
 * Created 21.10.2009
 *
 * Copyright (c) 2009-2012 SLF4J.ORG
 *
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.slf4j.impl;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.slf4j.ILoggerFactory;

import android.os.Build;
import android.util.Log;

/**
 * An implementation of {@link ILoggerFactory} which always returns
 * {@link AndroidLogger} instances.
 *
 * @author Thorsten M&ouml;ler
 * @version $Rev:$; $Author:$; $Date:$
 */
public class AndroidLoggerFactory implements ILoggerFactory
{
	private final Map<String, AndroidLogger> loggerMap;

	static final int TAG_MAX_LENGTH = 23; // tag names cannot be longer than this value on Android
	                                      // platforms older than Android 2.1 (which is API level 7);
                                         // see also android/system/core/include/cutils/property.h
                                         // and android/frameworks/base/core/jni/android_util_Log.cpp

	public AndroidLoggerFactory()
	{
		loggerMap = new HashMap<String, AndroidLogger>();
	}

	/* @see org.slf4j.ILoggerFactory#getLogger(java.lang.String) */
	public AndroidLogger getLogger(final String name)
	{
		String tag = null;
		try
		{
			// Length limitation of tags abolished since Build.VERSION_CODES.ECLAIR_MR1 (2.1, API level 7).
			// However, the field Build.VERSION.SDK_INT as well as the class Build.VERSION_CODES exist
			// since API level 4, which is why we use reflection to avoid compiler errors on older platforms.
			Field sdkInt;
			if ((sdkInt = Build.VERSION.class.getField("SDK_INT")) != null && sdkInt.getInt(null) >= 7)
			{
				tag = name;
			}
		}
		catch (SecurityException e) { /* handled indirectly in finally block */ }
		catch (IllegalArgumentException e) { /* handled indirectly in finally block */ }
		catch (NoSuchFieldException e) { /* handled indirectly in finally block */ }
		catch (IllegalAccessException e) { /* handled indirectly in finally block */ }
		finally
		{
			if (tag == null) tag = forceValidName(name); // fix for bug #173
		}


		AndroidLogger slogger = null;
		// protect against concurrent access of the loggerMap
		synchronized (this)
		{
			slogger = loggerMap.get(tag);
			if (slogger == null)
			{
				if (!tag.equals(name) && Log.isLoggable(AndroidLoggerFactory.class.getSimpleName(), Log.INFO))
				{
					Log.i(AndroidLoggerFactory.class.getSimpleName(),
						"SLF4J Logger name '" + name + "' exceeds maximum length of " + TAG_MAX_LENGTH +
						" characters, using '" + tag + "' as the Android Log tag instead.");
				}

				slogger = new AndroidLogger(tag);
				loggerMap.put(tag, slogger);
			}
		}
		return slogger;
	}

	/**
	 * Trim name in case it exceeds maximum length of {@value #TAG_MAX_LENGTH} characters.
	 */
	private final String forceValidName(String name)
	{
		if (name != null && name.length() > TAG_MAX_LENGTH)
		{
			final StringTokenizer st = new StringTokenizer(name, ".");
			if (st.hasMoreTokens()) // note that empty tokens are skipped, i.e., "aa..bb" has tokens "aa", "bb"
			{
				final StringBuilder sb = new StringBuilder();
				String token;
				do
				{
					token = st.nextToken();
					if (token.length() == 1) // token of one character appended as is
					{
						sb.append(token);
						sb.append('.');
					}
					else if (st.hasMoreTokens()) // truncate all but the last token
					{
						sb.append(token.charAt(0));
						sb.append("*.");
					}
					else // last token (usually class name) appended as is
					{
						sb.append(token);
					}
				} while (st.hasMoreTokens());

				name = sb.toString();
			}

			// Either we had no useful dot location at all or name still too long.
			// Take leading part and append '*' to indicate that it was truncated
			if (name.length() > TAG_MAX_LENGTH)
			{
				name = name.substring(0, TAG_MAX_LENGTH - 1) + '*';
			}
		}
		return name;
	}
}
