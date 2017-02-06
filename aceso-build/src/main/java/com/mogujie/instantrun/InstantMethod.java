package com.mogujie.instantrun;

import org.objectweb.asm.commons.Method;

/**
 * Created by wangzhi on 16/12/27.
 */
public class InstantMethod extends Method {
    private String oriDesc;
    private String owner;

    public InstantMethod(String owner, String name, String desc, String oriDesc) {
        super(name, desc);
        this.oriDesc = oriDesc;
        this.owner = owner;
    }

    public String getOriDesc() {
        return oriDesc;
    }


    public String getOwner() {
        return owner;
    }
}
