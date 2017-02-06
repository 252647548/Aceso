package com.mogujie.aceso;

import android.util.Log;

import com.android.tools.fd.runtime.InstantRunClassLoader;
import com.android.tools.fd.runtime.PatchesLoader;

import java.io.File;


/**
 * Created by wangzhi on 17/2/6.
 */

public class HotPatchUtil {
    private static final String TAG = "Guarder";

    public boolean fix(File optPath, File installPatchFile) {

        ClassLoader clsLoader = null;
        try {
            clsLoader = new InstantRunClassLoader(installPatchFile.getAbsolutePath(), optPath,
                    getClass().getClassLoader());
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }

        if (clsLoader == null) {
            return false;
        }

        boolean result = true;
        // we should transform this process with an interface/impl

        try {
            Class<?> aClass = Class.forName(
                    "com.android.tools.fd.runtime.AppPatchesLoaderImpl", false, clsLoader);
            PatchesLoader loader = (PatchesLoader) aClass.newInstance();
            String[] getPatchedClasses = (String[]) aClass
                    .getDeclaredMethod("getPatchedClasses").invoke(loader);

            Log.v(TAG, "Got the list of classes ");
            for (String getPatchedClass : getPatchedClasses) {
                Log.v(TAG, "class " + getPatchedClass);
            }

            if (!loader.load()) {
                result = false;
                Log.i(TAG, "load failure.");
            }
        } catch (Exception e) {
            result = false;
            Log.e(TAG, "Couldn't apply code changes", e);
            e.printStackTrace();
        }

        return result;
    }
}
