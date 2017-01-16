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
import instant.InstantRunTool
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
    Project project

    @Override
    void apply(Project project) {
        this.project = project
        project.extensions.create("InstantFix", Extension);


        project.afterEvaluate {
            config = project.extensions.findByName("InstantFix") as Extension

            if (config.disable == false) {
                if (config.instantMappingPath == null) {
                    config.instantMappingPath = new File(project.projectDir, "instant-mapping.txt").absolutePath
                }
                if (!Utils.checkFile(config.instantMappingPath)) {
                    Log.e("instant mapping not found!")
                }

                if (config.modifiedJar == null) {
                    config.modifiedJar = new File(project.projectDir, "modified.jar")
                }

                if (!Utils.checkFile(config.modifiedJar)) {
                    Log.e("modifie jar  not found!")
                }

                InstantRunTool.setMethodLevelFix(config.methodLevelFix)

                project.android.applicationVariants.all { variant ->
                    doIt(project, variant)
                }
            }

        }
    }


    public void doIt(org.gradle.api.Project project, def variant) {
        String varName = variant.name.capitalize()
        String varDirName = variant.getDirName()

        def jarMergingTask = findJarMergingTask(project, varName)

        project.tasks.create("instantFix" + varName, HotFixTask, new HotFixTask.HotFixAction(varName))

        int status = Util.isHotFix(project, config)
        InstantFixWrapper.filter = initFilter()
        if (status == Util.NEW_HOT_FIX) {
            Log.i "is new hotfix "
            if (jarMergingTask == null) {
                doPatchWhenJarNotExists(varName, varDirName)
            } else {
                doPatchWhenJarExists(jarMergingTask, varName, varDirName)
            }
            return
        }
        if (status == Util.OLD_HOT_FIX) {
            if (config.disableOldFixDebug && varName.toLowerCase().contains("debug")) {
                return;
            }

            Log.i "is old hotfix "
            if (jarMergingTask == null) {
                Log.w "jarMergingTask is null, we will not do anything."
            } else {
                Log.w "jarMergingTask is exist,we will expand class scope."

                expandWhenJarExists(jarMergingTask, varName, varDirName)
            }

        } else {

            if (config.disableInstrumentDebug && varName.toLowerCase().contains("debug")) {
                return;
            }
            //todo jarMergingTask == null
            if (jarMergingTask == null) {
                try {
                    throw new GradleException("jarMergingTask is null!")
                } catch (Exception e) {
                    Log.e("can not found jarMergingTask. we will not instrument code! for var " + varName)
                    if (!config.ignoreWarning) {
                        Log.e("ignoreWarning is " + config.ignoreWarning)
                        throw e
                    }
                }
                return
            }

            jarMergingTask.doLast {

                if (jarMergingTask.name.startsWith("transformClassesAndResourcesWithProguardFor")) {
                    ProguardTool.instance().initProguardMap(jarMergingTask.transform.getMappingFile())
                }

                doInstrumentClass(varName, varDirName, jarMergingTask)
            }

        }

    }


    private void expandWhenJarExists(def jarMergingTask, String varName, String varDirName) {
        jarMergingTask.outputs.upToDateWhen { return false }
        jarMergingTask.doLast {
            if (jarMergingTask.name.startsWith("transformClassesAndResourcesWithProguardFor")) {
                ProguardTool.instance().initProguardMap(jarMergingTask.transform.getMappingFile())
            }
            File combindJar = getCombindJar(jarMergingTask, varName)
            Log.i "combindJar is " + combindJar
            File combindBackupJar = Util.initFile(project.buildDir, "intermediates/jar-backup/${varDirName}/" + combindJar.name)

            File fixJar = Util.initFile(project.buildDir, "intermediates/jar-hotfix/${varDirName}/" + combindJar.name)

            InstantFixWrapper.expandScope(combindJar, fixJar)

            Util.copy(project, combindJar, combindBackupJar.parentFile)

            Util.copy(project, fixJar, combindJar.parentFile)
        }
    }

    private void doInstrumentClass(String varName, String varDirName, def jarMergingTask) {
        if (!config.instrument) {
            Log.i "instrument is disable"
            return;
        }
        File combindJar = getCombindJar(jarMergingTask, varName)
        //instrument jar
        File instrumentJar
        if (Util.isProguard(project, varName)) {
            instrumentJar = Util.initFile(project.buildDir, "intermediates/jar-instrument/${varDirName}/main.jar")
        } else {
            instrumentJar = Util.initFile(project.buildDir, "intermediates/jar-instrument/${varDirName}/combined.jar")
        }

        File androidJar = new File(Util.getAndroidSdkPath(project))
        if (!androidJar.exists()) {
            throw new RuntimeException("not found android jar.")
        }

        Log.i "start inject "
        ArrayList<File> classPath = new ArrayList<>()
        classPath.add(androidJar)
        File mappingFile = new File(project.buildDir, "intermediates/instant-mapping/${varDirName}/mapping.txt")
        InstantFixWrapper.instrument(combindJar, instrumentJar, classPath, mappingFile, config.instantMappingPath)

        //backup origin jar and overlay origin jar
        File classBackupDir = new File(project.buildDir, "intermediates/jar-backup/${varDirName}")

        Util.copy(project, combindJar, classBackupDir)

        combindJar.delete()

        Util.copy(project, instrumentJar, combindJar.parentFile)
    }

    private void doPatchWhenJarNotExists(String varName, String varDirName) {
        Log.i("jarMergingTask is null")
        AndroidJavaCompile classTask = project.tasks.findByName("compile${varName}JavaWithJavac")
        classTask.doLast {
            File tempJarFile = Util.initFile(project.buildDir, "intermediates/class-hotfix-jar/${varDirName}/backup.jar")
            File jarFile = Util.initFile(project.buildDir, "intermediates/class-hotfix-jar/${varDirName}/allclasses.jar")
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
            ArrayList<File> classPath = new ArrayList()
            classPath.add(new File(Util.getAndroidSdkPath(project)))
            classPath.add(config.modifiedJar)

            InstantFixWrapper.instantFix(tempJarFile, jarFile, classPath, null, config.instantMappingPath)
            Utils.clearDir(classTask.destinationDir)
            project.copy {
                from project.zipTree(jarFile)
                into classTask.destinationDir
            }
        }
    }

    private void doPatchWhenJarExists(def jarMergingTask, String varName, String varDirName) {
        AndroidJavaCompile classTask = project.tasks.findByName("compile${varName}JavaWithJavac")
        jarMergingTask.outputs.upToDateWhen { return false }
        jarMergingTask.doLast {
//            jarMergingTask=jarMergingTask as dE
            HashMap<String, String> proguardMap = null
            if (jarMergingTask.name.startsWith("transformClassesAndResourcesWithProguardFor")) {
                ProguardTool.instance().initProguardMap(jarMergingTask.transform.getMappingFile())
                proguardMap = ProguardTool.instance().getProguardMap()
            }

            File combindJar = getCombindJar(jarMergingTask, varName)
            Log.i "combindJar is " + combindJar
            File combindBackupJar = Util.initFile(project.buildDir, "intermediates/jar-backup/${varDirName}/" + combindJar.name)

            File fixJar = Util.initFile(project.buildDir, "intermediates/jar-hotfix/${varDirName}/" + combindJar.name)
            ArrayList<File> classPath = new ArrayList()
//            classPath.addAll(classTask.classpath.files)
            classPath.add(config.modifiedJar)
            classPath.add(new File(Util.getAndroidSdkPath(project)))
            InstantFixWrapper.instantFix(combindJar, fixJar, classPath, proguardMap, config.instantMappingPath)

            Util.copy(project, combindJar, combindBackupJar.parentFile)

            Util.copy(project, fixJar, combindJar.parentFile)
        }
    }


    public static InstantFixWrapper.InstrumentFilter initFilter() {

        return new InstantFixWrapper.InstrumentFilter() {
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
        if (Util.isProguard(project, varName)) {
            combindJar = IntermediateFolderUtils.getContentLocation(jarMergingDir, "main", transform.getOutputTypes(), transform.getScopes(), Format.JAR);
        } else {
            combindJar = IntermediateFolderUtils.getContentLocation(jarMergingDir, "combined", transform.getOutputTypes(), transform.getScopes(), Format.JAR);
        }
        Log.i "combind jar : " + combindJar
        return combindJar
    }

}
