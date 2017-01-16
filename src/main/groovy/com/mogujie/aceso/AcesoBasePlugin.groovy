package com.mogujie.aceso

import com.android.build.api.transform.Format
import com.android.build.api.transform.Transform
import com.android.build.gradle.internal.pipeline.IntermediateFolderUtils
import com.mogujie.groovy.util.Log
import com.mogujie.groovy.util.Utils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by wangzhi on 17/1/16.
 */
abstract class AcesoBasePlugin implements Plugin<Project> {
    static def MATCHER_R = '''.*/R\\$.*\\.class|.*/R\\.class'''
    Project project
    Extension config

    @Override
    void apply(Project project) {
        this.project = project
        project.extensions.create("Aceso", Extension);

        project.afterEvaluate {
            initExtensions()
            if (config.disable == false) {
                if (config.acesoMappingPath == null) {
                    config.acesoMappingPath = new File(project.projectDir, "aceso-mapping.txt").absolutePath
                }
                if (!Utils.checkFile(config.acesoMappingPath)) {
                    Log.e("aceso mapping not found!")
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

    protected def findJarMergingTask(Project project, String varName) {
        def jarMergingTask = project.tasks.findByName("transformClassesWithJarMergingFor${varName}")
        if (jarMergingTask == null) {
            jarMergingTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${varName}")

        }
        return jarMergingTask
    }

    protected File getCombindJar(DefaultTask jarMergingTask, String varName) {
        Collection<File> outputs = jarMergingTask.outputs.files.files.findAll { file -> file.isDirectory() }
        if (outputs.size() != 1) {
            throw new GradleException("illegal out put in ${jarMergingTask.name}'s output: ${outputs} . ")
        }
        File jarMergingDir = outputs.getAt(0)
        Log.i("jarMergingDir is " + jarMergingDir.absolutePath)
        Transform transform = jarMergingTask.transform
        File combindJar = null
        if (Util.isProguard(project, varName)) {
            combindJar = IntermediateFolderUtils.getContentLocation(jarMergingDir, "main", transform.getOutputTypes(), transform.getScopes(), Format.JAR);
        } else {
            combindJar = IntermediateFolderUtils.getContentLocation(jarMergingDir, "combined", transform.getOutputTypes(), transform.getScopes(), Format.JAR);
        }
        Log.i "combind jar : " + combindJar
        return combindJar
    }

    public static final String ACESO_DIR_NAME = "aceso"

    protected File getFileInAceso(String category,String varDirName, String fileName) {
        if(Utils.isStringEmpty(fileName)){
            return Utils.joinFile(project.buildDir, "intermediates", ACESO_DIR_NAME,category, varDirName)
        }else{
            return Utils.joinFile(project.buildDir, "intermediates", ACESO_DIR_NAME, category,varDirName, fileName)
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

                if (name.startsWith("com/android/tools/fd/runtime") || name.startsWith("com/xiaomi/")
                        || name.startsWith("org/apache/")
                        || name.startsWith("com/android/")
                        || name.startsWith("com/google/")
                        || name.startsWith("android/")
                        || name.startsWith("okhttp3/")) {
                    return false
                }
                return true

            }
        }
    }
}
