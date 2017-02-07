package com.mogujie.aceso
import com.mogujie.groovy.util.Log
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * A empty task whose name is acesoXXX and dependsOn assemble task.
 *
 * @author wangzhi
 */

public class AcesoTask extends DefaultTask {

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
