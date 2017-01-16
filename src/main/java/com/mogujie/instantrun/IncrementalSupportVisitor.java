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

package com.mogujie.instantrun;


import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.ObjectStreamClass;
import java.util.*;

/**
 * Visitor for classes that will eventually be replaceable at runtime.
 * <p/>
 * Since classes cannot be replaced in an existing class loader, we use a delegation model to
 * redirect any method implementation to the AndroidInstantRuntime.
 * <p/>
 * This redirection happens only when a new class implementation is available. A new version
 * will register itself in a static synthetic field called $change. Each method will be enhanced
 * with a piece of code to check if a new version is available by looking at the $change field
 * and redirect if necessary.
 * <p/>
 * Redirection will be achieved by calling a
 * {@code IncrementalChange#access$dispatch(String, Object...)} method.
 */
public class IncrementalSupportVisitor extends IncrementalVisitor {


    @NonNull
    private static final ILogger LOG = LoggerWrapper.getLogger(IncrementalSupportVisitor.class);

    private boolean disableRedirectionForClass = false;

    private static final class VisitorBuilder implements IncrementalVisitor.VisitorBuilder {

        private VisitorBuilder() {
        }

        @NonNull
        @Override
        public IncrementalVisitor build(
                @NonNull ClassNode classNode,
                @NonNull List<ClassNode> parentNodes,
                @NonNull ClassVisitor classVisitor) {
            return new IncrementalSupportVisitor(classNode, parentNodes, classVisitor);
        }

        @Override
        @NonNull
        public String getMangledRelativeClassFilePath(@NonNull String originalClassFilePath) {
            return originalClassFilePath;
        }

        @NonNull
        @Override
        public OutputType getOutputType() {
            return OutputType.INSTRUMENT;
        }
    }

    @NonNull
    public static final IncrementalVisitor.VisitorBuilder VISITOR_BUILDER = new VisitorBuilder();


    public IncrementalSupportVisitor(
            @NonNull ClassNode classNode,
            @NonNull List<ClassNode> parentNodes,
            @NonNull ClassVisitor classVisitor) {
        super(classNode, parentNodes, classVisitor);
    }

    /**
     * Ensures that the class contains a $change field used for referencing the IncrementalChange
     * dispatcher.
     * <p/>
     * <p>Also updates package_private visibility to public so we can call into this class from
     * outside the package.
     * <p/>
     * <p>All classes will have a serialVersionUID added (if one does not already exist), as
     * otherwise, serialVersionUID would be different for instrumented and non-instrumented classes.
     * We do this for all classes. Due to incremental changes, there could be a class that starts
     * implementing {@link java.io.Serializable}, thus making all of its subclasses serializable as
     * well. Those subclasses might be used for persistence, and we need to make sure their
     * serialVersionUID are stable across instant run and non-instant run builds.
     */
    @Override
    public void visit(int version, int access, String name, String signature, String superName,
                      String[] interfaces) {
        visitedClassName = name;
        visitedSuperName = superName;

        InstantProguardMap.instance().putClass(visitedClassName);
//        addSerialUidIfMissing();
//        super.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
//                        | Opcodes.ACC_VOLATILE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_TRANSIENT,
//                "$change", getRuntimeTypeName(MTD_MAP_TYPE), null, null);
        access = InstantRunTool.transformClassAccessForInstantRun(access);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        int newAccess =
                access & (~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PUBLIC;
        super.visitInnerClass(name, outerName, innerName, newAccess);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (desc.equals(DISABLE_ANNOTATION_TYPE.getDescriptor())) {
            disableRedirectionForClass = true;
        }
        return super.visitAnnotation(desc, visible);
    }


    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature,
                                   Object value) {
//        InstantProguardMap.instance().putField(InstantRunTool.getFieldSig(name, desc));
//        access = transformAccessForInstantRun(access);
        //make all field to public
        access &= ~Opcodes.ACC_PROTECTED;
        access &= ~Opcodes.ACC_PRIVATE;
        access = access | Opcodes.ACC_PUBLIC;
        return super.visitField(access, name, desc, signature, value);
    }

