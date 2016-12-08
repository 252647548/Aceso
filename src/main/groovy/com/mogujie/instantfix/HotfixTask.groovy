package com.mogujie.instantfix

import com.mogujie.groovy.util.Exec
import com.mogujie.groovy.util.Utils
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Created by wangzhi on 16/12/7.
 */
class HotfixTask extends DefaultTask {

    File combindJar

    String packageName = "com.mogujie"

    @TaskAction
    void taskExec() {
        println "hotfix -> "+combindJar

        File fixDxdIR = new File(project.buildDir, "intermediates/dx-hotfix")
        Utils.clearDir(fixDxdIR)

        File fixJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-hotfix/conbind.jar")

        InstantFixProxy.hotfix(combindJar, fixJar, new File(InstantUtil.getAndroidSdkPath(project)))
        Exec.exec(["dx", "--dex", "--output=" + fixDxdIR.absolutePath, fixJar.absolutePath],null)
        Exec.exec(["adb", "push", new File(fixDxdIR.absolutePath, "classes.dex").absolutePath, "/sdcard/reload.dex"],null)
        println "----------pc's md5-----------"
        Exec.exec(["md5", new File(fixDxdIR.absolutePath, "classes.dex").absolutePath],null)
        println "----------adb's md5-----------"
        Exec.exec(["adb", "shell", "md5", "/sdcard/reload.dex"],null)
        println "----------restart app-----------"
        Exec.exec(["adb", "shell", "am", "force-stop", packageName],null)

    }
}
