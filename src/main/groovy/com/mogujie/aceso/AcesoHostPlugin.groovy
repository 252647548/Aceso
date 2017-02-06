package com.mogujie.aceso

import com.mogujie.aceso.processor.HostClassProcessor
import com.mogujie.aceso.transoform.HookDexTransform

/**
 * Created by wangzhi on 17/1/16.
 */
class AcesoHostPlugin extends AcesoBasePlugin {

    @Override
    protected void realApply() {
        project.android.applicationVariants.all { variant ->
            doIt(variant)
        }
    }

    public void doIt(def variant) {
        String varName = variant.name.capitalize()
        String varDirName = variant.getDirName()
        if (config.disableInstrumentDebug && varName.toLowerCase().contains("debug")) {
            return;
        }
        HookDexTransform.injectDexTransform(project,variant,new HostClassProcessor(project,varName,varDirName,config))
    }

}
