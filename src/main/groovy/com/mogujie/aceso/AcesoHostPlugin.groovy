package com.mogujie.aceso

import com.mogujie.groovy.util.Log
import org.gradle.api.GradleException

/**
 * Created by wangzhi on 17/1/16.
 */
class AcesoHostPlugin extends AcesoBasePlugin {

    @Override
    protected void realApply() {

        project.android.applicationVariants.all { variant ->
            doIt(variant)
        }
    }

    public void doIt(def variant) {
        String varName = variant.name.capitalize()
        String varDirName = variant.getDirName()

        def jarMergingTask = findJarMergingTask(project, varName)

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

    private void doInstrumentClass(String varName, String varDirName, def jarMergingTask) {

        File combindJar = getCombindJar(jarMergingTask, varName)
        //instrument jar
        File instrumentJar
        if (Util.isProguard(project, varName)) {
            instrumentJar = Util.initFile(getFileInAceso("instrument", varDirName, "main.jar"))
        } else {
            instrumentJar = Util.initFile(getFileInAceso("instrument", varDirName, "combined.jar"))
        }

        File androidJar = new File(Util.getAndroidSdkPath(project))
        if (!androidJar.exists()) {
            throw new RuntimeException("not found android jar.")
        }

        Log.i "start inject "
        ArrayList<File> classPath = new ArrayList<>()
        classPath.add(androidJar)

        File mappingFile = getFileInAceso("mapping", varDirName, "mapping.txt")
        HookWrapper.instrument(combindJar, instrumentJar, classPath, mappingFile, config.acesoMapping)

        //backup origin jar and overlay origin jar
        File classBackupDir = getFileInAceso("backup-jar", varDirName, null)
        Util.copy(project, combindJar, classBackupDir)

        combindJar.delete()

        Util.copy(project, instrumentJar, combindJar.parentFile)
    }




}
