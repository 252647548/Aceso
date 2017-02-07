package com.mogujie.aceso;

/**
 * The DSL configuration.
 *
 * @author wangzhi
 */

public class Extension {

    /*-------------Configuration for AcesoBasePlugin-------------*/

    /**
     * The log level ,value range from 1-3,low to high.
     */
    public int logLevel = 2

    /**
     * Disable the plugin.
     */
    public boolean disable = false

    /**
     *  BlackList for class
     */
    public String blackListPath


    /**
     * The aceso mapping.
     * For host plugn,it is use for keeping this aceso-proguard's compilation result.
     * For fix plugin,it is use for generate patch apk.
     */
    public String acesoMapping

    /*-------------Configuration for AcesoHostPlugin-------------*/

    /**
     * Open the incrument in debug compile.
     */
    public boolean disableInstrumentDebug = true

    /*-------------Configuration for AcesoFixPlugin-------------*/

    /**
     * the modifiedJar's path
     */
    public String modifiedJar

    /**
     * Is method level fix? If true,you need add annotation
     *  com.android.annotations.FixMtd to the method that you want to fix.
     */
    public boolean methodLevelFix = true

}
