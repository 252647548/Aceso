package com.mogujie.instantfix

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Created by wangzhi on 16/12/7.
 */
class PatchClassTask extends DefaultTask {

    File combindJar

    String packageName = "com.mogujie"

    @TaskAction
    void taskExec() {
        println "hotfix -> " + combindJar
//        File fixDxDir = new File(project.buildDir, "intermediates/dx-hotfix")
//


//        Exec.exec(["dx", "--dex", "--output=" + fixDxDir.absolutePath, fixJar.absolutePath],null)

    }
}
