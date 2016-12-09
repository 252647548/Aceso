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


    public File getCombindJar(DefaultTask jarMergingTask) {
        Collection<File> outputs = jarMergingTask.outputs.files.files.findAll { file -> file.isDirectory() }
        if (outputs.size() != 1) {
            throw new GradleException("illegal out put in ${jarMergingTask.name}'s output: ${outputs} . ")
        }
        File jarMergingDir = outputs.getAt(0)
        Log.i("jarMergingDir is " + jarMergingDir.absolutePath)
        Transform transform = jarMergingTask.transform
        File combindJar = null
        if (isProguard) {
            combindJar = IntermediateFolderUtils.getContentLocation(jarMergingDir, "main", transform.getOutputTypes(), transform.getScopes(), Format.JAR);

        } else {
            combindJar = IntermediateFolderUtils.getContentLocation(jarMergingDir, "combined", transform.getOutputTypes(), transform.getScopes(), Format.JAR);
        }
        Log.i "combind jar : " + combindJar
        return combindJar
    }


    public void doIt(org.gradle.api.Project project, def variant) {
        String varName = variant.name.capitalize()

        def jarMergingTask = findJarMergingTask(project, varName)

        HotFixTask hotFixTask = project.tasks.create("hotfix" + varName, HotFixTask, new HotFixTask.HotFixAction(varName))

        isHotfix = project.gradle.getStartParameter().taskNames.any { taskName ->
            Log.i "task " + taskName
            if (taskName.startsWith("hotfix")) {
                return true
            }
        }



        if (isHotfix) {
            Log.i "is hotfix "
            if (jarMergingTask == null) {
                //patch class transorm will do
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
                    InstantFixProxy.hotfix(tempJarFile, jarFile, new File(InstantUtil.getAndroidSdkPath(project)))
                    Utils.clearDir(classTask.destinationDir)
                    project.copy {
                        from project.zipTree(jarFile)
                        into classTask.destinationDir
                    }
                }

            } else {
                jarMergingTask.outputs.upToDateWhen { return false }
                jarMergingTask.doLast {
                    File combindJar = getCombindJar(jarMergingTask)
                    Log.i "combindJar is " + combindJar
                    File combindBackupJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-backup/" + combindJar.name)

                    File fixJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-hotfix/" + combindJar.name)
                    InstantFixProxy.hotfix(combindJar, fixJar, new File(InstantUtil.getAndroidSdkPath(project)))

                    InstantUtil.copy(project, combindJar, combindBackupJar.parentFile)

                    InstantUtil.copy(project, fixJar, combindJar.parentFile)
                }
            }

            return
        } else {
            //todo jarMergingTask == null
            if (jarMergingTask == null) {
                throw new GradleException("jarMergingTask is null!")
            }

            jarMergingTask.doLast {
                if (!config.instrument) {
                    Log.i "instrument is disable"
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

                Log.i "start inject "
                InstantFixProxy.instrument(combindJar, instrumentJar, androidJar)

                //backup origin jar and overlay origin jar
                File classBackupDir = new File(project.buildDir, "intermediates/jar-backup")

                InstantUtil.copy(project, combindJar, classBackupDir)

                combindJar.delete()

                InstantUtil.copy(project, instrumentJar, combindJar.parentFile)


            }

        }

    }


}
