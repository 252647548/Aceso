package com.mogujie.aceso.processor
import com.mogujie.aceso.Extension
import com.mogujie.aceso.HookWrapper
import com.mogujie.groovy.util.Log
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
