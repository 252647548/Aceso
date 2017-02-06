package com.android.tools.fd.runtime;

import java.io.File;

import dalvik.system.BaseDexClassLoader;

/**
 * Created by xieguo on 1/10/17.
 */

public class InstantRunClassLoader extends BaseDexClassLoader {

    public InstantRunClassLoader(String dexPath, File optimizedDirectory, ClassLoader parent) {
        super(dexPath, optimizedDirectory, null, parent);
    }

    /**
     * find a native code library.
     *
     * @param libraryName the name of the library.
     * @return the String of a path name to the library or <code>null</code>.
     * @category ClassLoader
     * @see ClassLoader#findLibrary(String)
     */
    public String  findLibrary(final String libraryName) {

        try {
            return (String)AndroidInstantRuntime.invokeProtectedMethod(this.getParent(), new Object[]{libraryName},
                    new Class[]{String.class}, "findLibrary");
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

}
