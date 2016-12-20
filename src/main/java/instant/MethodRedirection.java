/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package instant;

import com.mogujie.groovy.util.Log;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.LabelNode;

import java.util.List;

public class MethodRedirection extends Redirection {

    /**
     * The name of the method we redirect to.
     */
    @NonNull
    private final String name;

    @NonNull
    private String mtdName;
    private String mtdDesc;

//    MethodNode method;


    String visitedClassName;

    MethodRedirection(@NonNull LabelNode label, @NonNull String name, @NonNull List<Type> types,
                      @NonNull Type type, String visitedClassName, String mtdName, String mtdDesc) {
        super(label, types, type);
        this.name = name;
        this.mtdDesc = mtdDesc;
        this.mtdName = mtdName;
        this.visitedClassName = visitedClassName;
    }

    @Override
    protected void redirect(GeneratorAdapter mv, int change) {
        // code to check if a new implementation of the current class is available.
        Label l0 = new Label();

        String mtdSetTypeDesc = IncrementalVisitor.getRuntimeTypeName(IncrementalVisitor.MTD_SET_TYPE);
        Log.i("redirect: " + visitedClassName + " " + mtdSetTypeDesc + "  " + mtdName + "  " + mtdDesc + "  " + name);
        mv.visitFieldInsn(Opcodes.GETSTATIC, visitedClassName, "$mtdSet",
                mtdSetTypeDesc);
        mv.visitLdcInsn(name);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, mtdSetTypeDesc, mtdName, mtdDesc, false);
        mv.visitJumpInsn(Opcodes.IFEQ, l0);

        mv.loadLocal(change);
        mv.visitJumpInsn(Opcodes.IFNULL, l0);


        doRedirect(mv, change);

        // Return
        if (type == Type.VOID_TYPE) {
            mv.pop();
        } else {
            ByteCodeUtils.unbox(mv, type);
        }
        mv.returnValue();

        // jump label for classes without any new implementation, just invoke the original
        // method implementation.
        mv.visitLabel(l0);
    }

    @Override
    protected void doRedirect(@NonNull GeneratorAdapter mv, int change) {
        // Push the three arguments
        mv.loadLocal(change);
        mv.push(name);
        ByteCodeUtils.newVariableArray(mv, ByteCodeUtils.toLocalVariables(types));

        // now invoke the generic dispatch method.
        mv.invokeInterface(IncrementalVisitor.CHANGE_TYPE, Method.getMethod("Object access$dispatch(String, Object[])"));
    }
}
