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

import com.mogujie.aceso.util.ProguardTool
import com.mogujie.groovy.util.Log
import com.mogujie.groovy.util.Utils
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * The base plugin
 *
 * @author wangzhi
 */

public abstract class AcesoBasePlugin implements Plugin<Project> {

    public static final String ACESO_DIR_NAME = "aceso"
    public static def MATCHER_R = '''.*/R\\$.*\\.class|.*/R\\.class'''

    Project project
    Extension config
    List<String> blackList

    @Override
    void apply(Project project) {
        this.project = project
        project.extensions.create("Aceso", Extension);

        project.afterEvaluate {
            initExtensions()
            Log.logLevel = config.logLevel
            if (!config.disable) {

                if (Utils.isStringEmpty(config.acesoMapping)) {
                    config.acesoMapping = new File(project.projectDir, "aceso-mapping.txt").absolutePath
                }

                initBlacklist()

                if (!Utils.checkFile(config.acesoMapping)) {
                    Log.w("aceso mapping not found!")
                }
                HookWrapper.filter = initFilter()
                realApply()
            }
        }
    }

    protected void realApply() {

    }

    protected void initExtensions() {
        config = project.extensions.findByName("Aceso") as Extension
    }

    protected void initBlacklist() {
        File blackListFile;
        blackList = new ArrayList<>()
        if (Utils.isStringEmpty(config.blackListPath)) {
            blackListFile = new File(project.projectDir, 'aceso-blackList.txt')
        } else {
            blackListFile = new File(config.blackListPath)
        }
        Log.i("blackList file is " + blackListFile.absolutePath)

        if (blackListFile.exists()) {
            blackListFile.eachLine { line ->
                blackList.add(line.trim())
            }
        }
    }

    public static HookWrapper.InstrumentFilter initFilter() {

        return new HookWrapper.InstrumentFilter() {
            @Override
            boolean accept(String name) {
                if (!name.endsWith(".class")) {
                    return false
                }

                if (name.endsWith("BuildConfig.class") || name ==~ MATCHER_R) {
                    return false
                }

                if (ProguardTool.instance().getProguardMap().size() > 0) {
                    name = ProguardTool.instance().getProguardMap().get(name)
                }

                for (String str : blackList) {
                    if (name.startsWith(str)) {
                        return false
                    }
                }
                return true
            }
        }
    }

}
