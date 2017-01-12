package com.mogujie.instantfix

import com.mogujie.groovy.util.Log
import com.mogujie.groovy.util.Utils
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Created by wangzhi on 16/12/8.
 */
class Util {

    public static initFile(File dir, String name) {
        File file = new File(dir, name)
        Utils.initParentFile(file)
        file.delete()
        return file
    }

    public static def getAndroidSdkPath(Project project) {
        return "${project.android.getSdkDirectory()}/platforms/${project.android.getCompileSdkVersion()}/android.jar"
    }

    public static void copy(Project project, File src, File dstDir) {
        Utils.clearDir(dstDir)
        project.copy {
            from src
            into dstDir
            println "copy from ${src.absolutePath} to ${dstDir.absolutePath}"
        }
    }

    public static File getTaskOutApkFile(Task task) {
        File apk = task.outputs.files.files.find() { apk ->
            return apk.name.endsWith(".apk")
        }
        return apk
    }

    public static final int NEW_HOT_FIX = 2;
    public static final int OLD_HOT_FIX = 1;
    public static final int NO_HOT_FIX = 0;

    public static int isHotFix(Project project, Extension config) {
        boolean isNewHotfix = project.gradle.getStartParameter().taskNames.any { taskName ->
            Log.i "task " + taskName
            if (taskName.toLowerCase().startsWith("instantfix")) {
                return true
            }
        }
        if (isNewHotfix) {
            return NEW_HOT_FIX;
        }
        if (config.oldHotfix) {
            return OLD_HOT_FIX;
        }
        return NO_HOT_FIX;
    }


    public static boolean isProguard(Project project, String varName) {
        return (project.tasks.findByName("transformClassesAndResourcesWithProguardFor${varName}") != null)
    }



}
