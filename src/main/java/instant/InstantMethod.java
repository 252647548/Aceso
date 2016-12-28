package instant;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/**
 * Created by wangzhi on 16/12/27.
 */
public class InstantMethod extends Method {
    private String oriDesc;
    public InstantMethod(String name, String desc,String oriDesc) {
        super(name, desc);
        this.oriDesc=oriDesc;
    }

    public InstantMethod(String name, Type returnType, Type[] argumentTypes) {
        super(name, returnType, argumentTypes);
    }

    public String getOriDesc() {
        return oriDesc;
    }
}
