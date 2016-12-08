package com.mogujie.instantfix

import com.android.build.api.transform.Format
import com.android.build.api.transform.Transform
import com.android.build.gradle.internal.pipeline.IntermediateFolderUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by wangzhi on 16/12/7.
 */
class InstantFixPlugin implements Plugin<Project> {

    boolean isProguard = false
    InstantFixExt config
    boolean isHotfix = false

    @Override
    void apply(Project project) {
        project.extensions.create("InstantFix", InstantFixExt);
        project.afterEvaluate {
            config = project.extensions.findByName("InstantFix") as InstantFixExt
            project.android.applicationVariants.all { variant ->
                doIt(project, variant)
            }
        }
    }


    public def findJarMergingTask(Project project, String varName) {
        def jarMergingTask = project.tasks.findByName("transformClassesWithJarMergingFor${varName}")
        if (jarMergingTask == null) {
            jarMergingTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${varName}")
            isProguard = true
        }
        return jarMergingTask
    }

    public HotfixTask initHotfixTask(Project project, String varName) {
        HotfixTask hotfixTask = project.tasks.create("hotfix${varName}", HotfixTask.class)
        hotfixTask.outputs.upToDateWhen { return false }

//        def classTask = project.tasks.findByName("compile${varName}JavaWithJavac")
//        classTask.outputs.upToDateWhen { return false }
//        hotfixTask.dependsOn(classTask)

        return hotfixTask
    }

    public File getCombindJar(DefaultTask jarMergingTask) {
        Collection<File> outputs = jarMergingTask.outputs.files.files.findAll { file -> file.isDirectory() }
        if (outputs.size() != 1) {
            throw new GradleException("illegal out put in ${jarMergingTask.name}'s output: ${outputs} . ")
        }
        File jarMergingDir = outputs.getAt(0)
        println("jarMergingDir is " + jarMergingDir.absolutePath)
        Transform transform = jarMergingTask.transform
        File combindJar = null
        if (isProguard) {
            combindJar = IntermediateFolderUtils.getContentLocation(jarMergingDir, "main", transform.getOutputTypes(), transform.getScopes(), Format.JAR);

        } else {
            combindJar = IntermediateFolderUtils.getContentLocation(jarMergingDir, "combined", transform.getOutputTypes(), transform.getScopes(), Format.JAR);
        }
        println "combind jar : " + combindJar
        return combindJar
    }


    public void doIt(org.gradle.api.Project project, def variant) {
        String varName = variant.name.capitalize()
        if (!varName.toLowerCase().contains("release")) {
            return
        }
        def jarMergingTask = findJarMergingTask(project, varName)

        isHotfix = project.gradle.getStartParameter().taskNames.any { taskName ->
            println "task " + taskName
            if (taskName.startsWith("hotfix")) {
                return true
            }
        }
        HotfixTask hotfixTask = initHotfixTask(project, varName)
        if (isHotfix) {
            hotfixTask.dependsOn jarMergingTask
            jarMergingTask.doLast {
                File combindJar = getCombindJar(jarMergingTask)
                println "combindJar is " + combindJar
                hotfixTask.combindJar = combindJar
            }
            println "is hotfix"
            return
        } else {

            jarMergingTask.doLast {
                if (!config.instrument) {
                    println "instrument is disable"
                    return;
                }
                File combindJar = getCombindJar(jarMergingTask)
                //instrument jar
                File instrumentJar
                if (isProguard) {
                    instrumentJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-instrument/main.jar")
                } else {
                    instrumentJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-instrument/combined.jar")
                }

                File androidJar = new File(InstantUtil.getAndroidSdkPath(project))
                if (!androidJar.exists()) {
                    throw new RuntimeException("not found android jar.")
                }

                println "start inject "
                InstantFixProxy.instrument(combindJar, instrumentJar, androidJar)

                //backup origin jar and overlay origin jar
                File classBackupDir = new File(project.buildDir, "intermediates/jar-backup")
                project.copy {
                    from(combindJar)
                    into classBackupDir.absolutePath
                    println "copy from ${combindJar.absolutePath} to ${classBackupDir.absolutePath}"
                }
                combindJar.delete()

                project.copy {
                    from instrumentJar
                    into combindJar.parentFile
                    println "copy from ${instrumentJar.absolutePath} to ${combindJar.parentFile.absolutePath}"
                }

            }

        }

    }


}
