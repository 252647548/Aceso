package com.mogujie.instantfix

import com.mogujie.groovy.util.Exec
import com.mogujie.groovy.util.Log
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Created by wangzhi on 16/12/8.
 */
class HotFixTask extends DefaultTask {

    String varName

    @TaskAction
    void taskExec() {
        def packageTask = project.tasks.findByName("package${varName}")
        File apkFile = InstantUtil.getTaskOutApkFile(packageTask)
        Exec.exec(["adb", "push", apkFile.absolutePath, "/sdcard/reload.apk"], null)
        Log.logLevel = 3
        Log.i("----------pc's md5-----------")
        Exec.exec(["md5", apkFile.absolutePath], null)
        Log.i("----------adb's md5-----------")
        Exec.exec(["adb", "shell", "md5", "/sdcard/reload.apk"], null)
        Log.i "----------restart app-----------"
        Exec.exec(["adb", "shell", "am", "force-stop", "com.mogujie"], null)
        Log.logLevel = 2
    }

    public static class HotFixAction implements org.gradle.api.Action<HotFixTask> {
        String varName
        HotFixAction(String varName) {

            this.varName = varName
        }
        @Override
        void execute(HotFixTask hotfixTask) {
            def assembleTask = hotfixTask.project.tasks.findByName("assemble" + varName)
            hotfixTask.dependsOn assembleTask
            hotfixTask.varName = varName
        }
    }
}
