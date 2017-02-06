package com.mogujie.instantrun;

import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * Created by wangzhi on 16/12/28.
 */
public class TransformAccessClassNode extends ClassNode {

    public TransformAccessClassNode() {
        super(Opcodes.ASM5);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        access = InstantRunTool.transformAccessForInstantRun(access);
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        access = InstantRunTool.transformAccessToPublic(access);
        return super.visitField(access, name, desc, signature, value);
    }
}
