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

package com.mogujie.aceso.processor
import com.android.build.gradle.internal.pipeline.TransformTask
import com.mogujie.aceso.Extension
import com.mogujie.aceso.HookWrapper
import com.mogujie.aceso.util.ProguardUtil
import com.mogujie.aceso.util.Log
import com.mogujie.aceso.util.FileUtils
import org.gradle.api.Project
/**
 * The class processor for generate the hotfix file.
 *
 * @author wangzhi
 */

class FixClassProcessor extends ClassProcessor {

    FixClassProcessor(Project project, String varName, String varDirName, Extension config) {
        super(project, varName, varDirName, config)
    }

    @Override
    File getMergedJar() {
        return getFileInAceso("merged", varDirName, jarName)
    }

    @Override
    File getOutJar() {
        return getFileInAceso("fix", varDirName, jarName)
    }

    @Override
    void process() {
        Log.i("generate the fix class..")
        File fixJar = FileUtils.initFile(getOutJar())
        HashMap<String, String> proguardMap = null
        TransformTask proguardTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${varName}")
        if (proguardTask != null) {
            ProguardUtil.instance().initProguardMap(proguardTask.transform.getMappingFile())
            proguardMap = ProguardUtil.instance().getProguardMap()
        }
        ArrayList<File> classPath = new ArrayList()
        classPath.add(new File(config.modifiedJar))
        HookWrapper.fix(project, getMergedJar(), fixJar, classPath, proguardMap, config.acesoMapping)
    }
}
