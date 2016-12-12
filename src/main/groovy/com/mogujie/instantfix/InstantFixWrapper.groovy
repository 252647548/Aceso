package com.mogujie.instantfix
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.mogujie.groovy.util.Log
import com.mogujie.groovy.util.Utils
import instant.IncrementalChangeVisitor
import instant.IncrementalSupportVisitor
import instant.IncrementalVisitor
import instant.InstantRunTool

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
                   boolean isPatch= IncrementalVisitor.instrumentClass(entry, zipFile, zos, builder, isHotfix)
                    mtdCount += IncrementalVisitor.mtdCount;
                    Log.i("class ${entryName}'s mtd count : " + IncrementalVisitor.mtdCount)
                    IncrementalVisitor.mtdCount = 0;
                    entryName = entryName.substring(0, entryName.lastIndexOf(".class"))
                    entryName = entryName.replace(File.separator, ".")
                    if(isPatch){
                        fixClassList.add(entryName)
                    }
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



    public static boolean support(String name) {
        if (filter == null) {
            Log.i("filter is null.")
            return true
        } else {
            return filter.accept(name)
        }
    }

    public static void hotfix(File combindJar, File instrumentJar, File androidJar) {
        inject(combindJar, instrumentJar, androidJar, IncrementalChangeVisitor.VISITOR_BUILDER, true)

    }

    public static byte[] getPatchesLoaderClass(ArrayList list) {
        ImmutableList<String> immutableList = ImmutableList.copyOf(list)
        return InstantRunTool.getPatchFileContents(immutableList, 1)
    }



}
