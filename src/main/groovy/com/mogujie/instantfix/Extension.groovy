package com.mogujie.instantfix;

/**
 * Created by wangzhi on 16/2/25.
 */
public class Extension {
    public boolean instrument = true
    public boolean ignoreWarning=false
    public File instantMappingFile
    public File modifiedJar
    public boolean disable=false
    public boolean disableInstrumentDebug =true
    public boolean expandScope=false


    @Override
    public String toString() {
        String str =
                """
                instrument: ${instrument}
                """
        return str
    }
}
