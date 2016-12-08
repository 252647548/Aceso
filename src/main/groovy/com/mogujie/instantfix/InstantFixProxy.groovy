package com.mogujie.instantfix

import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.mogujie.groovy.util.Log
import com.mogujie.groovy.util.Utils
import instant.IncrementalChangeVisitor
import instant.IncrementalSupportVisitor
import instant.IncrementalVisitor
import instant.InstantRunUtil

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Created by wangzhi on 16/12/5.
 */
class InstantFixProxy {

    public static void instrument(File combindJar, File supportJar, File androidJar) {
        inject(combindJar, supportJar, androidJar, IncrementalSupportVisitor.VISITOR_BUILDER, false)
    }


    public
    static void inject(File combindJar, File outJar, File androidJar, IncrementalVisitor.VisitorBuilder builder, boolean isHotfix) {
        ZipFile zipFile = new ZipFile(combindJar)
        List<URL> classPathList = new ArrayList<>()
        classPathList.add(combindJar.toURI().toURL())
        classPathList.add(androidJar.toURI().toURL())
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
                if (isHotfix || support(entryName)) {
                    count++;
                    IncrementalVisitor.instrumentClass(entry, zipFile, zos, builder, isHotfix)
                    mtdCount += IncrementalVisitor.mtdCount;
                    Log.i("class ${entryName}'s mtd count : " + IncrementalVisitor.mtdCount)
                    IncrementalVisitor.mtdCount = 0;
                    entryName = entryName.substring(0, entryName.lastIndexOf(".class"))
                    entryName = entryName.replace(File.separator, ".")
                    fixClassList.add(entryName)
                } else {
                    ZipEntry newEntry = new ZipEntry(entry.name)
                    zos.putNextEntry(newEntry)
                    zos.write(Utils.toByteArray(zipFile.getInputStream(entry)))
                    zos.closeEntry()
                }
            }
            if (isHotfix) {
                zos.putNextEntry(new ZipEntry("com/android/tools/fd/runtime/AppPatchesLoaderImpl.class"))
                byte[] bytes = getPatchesLoaderClass(fixClassList)
                zos.write(bytes)
                zos.closeEntry()
            }
            zos.flush()
            zos.close()
            Log.i("process class count : " + count)
            Log.i("process mtd count : " + mtdCount)
        } finally {
            Thread.currentThread().setContextClassLoader(originalThreadContextClassLoader);
        }
    }

    static def MATCHER_R = '''.*/R\\$.*\\.class|.*/R\\.class'''

    public static boolean support(String name) {
        if (!name.endsWith(".class")) {
            return false
        }
        if (name.endsWith("BuildConfig.class") || name ==~ MATCHER_R) {
            Log.v("skip BuildConfig or R ==>  " + name)
            return false
        }
        if (name.startsWith("com/mogujie/")) {
            return true
        } else {
            return false
        }
//        if (name.startsWith("com/xiaomi/")
//                || name.startsWith("org/apache/")
//                || name.startsWith("com/android/")
//                || name.startsWith("com/google/")
//                || name.startsWith("android/")
//                || name.startsWith("org/")
//                || name.startsWith("okhttp3/")) {
//            return false
//        } else {
//            return true
//        }
    }

    public static void hotfix(File combindJar, File instrumentJar, File androidJar) {
        inject(combindJar, instrumentJar, androidJar, IncrementalChangeVisitor.VISITOR_BUILDER, true)

    }

    public static byte[] getPatchesLoaderClass(ArrayList list) {
        ImmutableList<String> immutableList = ImmutableList.copyOf(list)
        return InstantRunUtil.getPatchFileContents(immutableList, 1)
    }

}
