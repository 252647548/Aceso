package com.mogujie.instantrun;

import com.mogujie.groovy.util.Log;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.List;

/**
 * Created by wangzhi on 16/12/27.
 */
public class SuperHelperVisitor extends ClassWriter implements Opcodes {
    IncrementalChangeVisitor visitor;
    ClassNode superNode;

    public SuperHelperVisitor(int api, IncrementalChangeVisitor visitor, ClassNode superNode) {
        super(api);
        this.visitor = visitor;
        this.superNode = superNode;
    }

    public void start() {
        visit(Opcodes.V1_7, ACC_PUBLIC + ACC_SUPER, visitor.visitedClassName + "$helper", null, visitor.visitedSuperName, null);
        for (MethodNode methodNode : superNode.methods) {
            if ("<init>".equals(methodNode.name)) {
                String[] exceptions = null;
                if (methodNode.exceptions != null) {
                    exceptions = methodNode.exceptions.toArray(new String[0]);
                }
                MethodVisitor mv = visitMethod(ACC_PUBLIC, methodNode.name, methodNode.desc, methodNode.signature, exceptions);
                mv.visitCode();
                Type[] args = Type.getArgumentTypes(methodNode.desc);
                List<LocalVariable> variables = ByteCodeUtils.toLocalVariables(Arrays.asList(args));
                mv.visitVarInsn(ALOAD, 0);
                int local = 1;
                for (int i = 0; i < variables.size(); i++) {
                    mv.visitVarInsn(variables.get(i).type.getOpcode(Opcodes.ILOAD), variables.get(i).var + 1);
                    local = variables.get(i).var + 1+variables.get(i).type.getSize();
                }
                mv.visitMethodInsn(INVOKESPECIAL, superNode.name, methodNode.name, methodNode.desc, false);
                mv.visitInsn(RETURN);
                mv.visitMaxs(local, local);
                mv.visitEnd();
            }
        }

        for (InstantMethod method : visitor.superMethods) {
            Log.i("Super: " + method.getName() + "<>" + method.getDescriptor());
            MethodVisitor mv = visitMethod(ACC_PUBLIC + ACC_STATIC, method.getName(), method.getDescriptor(), null, null);
            mv.visitCode();
            Type[] args = Type.getArgumentTypes(method.getDescriptor());

            List<LocalVariable> variables = ByteCodeUtils.toLocalVariables(Arrays.asList(args));
            int totSize = 1;
            for (LocalVariable variable : variables) {

                mv.visitVarInsn(variable.type.getOpcode(Opcodes.ILOAD), variable.var);
                totSize = variable.var;
            }

            mv.visitMethodInsn(INVOKESPECIAL, method.getOwner(), method.getName(), method.getOriDesc(), false);


            Type returnType = Type.getReturnType(method.getDescriptor());
            mv.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            mv.visitMaxs(totSize + 1, totSize + 1);
            mv.visitEnd();
        }

        visitEnd();
    }


    public void push(MethodVisitor mv, final int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }


}
