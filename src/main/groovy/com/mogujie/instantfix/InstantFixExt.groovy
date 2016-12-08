package com.mogujie.instantfix;

/**
 * Created by wangzhi on 16/2/25.
 */
public class InstantFixExt {
    public boolean instrument = true

    @Override
    public String toString() {
        String str =
                """
                instrument: ${instrument}
                """
        return str
    }
}
