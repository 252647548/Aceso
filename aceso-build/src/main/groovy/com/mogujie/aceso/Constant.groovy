package com.mogujie.aceso

/**
 * Created by wangzhi on 17/2/8.
 */
public class Constant {
    public static final String ACESO_DIR_NAME = "aceso"
    public static final String ALL_CLASSES_DIR_NAME = "all-classes"
    public static final String KEEP_RULE =
            "-keep class com.android.tools.fd.** {\n" +
                    "    *;\n" +
                    "}\n" +
                    "\n" +
                    "-keep class com.android.annotations.FixMtd { *;}"
}
