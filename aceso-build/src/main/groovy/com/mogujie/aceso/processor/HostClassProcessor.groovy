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
import com.mogujie.aceso.Extension
import com.mogujie.aceso.HookWrapper
import com.mogujie.aceso.util.Log
import org.gradle.api.Project

/**
 * The class processor for instrument class file in host project.
 *
 * @author wangzhi
 */

public class HostClassProcessor extends ClassProcessor {

    HostClassProcessor(Project project, String varName, String varDirName, Extension config) {
        super(project, varName, varDirName, config)
    }

    @Override
    File getMergedJar() {
        return getFileInAceso("merged", varDirName, jarName)
    }

    @Override
    File getOutJar() {
        return getFileInAceso("instrument", varDirName, jarName)
    }

    @Override
    void process() {
        Log.i("process the host class..")
        HookWrapper.instrument(project,getMergedJar(), getOutJar(), null,
                getFileInAceso("mapping", varDirName, "mapping.txt"), config.acesoMapping)
    }


}
