package com.mogujie.instantfix

import com.mogujie.groovy.util.Utils
import org.gradle.api.Project

/**
 * Created by wangzhi on 16/12/8.
 */
class InstantUtil {

    public static initFile(File dir, String name) {
        File file = new File(dir, name)
        Utils.initParentFile(file)
        file.delete()
        return file
    }

    public static def getAndroidSdkPath(Project project) {
        return "${project.android.getSdkDirectory()}/platforms/${project.android.getCompileSdkVersion()}/android.jar"
    }
}
