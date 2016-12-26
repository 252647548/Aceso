package instant;

import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Created by wangzhi on 16/12/6.
 */
public class InstantRunTool {

    public static byte[] getPatchFileContents(
            @NonNull ImmutableList<String> patchFileContents, @NonNull ImmutableList<Integer> patchIndexContents) {
if(patchFileContents.size()!=patchIndexContents.size()){
    throw new GradleException("patchFileContents's size is "
            +patchFileContents.size()+", but patchIndexContents's size is "
            +patchIndexContents.size()+", please check the changed classes.");
}
        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                IncrementalVisitor.APP_PATCHES_LOADER_IMPL, null,
                IncrementalVisitor.ABSTRACT_PATCHES_LOADER_IMPL, null);

        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    IncrementalVisitor.ABSTRACT_PATCHES_LOADER_IMPL,
                    "<init>", "()V", false);
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC,
                    "getPatchedClasses", "()[Ljava/lang/String;", null, null);
            mv.visitCode();

            mv.visitIntInsn(Opcodes.SIPUSH, patchFileContents.size());
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
            for (int index = 0; index < patchFileContents.size(); index++) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitIntInsn(Opcodes.SIPUSH, index);
                mv.visitLdcInsn(patchFileContents.get(index));
                mv.visitInsn(Opcodes.AASTORE);
            }
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(4, 1);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(Opcodes.ACC_PUBLIC,
                    "getPatchedClassIndexes", "()[I", null, null);
            mv.visitCode();

            mv.visitIntInsn(Opcodes.SIPUSH, patchIndexContents.size());
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);
            for (int index = 0; index < patchIndexContents.size(); index++) {
                mv.visitInsn(Opcodes.DUP);
                mv.visitIntInsn(Opcodes.SIPUSH, index);
                mv.visitLdcInsn(patchIndexContents.get(index));
                mv.visitInsn(Opcodes.IASTORE);
            }
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(4, 1);
            mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();

    }

    public static String getMtdSig(String mtdName, String mtdDesc) {
        return mtdName + "." + mtdDesc;
    }
}
