package com.mogujie.aceso

import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.mogujie.aceso.util.Util
import com.mogujie.groovy.traversal.ZipTraversal
import com.mogujie.groovy.util.Log
import com.mogujie.groovy.util.Utils
import com.mogujie.instantrun.*
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * A wrapper for class real processing
 *
 * @author wangzhi
 */

public class HookWrapper {

    public interface InstrumentFilter {
        boolean accept(String name)
    }

    static InstrumentFilter filter

    public
    static void expandScope(File combindJar, File instrumentJar) {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(instrumentJar))
        ZipTraversal.traversal(combindJar, new ZipTraversal.Callback() {
            @Override
            void oneEntry(ZipEntry entry, byte[] bytes) {
                Log.i("entry: " + entry)
                if (!filter.accept(entry.getName())) {
                    return;
                }
                Log.i("accept entry: " + entry)
                zos.putNextEntry(new ZipEntry(entry.name))
                ClassReader cr = new ClassReader(bytes);
                TransformAccessClassNode cn = new TransformAccessClassNode()
                cr.accept(cn, 0)
                ClassWriter cw = new ClassWriter(0)
                cn.accept(cw)
                zos.write(cw.toByteArray())
                zos.closeEntry()
            }
        })
        zos.close()
    }

    /**
     * Instrument all class file in the inJar file,
     */
    public static void instrument(Project project, File inJar, File outJar,
                                  ArrayList<File> classPath, File mappingFile,
                                  String acesoMapping) {
        AcesoProguardMap.instance().reset()
        if (Utils.checkFile(acesoMapping)) {
            Log.i("apply instant mapping: " + acesoMapping)
            AcesoProguardMap.instance().readMapping(new File(acesoMapping))
        }
        inject(project, inJar, outJar, classPath, null, false)
        AcesoProguardMap.instance().printMapping(mappingFile)
    }

    /**
     * Generate patch class base on inJar file,
     */
    public static void fix(Project project, File inJar, File outJar,
                           ArrayList<File> classPathList, HashMap<String, String> proguardMap,
                           String acesoMapping) {
        AcesoProguardMap.instance().readMapping(new File(acesoMapping))
        inject(project, inJar, outJar, classPathList, proguardMap, true)
    }

    /**
     * if isHotfix is true,then generate the patch file,
     * else instrument the class file.
     */
    public static void inject(Project project, File inJar, File outJar,
                              ArrayList<File> classPath, HashMap<String, String> proguardMap,
                              boolean isHotfix) {
        ZipFile zipFile = new ZipFile(inJar)
        ArrayList<File> newClassPath = new ArrayList<>()
        if (classPath != null) {
            newClassPath.addAll(classPath)
        }
        File androidJar = new File(Util.getAndroidSdkPath(project))
        if (!androidJar.exists()) {
            throw new RuntimeException("not found android jar.")
        }
        newClassPath.add(androidJar)

        List<URL> classPathList = new ArrayList<>()
        classPathList.add(inJar.toURI().toURL())

        newClassPath.each { cp ->
            classPathList.add(cp.toURI().toURL())
        }

        ClassLoader classesToInstrumentLoader = new URLClassLoader(
                Iterables.toArray(classPathList, URL.class)) {
            @Override
            public URL getResource(String name) {
                // Never delegate to bootstrap classes.
                return findResource(name);
            }
        };


        ClassLoader originalThreadContextClassLoader = Thread.currentThread()
                .getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classesToInstrumentLoader);
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outJar))
            ArrayList<String> fixClassList = new ArrayList()
            zipFile.entries().each { entry ->
                if (entry.name.endsWith(".class")) {
                    if (isHotfix) {
                        fixOneEntry(zos, zipFile, entry, fixClassList, newClassPath, proguardMap)
                    } else {
                        instrumentOneEntry(zos, zipFile, entry)
                    }
                }
            }
            if (isHotfix) {
                zos.putNextEntry(new ZipEntry("com/android/tools/fd/runtime/AppPatchesLoaderImpl.class"))
                byte[] bytes = getPatchesLoaderClass(fixClassList)
                zos.write(bytes)
                zos.closeEntry()
            } else {
                Log.i("process class count : " + AcesoProguardMap.instance().nowClassIndex)
                Log.i("process mtd count : " + AcesoProguardMap.instance().nowMtdIndex)
            }
            zos.flush()
            zos.close()

        } finally {
            Thread.currentThread().setContextClassLoader(originalThreadContextClassLoader);
        }
    }

    private static void instrumentOneEntry(ZipOutputStream zos, ZipFile zipFile, ZipEntry entry) {
        if (inBlackList(entry.name)) {
            putEntry(zos, zipFile, entry)
        } else {
            processClassInternal(IncrementalSupportVisitor.VISITOR_BUILDER, zos, zipFile, entry, false)
        }
    }

    private static void fixOneEntry(ZipOutputStream zos, ZipFile zipFile, ZipEntry entry,
                                    ArrayList<String> fixClassList, ArrayList<File> classPath,
                                    HashMap<String, String> proguardMap) {
        if (!isNewClass(entry.name, classPath, proguardMap)) {
            String addEntryName = processClassInternal(IncrementalChangeVisitor.VISITOR_BUILDER, zos, zipFile, entry, true)
            if (!Utils.isStringEmpty(addEntryName)) {
                fixClassList.add(entry.name)
            }
        } else {
            fixClassList.add(entry.name)
            putEntry(zos, zipFile, entry)
        }
    }

    private
    static boolean isNewClass(String entryName, ArrayList<File> classPath, HashMap<String, String> proguardMap) {
        boolean isNewClass = true

        if (entryName.endsWith("BuildConfig.class") || entryName ==~ AcesoBasePlugin.MATCHER_R) {
            return true
        }

        classPath.each { path ->
            if (path.isFile() && (path.name.endsWith(".jar") || path.name.endsWith(".zip"))) {
                ZipFile classJar = new ZipFile(path)
                if (classJar.getEntry(entryName) != null) {
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
    static String processClassInternal(IncrementalVisitor.VisitorBuilder builder, ZipOutputStream zos,
                                       ZipFile zipFile, ZipEntry entry,
                                       boolean isHotfix) {
        String entryName = entry.name
        boolean isPatch = IncrementalVisitor.instrumentClass(entry, zipFile, zos, builder, isHotfix)
        Log.v("class ${entryName}'s mtd count : " + AcesoProguardMap.instance().getNowMtdIndex())
        entryName = pathToClassNameInPackage(entryName)
        if (isPatch) {
            return entryName
        } else {
            return null
        }
    }

    /**
     * Transform the class path to fully qualified name.
     * e.g. com/mogujie/aceso/a.class -> com.mogujie.aceso.a
     */
    private static String pathToClassNameInPackage(String path) {
        String className = path.substring(0, path.lastIndexOf(".class"))
        className = className.replace(File.separator, ".")
        return className
    }


    public static boolean inBlackList(String name) {
        if (filter == null) {
            Log.i("filter is null.")
            return false
        } else {
            return !filter.accept(name)
        }
    }

    /**
     * Generate the class that includes
     * all classes that need to be repaired.
     */
    public
    static byte[] getPatchesLoaderClass(ArrayList<String> classPathList) {
        ArrayList<String> classNames = new ArrayList<>()
        ArrayList<Integer> classIndexs = new ArrayList<>()
        for (int i = 0; i < classPathList.size(); i++) {
            String classPath = classPathList.get(i)
            classNames.add(pathToClassNameInPackage(classPath))
            String classNameInAsm = classPath.substring(0, classPath.lastIndexOf(".class"))
            AcesoProguardMap.ClassData classData = AcesoProguardMap.instance().getClassData(classNameInAsm)
            if (classData == null) {
                classIndexs.add(i, -1)
            } else {
                classIndexs.add(i, classData.getIndex())
            }
        }
        ImmutableList<String> classNameList = ImmutableList.copyOf(classNames)
        ImmutableList<Integer> classIndexList = ImmutableList.copyOf(classIndexs)
        return IncrementalTool.getPatchFileContents(classNameList, classIndexList)
    }

}
