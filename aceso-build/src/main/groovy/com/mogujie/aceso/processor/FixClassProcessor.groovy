package com.mogujie.aceso.processor

import com.android.build.gradle.internal.pipeline.TransformTask
import com.mogujie.aceso.Extension
import com.mogujie.aceso.HookWrapper
import com.mogujie.aceso.util.ProguardTool
import com.mogujie.aceso.util.Util
import com.mogujie.groovy.util.Log
import org.gradle.api.Project

/**
 * Created by wangzhi on 17/2/4.
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
        File fixJar = Util.initFile(getOutJar())
        HashMap<String, String> proguardMap = null
        TransformTask proguardTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${varName}")
        if (proguardTask != null) {
            ProguardTool.instance().initProguardMap(proguardTask.transform.getMappingFile())
            proguardMap = ProguardTool.instance().getProguardMap()
        }
        ArrayList<File> classPath = new ArrayList()
        classPath.add(new File(config.modifiedJar))
        HookWrapper.fix(project, getMergedJar(), fixJar, classPath, proguardMap, config.acesoMapping)
    }
}
