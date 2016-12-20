package com.mogujie.instantfix

import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.mogujie.groovy.util.Log
import com.mogujie.groovy.util.Utils
import instant.IncrementalChangeVisitor
import instant.IncrementalSupportVisitor
import instant.IncrementalVisitor
import instant.InstantRunTool
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
    static void instrument(File combindJar, File supportJar, ArrayList<File> classPath) {
        inject(combindJar, supportJar, classPath, null, IncrementalSupportVisitor.VISITOR_BUILDER, false)
    }

    public
    static void instantFix(File combindJar, File instrumentJar, ArrayList<File> classPathList, HashMap<String, String> proguardMap) {
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
                        mtdCount += IncrementalVisitor.mtdCount;
                        IncrementalVisitor.mtdCount = 0;
                    } else {
                        putEntry(zos, zipFile, entry)
                    }

                } else {
                    if (!isNewClass(entryName, classPath, proguardMap)) {
                        String addEntryName = instrumentClassInternal(builder, zos, zipFile, entry, entryName, isHotfix)
                        if (!Utils.isStringEmpty(addEntryName)) {
                            fixClassList.add(addEntryName)
                        }
                    } else {
                        fixClassList.add(pathToClassName(entryName))
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
        if (proguardMap == null) {
            return false
        }
        classPath.each { path ->
            if (path.isFile() && (path.name.endsWith(".jar") || path.name.endsWith(".zip"))) {
                ZipFile classJar = new ZipFile(path)

                //获得混淆之前的类名
                String realName = proguardMap.get(entryName)
                if (realName != null && classJar.getEntry(realName) != null) {
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
        Log.i("class ${entryName}'s mtd count : " + IncrementalVisitor.mtdCount)
        entryName = pathToClassName(entryName)
        if (isPatch) {
            return entryName
        } else {
            return null
        }
    }

    private static String pathToClassName(String path) {
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


    public static byte[] getPatchesLoaderClass(ArrayList list) {
        ImmutableList<String> immutableList = ImmutableList.copyOf(list)
        return InstantRunTool.getPatchFileContents(immutableList, 1)
    }


}
