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

import com.mogujie.aceso.AcesoBasePlugin
import com.mogujie.aceso.Extension
import com.mogujie.aceso.util.GradleUtil
import com.mogujie.aceso.util.FileUtils
import org.gradle.api.Project

/**
 * The class processor.
 *
 * @author wangzhi
 */

public abstract class ClassProcessor {

    Project project

    String varName

    String varDirName

    Extension config

    String jarName

    ClassProcessor(Project project, String varName, String varDirName, Extension config) {
        this.project = project
        this.varName = varName
        this.varDirName = varDirName
        this.config = config
        if (GradleUtil.proguardOpen(project, varName)) {
            jarName = "main.jar"
        } else {
            jarName = "combined.jar"
        }
    }

    /**
     * Return the jar file which includes all classes.
     */
    abstract File getMergedJar()

    /**
     * Return the jar file which includes
     * all classes that have been processed.
     */
    abstract File getOutJar()

    /**
     * processing the class file.
     */
    abstract void process()

    /**
     * init env
     */
    void prepare() {
        FileUtils.initFile(getMergedJar())
        FileUtils.initFile(getOutJar())
    }

    protected File getFileInAceso(String category, String varDirName, String fileName) {
        if (FileUtils.isStringEmpty(fileName)) {
            return FileUtils.joinFile(project.buildDir, "intermediates", AcesoBasePlugin.ACESO_DIR_NAME, category, varDirName)
        } else {
            return FileUtils.joinFile(project.buildDir, "intermediates", AcesoBasePlugin.ACESO_DIR_NAME, category, varDirName, fileName)
        }
    }

}
