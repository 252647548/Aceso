package com.mogujie.aceso.util

import com.mogujie.groovy.util.Utils
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * A Util.
 *
 * @author wangzhi
 */

public class Util {

    public static initFile(File file) {
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

    /**
     * Whether it is executed acesoXXX task.
     */
    public static boolean isAcesoFix(Project project) {
        boolean isNewHotfix = project.gradle.getStartParameter().taskNames.any { taskName ->
            if (taskName.toLowerCase().startsWith("aceso")) {
                return true
            }
        }
        return isNewHotfix
    }


    public static boolean proguardOpen(Project project, String varName) {
        return (project.tasks.findByName("transformClassesAndResourcesWithProguardFor${varName}") != null)
    }


}
