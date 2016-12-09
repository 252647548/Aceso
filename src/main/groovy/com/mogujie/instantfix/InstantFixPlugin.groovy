package com.mogujie.instantfix

import com.android.SdkConstants
import com.android.build.api.transform.Format
import com.android.build.api.transform.Transform
import com.android.build.gradle.internal.pipeline.IntermediateFolderUtils
import com.android.build.gradle.internal.transforms.JarMerger
import com.android.build.gradle.tasks.factory.AndroidJavaCompile
import com.android.builder.signing.SignedJarBuilder
import com.mogujie.groovy.util.Log
import com.mogujie.groovy.util.Utils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by wangzhi on 16/12/7.
 */
class InstantFixPlugin implements Plugin<Project> {

    static def MATCHER_R = '''.*/R\\$.*\\.class|.*/R\\.class'''
    Extension config
    boolean isHotfix = false
    Project project

    @Override
    void apply(Project project) {
        this.project = project
        project.extensions.create("InstantFix", Extension);


        project.afterEvaluate {
            config = project.extensions.findByName("InstantFix") as Extension
            project.android.applicationVariants.all { variant ->
                doIt(project, variant)
            }
        }
    }


    public void doIt(org.gradle.api.Project project, def variant) {
        String varName = variant.name.capitalize()

        def jarMergingTask = findJarMergingTask(project, varName)

        project.tasks.create("hotfix" + varName, HotFixTask, new HotFixTask.HotFixAction(varName))

        isHotfix = InstantUtil.isHotFix(project)

        if (isHotfix) {
            Log.i "is hotfix "

            if (jarMergingTask == null) {
                doPatchWhenJarNotExists(varName)
            } else {
                doPatchWhenJarExists(jarMergingTask, varName)
            }
            return
        } else {
            InstantFixWrapper.filter = initFilter()
            //todo jarMergingTask == null
            if (jarMergingTask == null) {
                try {
                    throw new GradleException("jarMergingTask is null!")
                } catch (Exception e) {
                    Log.e("can not found jarMergingTask. we will not instrument code!")
                    if (!config.ignoreWarning) {
                        throw e
                    }
                }
                return
            }

            jarMergingTask.doLast {
                doInstrumentClass(varName, jarMergingTask)
            }

        }

    }

    private void doInstrumentClass(String varName, def jarMergingTask) {
        if (!config.instrument) {
            Log.i "instrument is disable"
            return;
        }
        File combindJar = getCombindJar(jarMergingTask, varName)
        //instrument jar
        File instrumentJar
        if (InstantUtil.isProguard(project, varName)) {
            instrumentJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-instrument/main.jar")
        } else {
            instrumentJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-instrument/combined.jar")
        }

        File androidJar = new File(InstantUtil.getAndroidSdkPath(project))
        if (!androidJar.exists()) {
            throw new RuntimeException("not found android jar.")
        }

        Log.i "start inject "
        InstantFixWrapper.instrument(combindJar, instrumentJar, androidJar)

        //backup origin jar and overlay origin jar
        File classBackupDir = new File(project.buildDir, "intermediates/jar-backup")

        InstantUtil.copy(project, combindJar, classBackupDir)

        combindJar.delete()

        InstantUtil.copy(project, instrumentJar, combindJar.parentFile)
    }

    private void doPatchWhenJarNotExists(String varName) {
        Log.i("jarMergingTask is null")
        AndroidJavaCompile classTask = project.tasks.findByName("compile${varName}JavaWithJavac")
        classTask.doLast {
            File tempJarFile = InstantUtil.initFile(project.buildDir, "intermediates/class-hotfix-jar/temp.jar")
            File jarFile = InstantUtil.initFile(project.buildDir, "intermediates/class-hotfix-jar/allclasses.jar")
            JarMerger jarMerger = new JarMerger(tempJarFile)
            jarMerger.setFilter(new SignedJarBuilder.IZipEntryFilter() {
                @Override
                public boolean checkEntry(String archivePath)
                        throws SignedJarBuilder.IZipEntryFilter.ZipAbortException {
                    return archivePath.endsWith(SdkConstants.DOT_CLASS);
                }
            });
            jarMerger.addFolder(classTask.destinationDir)
            jarMerger.close()
            InstantFixWrapper.hotfix(tempJarFile, jarFile, new File(InstantUtil.getAndroidSdkPath(project)))
            Utils.clearDir(classTask.destinationDir)
            project.copy {
                from project.zipTree(jarFile)
                into classTask.destinationDir
            }
        }
    }

    private void doPatchWhenJarExists(def jarMergingTask, String varName) {
        jarMergingTask.outputs.upToDateWhen { return false }
        jarMergingTask.doLast {
            File combindJar = getCombindJar(jarMergingTask, varName)
            Log.i "combindJar is " + combindJar
            File combindBackupJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-backup/" + combindJar.name)

            File fixJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-hotfix/" + combindJar.name)
            InstantFixWrapper.hotfix(combindJar, fixJar, new File(InstantUtil.getAndroidSdkPath(project)))

            InstantUtil.copy(project, combindJar, combindBackupJar.parentFile)

            InstantUtil.copy(project, fixJar, combindJar.parentFile)
        }
    }

    public InstantFixWrapper.InstrumentFilter initFilter() {
        return new InstantFixWrapper.InstrumentFilter() {
            @Override
            boolean accept(String name) {
                if (!name.endsWith(".class")) {
                    return false
                }
                if (name.endsWith("BuildConfig.class") || name ==~ MATCHER_R) {
                    return false
                }
                Log.i("filter accept => " + name)
                if (name.startsWith("com/xiaomi/")
                        || name.startsWith("org/apache/")
                        || name.startsWith("com/android/")
                        || name.startsWith("com/google/")
                        || name.startsWith("android/")
                        || name.startsWith("org/")
                        || name.startsWith("okhttp3/")) {
                    return false
                }
                return true


            }
        }
    }

    public def findJarMergingTask(Project project, String varName) {
        def jarMergingTask = project.tasks.findByName("transformClassesWithJarMergingFor${varName}")
        if (jarMergingTask == null) {
            jarMergingTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${varName}")

        }
        return jarMergingTask
    }


    public File getCombindJar(DefaultTask jarMergingTask, String varName) {
        Collection<File> outputs = jarMergingTask.outputs.files.files.findAll { file -> file.isDirectory() }
        if (outputs.size() != 1) {
            throw new GradleException("illegal out put in ${jarMergingTask.name}'s output: ${outputs} . ")
        }
        File jarMergingDir = outputs.getAt(0)
        Log.i("jarMergingDir is " + jarMergingDir.absolutePath)
        Transform transform = jarMergingTask.transform
        File combindJar = null
        if (InstantUtil.isProguard(project, varName)) {
            combindJar = IntermediateFolderUtils.getContentLocation(jarMergingDir, "main", transform.getOutputTypes(), transform.getScopes(), Format.JAR);
        } else {
            combindJar = IntermediateFolderUtils.getContentLocation(jarMergingDir, "combined", transform.getOutputTypes(), transform.getScopes(), Format.JAR);
        }
        Log.i "combind jar : " + combindJar
        return combindJar
    }

}
