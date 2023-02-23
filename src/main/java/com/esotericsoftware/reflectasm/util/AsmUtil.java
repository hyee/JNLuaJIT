package com.esotericsoftware.reflectasm.util;

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static com.esotericsoftware.reflectasm.util.NumberUtils.namePrimitiveMap;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

public final class AsmUtil {

    private AsmUtil() {
        throw new AssertionError();
    }

    public static boolean isSignaturePolymorphic(Method method) {
        for (Annotation annotation : method.getDeclaredAnnotations()) {
            // meh, theres definitely a better way to do this
            if (annotation.toString().contains("PolymorphicSignature")) {
                return true;
            }
        }

        return false;
    }

    public static void iconst(MethodVisitor mv, final int cst) {
        if (cst >= -1 && cst <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + cst);
        } else if (cst >= Byte.MIN_VALUE && cst <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, cst);
        } else if (cst >= Short.MIN_VALUE && cst <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, cst);
        } else {
            mv.visitLdcInsn(cst);
        }
    }

    public static void box(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.VOID:
                mv.visitInsn(ACONST_NULL);
                return;
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
            case Type.FLOAT:
            case Type.LONG:
            case Type.DOUBLE:
                Type clz = Type.getType(NumberUtils.namePrimitiveMap.get(type.getClassName()));
                mv.visitMethodInsn(INVOKESTATIC, clz.getInternalName(), "valueOf", "(" + type.getDescriptor() + ")" + clz.getDescriptor(), false);
                break;
        }
    }

    public static void unbox(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
            case Type.FLOAT:
            case Type.LONG:
            case Type.DOUBLE:
                String name = Type.getInternalName(namePrimitiveMap.get(type.getClassName()));
                mv.visitTypeInsn(CHECKCAST, name);
                mv.visitMethodInsn(INVOKEVIRTUAL, name, type.getClassName() + "Value", "()" + type.getDescriptor(), false);
                break;
            case Type.ARRAY:
                mv.visitTypeInsn(CHECKCAST, type.getDescriptor());
                break;
            case Type.OBJECT:
                mv.visitTypeInsn(CHECKCAST, type.getInternalName());
                break;
        }
    }

    public final static void setInline(MethodVisitor mv) {
        AnnotationVisitor av;
        for (String an : new String[]{"Ljava/lang/Override;", "Ljava/lang/invoke/ForceInline;", "Ljava/lang/invoke/LambdaForm$Compiled;"}) {
            av = mv.visitAnnotation(an, true);
            av.visitEnd();
        }
        mv.visitCode();
    }

    public static String buildHandleSignature(boolean isStatic, Class<?> owner, Class<?> rType, Class<?>... pTypes) {
        StringBuilder builder = new StringBuilder("(");
        if (!isStatic) {
            builder.append(Type.getDescriptor(owner));
        }

        for (Class<?> pType : pTypes) {
            builder.append(Type.getDescriptor(pType));
        }
        String ret = builder.append(")").append(Type.getDescriptor(rType)).toString();
        return ret;
    }

    public static boolean exists(String clazz) {
        try {
            Class.forName(clazz, false, AsmUtil.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static void loadParams(MethodVisitor mv, boolean isStatic, Class<?>[] types) {
        for (int i = 0, var = isStatic ? 0 : 1; i < types.length; i++, var++) {
            Type type = Type.getType(types[i]);
            loadParam(mv, type, var);
            if (type.getSort() == Type.DOUBLE || type.getSort() == Type.LONG) {
                var++;
            }
        }
    }

    public static void loadParam(MethodVisitor mv, Type type, int var) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                mv.visitVarInsn(ILOAD, var);
                return;
            case Type.FLOAT:
                mv.visitVarInsn(FLOAD, var);
                return;
            case Type.LONG:
                mv.visitVarInsn(LLOAD, var);
                return;
            case Type.DOUBLE:
                mv.visitVarInsn(DLOAD, var);
            case Type.VOID:
                return;
            default:
                mv.visitVarInsn(ALOAD, var);
        }
    }

    public static void makeReturn(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                mv.visitInsn(IRETURN);
                return;
            case Type.FLOAT:
                mv.visitInsn(FRETURN);
                return;
            case Type.LONG:
                mv.visitInsn(LRETURN);
                return;
            case Type.DOUBLE:
                mv.visitInsn(DRETURN);
                return;
            case Type.VOID:
                mv.visitInsn(RETURN);
                return;
            default:
                mv.visitInsn(ARETURN);
        }
    }

    public static String[] internalTypeArray(Class<?>... types) {
        String[] result = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            result[i] = Type.getInternalName(types[i]);
        }

        return result;
    }

}
