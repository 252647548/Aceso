package com.mogujie.aceso

import com.android.SdkConstants
import com.android.build.gradle.internal.transforms.JarMerger
import com.android.build.gradle.tasks.factory.AndroidJavaCompile
import com.android.builder.signing.SignedJarBuilder
import com.mogujie.groovy.util.Log
import com.mogujie.groovy.util.Utils
import com.mogujie.instantrun.InstantRunTool

/**
 * Created by wangzhi on 16/12/7.
 */
class AcesoFixPlugin extends AcesoBasePlugin {


    @Override
    protected void realApply() {
        if (config.modifiedJar == null) {
            config.modifiedJar = new File(project.projectDir, "modified.jar")
        }
        if (!Utils.checkFile(config.modifiedJar)) {
            Log.e("modifie jar  not found!")
        }

        InstantRunTool.setMethodLevelFix(config.methodLevelFix)

        project.android.applicationVariants.all { variant ->
            doIt(variant)
        }
    }


    public void doIt(def variant) {
        String varName = variant.name.capitalize()
        String varDirName = variant.getDirName()

        def jarMergingTask = findJarMergingTask(project, varName)

        project.tasks.create("Aceso" + varName, AcesoTask, new AcesoTask.HotFixAction(varName))

        if (Util.isAcesoFix(project)) {
            Log.i "is new hotfix "
            if (jarMergingTask == null) {
                doPatchWhenJarNotExists(varName, varDirName)
            } else {
                doPatchWhenJarExists(jarMergingTask, varName, varDirName)
            }
        } else {
            Log.i "is old hotfix "
            if (jarMergingTask == null) {
                Log.w "jarMergingTask is null, we will not do anything."
            } else {
                Log.w "jarMergingTask is exist,we will expand class scope."
                expandWhenJarExists(jarMergingTask, varName, varDirName)
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
            File combindBackupJar = Util.initFile(getFileInAceso("backup", varDirName, combindJar.name))

            File fixJar = Util.initFile(getFileInAceso("fix", varDirName, combindJar.name))

            HookWrapper.expandScope(combindJar, fixJar)

            Util.copy(project, combindJar, combindBackupJar.parentFile)

            Util.copy(project, fixJar, combindJar.parentFile)
        }
    }


    private void doPatchWhenJarNotExists(String varName, String varDirName) {
        Log.i("jarMergingTask is null")
        AndroidJavaCompile classTask = project.tasks.findByName("compile${varName}JavaWithJavac")
        classTask.doLast {
            File tempJarFile = Util.initFile(getFileInAceso("fix", varDirName, "backup.jar"))
            File jarFile = Util.initFile(getFileInAceso("fix", varDirName, "allclasses.jar"))
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

            HookWrapper.fix(tempJarFile, jarFile, classPath, null, config.acesoMappingPath)
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

            File combindBackupJar = Util.initFile(getFileInAceso("backup", varDirName, combindJar.name))
            File fixJar = Util.initFile(getFileInAceso("fix", varDirName, combindJar.name))
            ArrayList<File> classPath = new ArrayList()
//            classPath.addAll(classTask.classpath.files)
            classPath.add(config.modifiedJar)
            classPath.add(new File(Util.getAndroidSdkPath(project)))
            HookWrapper.fix(combindJar, fixJar, classPath, proguardMap, config.acesoMappingPath)

            Util.copy(project, combindJar, combindBackupJar.parentFile)

            Util.copy(project, fixJar, combindJar.parentFile)
        }
    }


}
