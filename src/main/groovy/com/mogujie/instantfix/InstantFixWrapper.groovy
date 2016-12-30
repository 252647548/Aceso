package com.mogujie.instantfix

import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.mogujie.groovy.util.Log
import com.mogujie.groovy.util.Utils
import instant.*
import org.gradle.api.GradleException

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Created by wangzhi on 16/12/5.
 */
class InstantFixWrapper {

    public interface InstrumentFilter {
        boolean accept(String name)
    }

    static InstrumentFilter filter

    public
    static void instrument(File combindJar, File supportJar, ArrayList<File> classPath, File mappingFile) {
        InstantProguardMap.instance().reset()
        inject(combindJar, supportJar, classPath, null, IncrementalSupportVisitor.VISITOR_BUILDER, false)
        InstantProguardMap.instance().printMapping(mappingFile)
    }

    public
    static void instantFix(File combindJar, File instrumentJar, ArrayList<File> classPathList, HashMap<String, String> proguardMap, File instantFixMapping) {
        InstantProguardMap.instance().readMapping(instantFixMapping)
        inject(combindJar, instrumentJar, classPathList, proguardMap, IncrementalChangeVisitor.VISITOR_BUILDER, true)
    }

    public
    static void inject(File combindJar, File outJar, ArrayList<File> classPath, HashMap<String, String> proguardMap, IncrementalVisitor.VisitorBuilder builder, boolean isHotfix) {
        ZipFile zipFile = new ZipFile(combindJar)
        List<URL> classPathList = new ArrayList<>()
        classPathList.add(combindJar.toURI().toURL())
        classPath.each { cp ->
            classPathList.add(cp.toURI().toURL())
        }
        URL[] classPathAarray = Iterables.toArray(classPathList, URL.class)
        ClassLoader classesToInstrumentLoader = new URLClassLoader(classPathAarray) {
            @Override
            public URL getResource(String name) {
                // Never delegate to bootstrap classes.
                return findResource(name);
            }
        };


        ClassLoader originalThreadContextClassLoader = Thread.currentThread()
                .getContextClassLoader();
        try {
            int count = 0;
            int mtdCount = 0;
            Thread.currentThread().setContextClassLoader(classesToInstrumentLoader);
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outJar))
            ArrayList<String> fixClassList = new ArrayList()

            zipFile.entries().each { entry ->
                String entryName = entry.name
                if (!isHotfix) {
                    if (support(entryName)) {
                        count++;
                        instrumentClassInternal(builder, zos, zipFile, entry, entryName, isHotfix)
                    } else {
                        putEntry(zos, zipFile, entry)
                    }

                } else {
                    if (!isNewClass(entryName, classPath, proguardMap)) {
                        String addEntryName = instrumentClassInternal(builder, zos, zipFile, entry, entryName, isHotfix)
                        if (!Utils.isStringEmpty(addEntryName)) {
                            fixClassList.add(entryName)
                        }
                    } else {
                        fixClassList.add(entryName)
                        putEntry(zos, zipFile, entry)
                    }

                }
            }
            if (isHotfix) {
                zos.putNextEntry(new ZipEntry("com/android/tools/fd/runtime/AppPatchesLoaderImpl.class"))
                Log.i("fix ClassList: " + fixClassList)
                byte[] bytes = getPatchesLoaderClass(fixClassList)
                zos.write(bytes)
                zos.closeEntry()
            } else {
                Log.i("process class count : " + count)
                Log.i("process mtd count : " + mtdCount)
            }
            zos.flush()
            zos.close()

        } finally {
            Thread.currentThread().setContextClassLoader(originalThreadContextClassLoader);
        }
    }

    private
    static boolean isNewClass(String entryName, ArrayList<File> classPath, HashMap<String, String> proguardMap) {
        boolean isNewClass = true

        classPath.each { path ->
            if (path.isFile() && (path.name.endsWith(".jar") || path.name.endsWith(".zip"))) {
                ZipFile classJar = new ZipFile(path)

                //获得混淆之前的类名
//                String realName = entryName
//                if (proguardMap != null) {
//                    realName = proguardMap.get(entryName)
//                }
                if (classJar.getEntry(entryName)!=null) {
                    isNewClass = false
                }

            } else {
                throw new GradleException("unknown class path type " + path)
            }
        }
        if (isNewClass) {
            Log.i("find new class " + entryName)
        }
        return isNewClass
    }

    private static void putEntry(ZipOutputStream zos, ZipFile zipFile, ZipEntry entry) {
        ZipEntry newEntry = new ZipEntry(entry.name)
        zos.putNextEntry(newEntry)
        zos.write(Utils.toByteArray(zipFile.getInputStream(entry)))
        zos.closeEntry()
    }

    private
    static String instrumentClassInternal(IncrementalVisitor.VisitorBuilder builder, ZipOutputStream zos,
                                          ZipFile zipFile, ZipEntry entry, String entryName,
                                          boolean isHotfix) {

        boolean isPatch = IncrementalVisitor.instrumentClass(entry, zipFile, zos, builder, isHotfix)
        Log.v("class ${entryName}'s mtd count : " + InstantProguardMap.instance().getNowMtdIndex())
        entryName = pathToClassNameInPackage(entryName)
        if (isPatch) {
            return entryName
        } else {
            return null
        }
    }

    private static String pathToClassNameInPackage(String path) {
        String className = path.substring(0, path.lastIndexOf(".class"))
        className = className.replace(File.separator, ".")
        return className
    }


    public static boolean support(String name) {
        if (filter == null) {
            Log.i("filter is null.")
            return true
        } else {
            return filter.accept(name)
        }
    }


    public
    static byte[] getPatchesLoaderClass(ArrayList<String> classPathList) {
        ArrayList<String> classNames = new ArrayList<>()
        ArrayList<Integer> classIndexs = new ArrayList<>()
        for (int i = 0; i < classPathList.size(); i++) {
            String classPath = classPathList.get(i)
            classNames.add(pathToClassNameInPackage(classPath))
            String classNameInAsm = classPath.substring(0, classPath.lastIndexOf(".class"))
            InstantProguardMap.ClassData classData = InstantProguardMap.instance().getClassData(classNameInAsm)
            if (classData == null) {
                classIndexs.add(i, -1)
            } else {
                classIndexs.add(i, classData.getIndex())
            }
        }
        ImmutableList<String> classNameList = ImmutableList.copyOf(classNames)

        ImmutableList<Integer> classIndexList = ImmutableList.copyOf(classIndexs)
        return InstantRunTool.getPatchFileContents(classNameList, classIndexList)
    }


    public static void expandCode(){

    }

}
