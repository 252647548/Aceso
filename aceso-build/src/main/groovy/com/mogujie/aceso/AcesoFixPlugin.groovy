/*
 *
 *  * Copyright (C) 2017 meili-inc company
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.mogujie.aceso

import com.mogujie.aceso.processor.ExpandScopeProcessor
import com.mogujie.aceso.processor.FixClassProcessor
import com.mogujie.aceso.transoform.HookDexTransform
import com.mogujie.aceso.util.GradleUtil
import com.mogujie.aceso.util.Log
import com.mogujie.aceso.util.FileUtils
import com.mogujie.instantrun.IncrementalTool

/**
 * A plugin for generate patch apk.
 *
 * @author wangzhi
 */

public class AcesoFixPlugin extends AcesoBasePlugin {

    @Override
    protected void realApply() {
        if (FileUtils.isStringEmpty(config.modifiedJar)) {
            config.modifiedJar = new File(project.projectDir, "modified.jar").absolutePath
        }
        if (!FileUtils.checkFile(new File(config.modifiedJar))) {
            Log.e("modifie jar  not found!")
        }
        IncrementalTool.setMethodLevelFix(config.methodLevelFix)
        project.android.applicationVariants.all { variant ->
            doIt(variant)
        }
    }

    public void doIt(def variant) {
        String varName = variant.name.capitalize()
        String varDirName = variant.getDirName()
        //create the aceso task
        project.tasks.create("aceso" + varName, AcesoTask, new AcesoTask.HotFixAction(varName))

        if (GradleUtil.isAcesoFix(project)) {
            Log.i "the next will be aceso fix."
            HookDexTransform.injectDexTransform(project, variant, new FixClassProcessor(project, varName, varDirName, config))
        } else {
            Log.i "the next will expand scope."
            HookDexTransform.injectDexTransform(project, variant, new ExpandScopeProcessor(project, varName, varDirName, config))
        }
    }

}
