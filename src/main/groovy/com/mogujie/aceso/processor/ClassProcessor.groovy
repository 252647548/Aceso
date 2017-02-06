package com.mogujie.aceso.processor

import com.mogujie.aceso.AcesoBasePlugin
import com.mogujie.aceso.Extension
import com.mogujie.aceso.util.Util
import com.mogujie.groovy.util.Utils
import org.gradle.api.Project

/**
 * Created by wangzhi on 17/2/4.
 */
abstract class ClassProcessor {

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
        if (Util.isProguard(project, varName)) {
            jarName = "main.jar"
        } else {
            jarName = "combined.jar"
        }
    }

    abstract File getMergedJar()

    abstract File getOutJar()

    abstract void process()

    void prepare() {
        Util.initFile(getMergedJar())
        Util.initFile(getOutJar())
    }

    protected File getFileInAceso(String category, String varDirName, String fileName) {
        if (Utils.isStringEmpty(fileName)) {
            return Utils.joinFile(project.buildDir, "intermediates", AcesoBasePlugin.ACESO_DIR_NAME, category, varDirName)
        } else {
            return Utils.joinFile(project.buildDir, "intermediates", AcesoBasePlugin.ACESO_DIR_NAME, category, varDirName, fileName)
        }
    }

}
