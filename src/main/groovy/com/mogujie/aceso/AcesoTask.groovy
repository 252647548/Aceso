package com.mogujie.aceso

import com.mogujie.groovy.util.Log
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Created by wangzhi on 16/12/8.
 */
class AcesoTask extends DefaultTask {

    @TaskAction
    void taskExec() {
        Log.i("execute aceso fix successful!")
    }

    public static class HotFixAction implements org.gradle.api.Action<AcesoTask> {
        String varName

        HotFixAction(String varName) {
            this.varName = varName
        }

        @Override
        void execute(AcesoTask hotfixTask) {
            def assembleTask = hotfixTask.project.tasks.findByName("assemble" + varName)
            hotfixTask.dependsOn assembleTask
        }

    }
}
