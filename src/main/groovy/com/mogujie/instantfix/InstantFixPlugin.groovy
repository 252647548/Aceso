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
        String varDirName = variant.getDirName()

        def jarMergingTask = findJarMergingTask(project, varName)

        project.tasks.create("instantFix" + varName, HotFixTask, new HotFixTask.HotFixAction(varName))

        isHotfix = InstantUtil.isInstantFix(project)

        if (isHotfix) {
            Log.i "is hotfix "

            if (jarMergingTask == null) {
                doPatchWhenJarNotExists(varName, varDirName)
            } else {
                doPatchWhenJarExists(jarMergingTask, varName, varDirName)
            }
            return
        } else {
            InstantFixWrapper.filter = initFilter()
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
                doInstrumentClass(varName, varDirName, jarMergingTask)
            }

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
        if (InstantUtil.isProguard(project, varName)) {

            instrumentJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-instrument/${varDirName}/main.jar")
        } else {
            instrumentJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-instrument/${varDirName}/combined.jar")
        }

        File androidJar = new File(InstantUtil.getAndroidSdkPath(project))
        if (!androidJar.exists()) {
            throw new RuntimeException("not found android jar.")
        }

        Log.i "start inject "
        ArrayList<File> classPath = new ArrayList<>()
        classPath.add(androidJar)
        File mappingFile = new File(project.buildDir, "intermediates/instant-mapping/${varDirName}/mapping.txt")
        InstantFixWrapper.instrument(combindJar, instrumentJar, classPath, mappingFile)

        //backup origin jar and overlay origin jar
        File classBackupDir = new File(project.buildDir, "intermediates/jar-backup/${varDirName}")

        InstantUtil.copy(project, combindJar, classBackupDir)

        combindJar.delete()

        InstantUtil.copy(project, instrumentJar, combindJar.parentFile)
    }

    private void doPatchWhenJarNotExists(String varName, String varDirName) {
        Log.i("jarMergingTask is null")
        AndroidJavaCompile classTask = project.tasks.findByName("compile${varName}JavaWithJavac")
        classTask.doLast {
            File tempJarFile = InstantUtil.initFile(project.buildDir, "intermediates/class-hotfix-jar/${varDirName}/backup.jar")
            File jarFile = InstantUtil.initFile(project.buildDir, "intermediates/class-hotfix-jar/${varDirName}/allclasses.jar")
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
            classPath.add(new File(InstantUtil.getAndroidSdkPath(project)))
            classPath.addAll(classTask.classpath.files)

            InstantFixWrapper.instantFix(tempJarFile, jarFile, classPath, null, config.mappingFile)
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
                proguardMap = getProguardMap(jarMergingTask.transform.getMappingFile())
                Log.i("get proguard map, size: " + proguardMap.size())
                proguardMap.each { key, value -> Log.i(key + "<>" + value) }
            }

            File combindJar = getCombindJar(jarMergingTask, varName)
            Log.i "combindJar is " + combindJar
            File combindBackupJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-backup/${varDirName}/" + combindJar.name)

            File fixJar = InstantUtil.initFile(project.buildDir, "intermediates/jar-hotfix/${varDirName}/" + combindJar.name)
            ArrayList<File> classPath = new ArrayList()
            classPath.addAll(classTask.classpath.files)
            classPath.add(new File(InstantUtil.getAndroidSdkPath(project)))
            InstantFixWrapper.instantFix(combindJar, fixJar, classPath, proguardMap, config.mappingFile)

            InstantUtil.copy(project, combindJar, combindBackupJar.parentFile)

            InstantUtil.copy(project, fixJar, combindJar.parentFile)
        }
    }

    public HashMap<String, String> getProguardMap(File mappingFile) {
        HashMap<String, String> proguardMap = new HashMap<>()
        mappingFile.readLines().each { line ->
            line = line.trim();

            // Is it a non-comment line?
            if (!line.startsWith("#")) {
                // Is it a class mapping or a class member mapping?
                if (line.endsWith(":")) {


                    int arrowIndex = line.indexOf("->");
                    if (arrowIndex < 0) {
                        return null;
                    }

                    int colonIndex = line.indexOf(':', arrowIndex + 2);
                    if (colonIndex < 0) {
                        return null;
                    }

                    // Extract the elements.
                    String className = line.substring(0, arrowIndex).trim();
                    String newClassName = line.substring(arrowIndex + 2, colonIndex).trim();
                    proguardMap.put(newClassName.replace(".", "/") + ".class", className.replace(".", "/") + ".class")

                }

            }
        }
        return proguardMap
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

//                if (name.startsWith("com/mogujie/") || name.startsWith("com/mogu/") || name.startsWith("com/xiaodian/")
//                        || name.startsWith("com/minicooper/") || name.startsWith("com/squareup/picasso")
//                        || name.startsWith("com/nineold/animation")
//                        || name.startsWith("com/astonmartin/image") || name.startsWith("com/caches") || name.startsWith("com/facebook")) {
//                    return true
//                } else {
//                    return false
//                }

                Log.i("filter accept => " + name)
                if (name.startsWith("com/xiaomi/")
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
        if (InstantUtil.isProguard(project, varName)) {
            combindJar = IntermediateFolderUtils.getContentLocation(jarMergingDir, "main", transform.getOutputTypes(), transform.getScopes(), Format.JAR);
        } else {
            combindJar = IntermediateFolderUtils.getContentLocation(jarMergingDir, "combined", transform.getOutputTypes(), transform.getScopes(), Format.JAR);
        }
        Log.i "combind jar : " + combindJar
        return combindJar
    }

}