    /**
     * Insert Constructor specific logic({@link ConstructorRedirection} and
     * {@link ConstructorBuilder}) for constructor redirecting or
     * normal method redirecting ({@link MethodRedirection}) for other methods.
     */
    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                     String[] exceptions) {
        InstantProguardMap.instance().putMethod(InstantRunTool.getMtdSig(name, desc));
        access = InstantRunTool.transformAccessForInstantRun(access);

        MethodVisitor defaultVisitor = super.visitMethod(access, name, desc, signature, exceptions);
        MethodNode method = getMethodByNameInClass(name, desc, classNode);
        // does the method use blacklisted APIs.
        boolean hasIncompatibleChange = InstantRunMethodVerifier.verifyMethod(method)
                != InstantRunVerifierStatus.COMPATIBLE;

        if (hasIncompatibleChange || disableRedirectionForClass
                || !isAccessCompatibleWithInstantRun(access)
                || name.equals(ByteCodeUtils.CLASS_INITIALIZER)) {
            return defaultVisitor;
        } else {
            ArrayList<Type> args = new ArrayList<Type>(Arrays.asList(Type.getArgumentTypes(desc)));
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            if (!isStatic) {
                args.add(0, Type.getType(Object.class));
            }


            ISMethodVisitor mv = new ISMethodVisitor(defaultVisitor, access, name, desc);
            if (name.equals(ByteCodeUtils.CONSTRUCTOR)) {

            } else {
                mv.addRedirection(new MethodRedirection(
                        new LabelNode(mv.getStartLabel()),
                        visitedClassName,
                        name,
                        desc,
                        args,
                        Type.getReturnType(desc), isStatic));
            }
            method.accept(mv);
            return null;
        }
    }



    private class ISMethodVisitor extends GeneratorAdapter {

        private boolean disableRedirection = false;
        private int change;
        private final List<Type> args;
        private final List<Redirection> redirections;
        private final Map<Label, Redirection> resolvedRedirections;
        private final Label start;

        public ISMethodVisitor(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM5, mv, access, name, desc);
            this.change = -1;
            this.redirections = new ArrayList();
            this.resolvedRedirections = new HashMap();
            this.args = new ArrayList(Arrays.asList(Type.getArgumentTypes(desc)));
            this.start = new Label();
            boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
            // if this is not a static, we add a fictional first parameter what will contain the
            // "this" reference which can be loaded with ILOAD_0 bytecode.
            if (!isStatic) {
                args.add(0, Type.getType(Object.class));
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (desc.equals(DISABLE_ANNOTATION_TYPE.getDescriptor())) {
                disableRedirection = true;
            }
            return super.visitAnnotation(desc, visible);
        }

        /**
         * inserts a new local '$change' in each method that contains a reference to the type's
         * IncrementalChange dispatcher, this is done to avoid threading issues.
         * <p/>
         * Pseudo code:
         * <code>
         * $package/IncrementalChange $local1 = $className$.$change;
         * </code>
         */
        @Override
        public void visitCode() {
            if (!disableRedirection) {
                // Labels cannot be used directly as they are volatile between different visits,
                // so we must use LabelNode and resolve before visiting for better performance.
                for (Redirection redirection : redirections) {
                    resolvedRedirections.put(redirection.getPosition().getLabel(), redirection);
                }

                super.visitLabel(start);
                change = newLocal(MTD_MAP_TYPE);
//                visitFieldInsn(Opcodes.GETSTATIC, visitedClassName, "$change",
//                        getRuntimeTypeName(MTD_MAP_TYPE));
                push(new Integer(InstantProguardMap.instance().getNowClassIndex()));
                push(new Integer(InstantProguardMap.instance().getNowMtdIndex()));
                invokeStatic(IncrementalVisitor.MTD_MAP_TYPE, Method.getMethod("com.android.tools.fd.runtime.IncrementalChange get(int,int)"));

                storeLocal(change);

                redirectAt(start);
            }
            super.visitCode();
        }

        @Override
        public void visitLabel(Label label) {
            super.visitLabel(label);
            redirectAt(label);
        }

        private void redirectAt(Label label) {
            if (disableRedirection) return;
            Redirection redirection = resolvedRedirections.get(label);
            if (redirection != null) {
                // A special line number to mark this area of code.
                super.visitLineNumber(0, label);
                redirection.redirect(this, change);
            }
        }

        public void addRedirection(@NonNull Redirection redirection) {
            redirections.add(redirection);
        }


        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start,
                                       Label end, int index) {
            // In dex format, the argument names are separated from the local variable names. It
            // seems to be needed to declare the local argument variables from the beginning of
            // the methods for dex to pick that up. By inserting code before the first label we
            // break that. In Java this is fine, and the debugger shows the right thing. However
            // if we don't readjust the local variables, we just don't see the arguments.
            if (!disableRedirection && index < args.size()) {
                start = this.start;
            }
            super.visitLocalVariable(name, desc, signature, start, end, index);
        }

        public Label getStartLabel() {
            return start;
        }
    }

}
