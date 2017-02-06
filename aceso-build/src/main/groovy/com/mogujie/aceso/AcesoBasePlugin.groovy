package com.mogujie.aceso

import com.mogujie.aceso.util.ProguardTool
import com.mogujie.groovy.util.Log
import com.mogujie.groovy.util.Utils
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by wangzhi on 17/1/16.
 */
abstract class AcesoBasePlugin implements Plugin<Project> {

    public static final String ACESO_DIR_NAME = "aceso"
    public static def MATCHER_R = '''.*/R\\$.*\\.class|.*/R\\.class'''

    Project project
    Extension config

    @Override
    void apply(Project project) {
        this.project = project
        project.extensions.create("Aceso", Extension);

        project.afterEvaluate {
            initExtensions()
            Log.logLevel = config.logLevel
            if (config.disable == false) {
                if (config.acesoMapping == null) {
                    config.acesoMapping = new File(project.projectDir, "aceso-mapping.txt").absolutePath
                }
                if (!Utils.checkFile(config.acesoMapping)) {
                    Log.w("aceso mapping not found!")
                }
                HookWrapper.filter = initFilter()
                realApply()
            }
        }
    }

    protected void realApply() {

    }

    protected void initExtensions() {
        config = project.extensions.findByName("Aceso") as Extension
    }

    public static HookWrapper.InstrumentFilter initFilter() {

        return new HookWrapper.InstrumentFilter() {
            @Override
            boolean accept(String name) {
                if (!name.endsWith(".class")) {
                    return false
                }

                if (name.endsWith("BuildConfig.class") || name ==~ MATCHER_R) {
                    return false
                }

                if (ProguardTool.instance().getProguardMap().size() > 0) {
                    name = ProguardTool.instance().getProguardMap().get(name)
                }

                if (name.startsWith("com/android/tools/fd/runtime")
                        || name.startsWith("com/xiaomi/")
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
}
