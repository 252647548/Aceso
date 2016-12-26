package com.mogujie.instantfix;

/**
 * Created by wangzhi on 16/2/25.
 */
public class Extension {
    public boolean instrument = true
    public boolean ignoreWarning=false
    public File mappingFile

    @Override
    public String toString() {
        String str =
                """
                instrument: ${instrument}
                """
        return str
    }
}
