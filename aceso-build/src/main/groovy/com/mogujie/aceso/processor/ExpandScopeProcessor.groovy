package com.mogujie.aceso.processor

import com.android.build.gradle.internal.pipeline.TransformTask
import com.mogujie.aceso.Extension
import com.mogujie.aceso.HookWrapper
import com.mogujie.aceso.util.ProguardTool
import com.mogujie.groovy.util.Log
import org.gradle.api.Project

/**
 * Created by wangzhi on 17/2/6.
 */
class ExpandScopeProcessor extends ClassProcessor {
    ExpandScopeProcessor(Project project, String varName, String varDirName, Extension config) {
        super(project, varName, varDirName, config)
    }

    @Override
    File getMergedJar() {
        return getFileInAceso("merged", varDirName, jarName)
    }

    @Override
    File getOutJar() {
        return getFileInAceso("expand", varDirName, jarName)
    }

    @Override
    void process() {
        Log.i("expand class in hotfix project..")
        TransformTask proguardTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${varName}")
        if (proguardTask != null) {
            ProguardTool.instance().initProguardMap(proguardTask.transform.getMappingFile())
        }
        HookWrapper.expandScope(getMergedJar(), getOutJar())
    }

}
