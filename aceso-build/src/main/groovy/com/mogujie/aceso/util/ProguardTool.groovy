package com.mogujie.aceso.util

/**
 * This class hold the proguard map.
 *
 * @author wangzhi
 */

public class ProguardTool {


    private ProguardTool() {
    }

    private static class SINGLETON {
        private final static ProguardTool instance = new ProguardTool();
    }

    public static ProguardTool instance() {
        return SINGLETON.instance;
    }
    private HashMap<String, String> proguardMap = new HashMap<>();

    HashMap<String, String> getProguardMap() {
        return proguardMap
    }

    /**
     * read mappingFile,and store in proguardMap.
     */
    public void initProguardMap(File mappingFile) {

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

    }
}
