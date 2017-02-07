package com.mogujie.aceso

import com.mogujie.aceso.processor.ExpandScopeProcessor
import com.mogujie.aceso.processor.FixClassProcessor
import com.mogujie.aceso.transoform.HookDexTransform
import com.mogujie.aceso.util.Util
import com.mogujie.groovy.util.Log
import com.mogujie.groovy.util.Utils
import com.mogujie.instantrun.InstantRunTool

/**
 * A plugin for generate patch apk.
 *
 * @author wangzhi
 */

public class AcesoFixPlugin extends AcesoBasePlugin {

    @Override
    protected void realApply() {
        if (Utils.isStringEmpty(config.modifiedJar)) {
            config.modifiedJar = new File(project.projectDir, "modified.jar").absolutePath
        }
        if (!Utils.checkFile(new File(config.modifiedJar))) {
            Log.e("modifie jar  not found!")
        }
        InstantRunTool.setMethodLevelFix(config.methodLevelFix)
        project.android.applicationVariants.all { variant ->
            doIt(variant)
        }
    }

    public void doIt(def variant) {
        String varName = variant.name.capitalize()
        String varDirName = variant.getDirName()
        //create the aceso task
        project.tasks.create("aceso" + varName, AcesoTask, new AcesoTask.HotFixAction(varName))

        if (Util.isAcesoFix(project)) {
            Log.i "the next will be aceso fix."
            HookDexTransform.injectDexTransform(project, variant, new FixClassProcessor(project, varName, varDirName, config))
        } else {
            Log.i "the next will expand scope."
            HookDexTransform.injectDexTransform(project, variant, new ExpandScopeProcessor(project, varName, varDirName, config))
        }
    }

}
