package com.mogujie.aceso

/**
 * Created by wangzhi on 17/2/8.
 */
public class Constant {
    //dir name
    public static final String INTERMEDIATES_DIR = "intermediates"
    public static final String ACESO_DIR = "aceso"
    public static final String ALL_CLASSES_DIR = "all-classes"
    public static final String MERGED_DIR = "merged"
    public static final String EXPAND_DIR = "expand"
    public static final String FIX_DIR = "fix"
    public static final String INSTRUMENT_DIR = "instrument"

    //file name
    public static final String ACESO_MAPPING = "aceso-mapping.txt"
    public static final String EXPAND_JAR = "expand.jar"
    public static final String MERGED_EXPAND_JAR = "expand-merged.jar"
    public static final String MERGED_FIX_JAR = "fix-merged.jar"
    public static final String MERGED_HOST_JAR = "host-merged.jar"
    public static final String FIX_JAR = "fix.jar"
    public static final String INSTRUMENT_JAR = "instrument.jar"
    public static final String ALL_CLASSES_JAR = "all-classes.jar"


    public static final String KEEP_RULE =
            "-keep class com.android.tools.fd.** {\n" +
                    "    *;\n" +
                    "}\n" +
                    "\n" +
                    "-keep class com.android.annotations.FixMtd { *;}"
}
