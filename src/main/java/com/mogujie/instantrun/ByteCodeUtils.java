package com.mogujie.instantrun;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.util.List;


public class ByteCodeUtils {

    public static final String CONSTRUCTOR = "<init>";
    public static final String CLASS_INITIALIZER = "<clinit>";
    private static final Type NUMBER_TYPE = Type.getObjectType("java/lang/Number");
    private static final Method SHORT_VALUE = Method.getMethod("short shortValue()");
    private static final Method BYTE_VALUE = Method.getMethod("byte byteValue()");

    
    public static void unbox(GeneratorAdapter mv, Type type) {
        if (type.equals(Type.SHORT_TYPE)) {
            mv.checkCast(NUMBER_TYPE);
            mv.invokeVirtual(NUMBER_TYPE, SHORT_VALUE);
        } else if (type.equals(Type.BYTE_TYPE)) {
            mv.checkCast(NUMBER_TYPE);
            mv.invokeVirtual(NUMBER_TYPE, BYTE_VALUE);
        } else {
            mv.unbox(type);
        }
    }

    
    public static String textify( MethodNode method) {
        Textifier textifier = new Textifier();
        TraceMethodVisitor trace = new TraceMethodVisitor(textifier);
        method.accept(trace);
        String ret = "";
        for (Object line : textifier.getText()) {
            ret += line;
        }
        return ret;
    }

    
    static void newVariableArray(
             GeneratorAdapter mv,
             List<LocalVariable> variables) {
        mv.push(variables.size());
        mv.newArray(Type.getType(Object.class));
        loadVariableArray(mv, variables, 0);
    }

    
    static void loadVariableArray(
             GeneratorAdapter mv,
             List<LocalVariable> variables, int offset) {
        // we need to maintain the stack index when loading parameters from, as for long and double
        // values, it uses 2 stack elements, all others use only 1 stack element.
        for (int i = offset; i < variables.size(); i++) {
            LocalVariable variable = variables.get(i);
            // duplicate the array of objects reference, it will be used to store the value in.
            mv.dup();
            // index in the array of objects to store the boxed parameter.
            mv.push(i);
            // Pushes the appropriate local variable on the stack
            mv.visitVarInsn(variable.type.getOpcode(Opcodes.ILOAD), variable.var);
            // potentially box up intrinsic types.
            mv.box(variable.type);
            // store it in the array
            mv.arrayStore(Type.getType(Object.class));
        }
    }

    
    static void restoreVariables(
             GeneratorAdapter mv,
             List<LocalVariable> variables) {
        for (int i = 0; i < variables.size(); i++) {
            LocalVariable variable = variables.get(i);
            // Duplicates the array on the stack;
            mv.dup();
            // Sets up the index
            mv.push(i);
            // Gets the Object value
            mv.arrayLoad(Type.getType(Object.class));
            // Unboxes to the type of the local variable
            mv.unbox(variable.type);
            // Restores the local variable
            mv.visitVarInsn(variable.type.getOpcode(Opcodes.ISTORE), variable.var);
        }
        // Pops the array from the stack.
        mv.pop();
    }

    
    static List<LocalVariable> toLocalVariables( List<Type> types) {
        List<LocalVariable> variables = Lists.newArrayList();
        int stack = 0;
        for (int i = 0; i < types.size(); i++) {
            Type type = types.get(i);
            variables.add(new LocalVariable(type, stack));
            stack += type.getSize();
        }
        return variables;
    }

    
    static Type getTypeForStoreOpcode(int opcode) {
        switch (opcode) {
            case Opcodes.ISTORE:
                return Type.INT_TYPE;
            case Opcodes.LSTORE:
                return Type.LONG_TYPE;
            case Opcodes.FSTORE:
                return Type.FLOAT_TYPE;
            case Opcodes.DSTORE:
                return Type.DOUBLE_TYPE;
            case Opcodes.ASTORE:
                return Type.getType(Object.class);
        }
        return null;
    }

    

    public static String toInternalName( String className) {
        return className.replace('.', '/');
    }

    

    public static String getClassName( String memberName) {
        Preconditions.checkArgument(memberName.contains(":"), "Class name passed as argument.");
        return memberName.substring(0, memberName.indexOf('.'));
    }

    

    public static String getPackageName( String internalName) {
        List<String> parts = Splitter.on('/').splitToList(internalName);
        if (parts.size() == 1) {
            return null;
        }
        return Joiner.on('.').join(parts.subList(0, parts.size() - 1));
    }
}
