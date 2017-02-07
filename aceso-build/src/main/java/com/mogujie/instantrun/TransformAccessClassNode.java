package com.mogujie.instantrun;

import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * A ClassNode used for expanding the method and field's scope
 *
 * @author wangzhi
 */
public class TransformAccessClassNode extends ClassNode {

    public TransformAccessClassNode() {
        super(Opcodes.ASM5);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        access = IncrementalTool.transformAccessForInstantRun(access);
        return super.visitMethod(access, name, desc, signature, exceptions);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        access = IncrementalTool.transformAccessToPublic(access);
        return super.visitField(access, name, desc, signature, value);
    }
}
