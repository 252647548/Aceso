package com.mogujie.instantrun;

import com.google.common.collect.ImmutableMultimap;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.MethodNode;

/**
 * Verifies that a method implementation is compatible.
 */
public class InstantRunMethodVerifier {

    /**
     * Verifies a method implementation against the blacklisted list of APIs.
     */
    public static boolean verifyMethod(MethodNode method) {
        VerifierMethodVisitor mv = new VerifierMethodVisitor(method);
        method.accept(mv);
        return (mv.incompatibleChange == InstantRunVerifierStatus.INCOMPATIBLE);
    }

    public static class VerifierMethodVisitor extends MethodNode {
        InstantRunVerifierStatus incompatibleChange = InstantRunVerifierStatus.COMPATIBLE;
        public VerifierMethodVisitor(MethodNode method) {
            super(Opcodes.ASM5, method.access, method.name, method.desc, method.signature,
                    (String[]) method.exceptions.toArray(new String[method.exceptions.size()]));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                                    boolean itf) {
            Type receiver = Type.getObjectType(owner);
            if (incompatibleChange != InstantRunVerifierStatus.INCOMPATIBLE) {
                if (opcode == Opcodes.INVOKEVIRTUAL && blackListedMethods.containsKey(receiver)) {
                    for (Method method : blackListedMethods.get(receiver)) {
                        if (method.getName().equals(name) && method.getDescriptor().equals(desc)) {
                            incompatibleChange = InstantRunVerifierStatus.INCOMPATIBLE;
                        }
                    }
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }

    /**
     * If a method is called methods in the blacklist,
     * we will not instrument it.
     */
    private static final ImmutableMultimap<Type, Method> blackListedMethods =
            ImmutableMultimap.<Type, Method>builder().build();
}
