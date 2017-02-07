package com.mogujie.aceso;

/**
 * The DSL configuration.
 *
 * @author wangzhi
 */

public class Extension {
    //base plugin
    public int logLevel = 2
    public boolean disable = false
    public String acesoMapping
    //host plugin
    public boolean disableInstrumentDebug = true

    //fix plugin
    public String modifiedJar
    public boolean methodLevelFix = true

}
