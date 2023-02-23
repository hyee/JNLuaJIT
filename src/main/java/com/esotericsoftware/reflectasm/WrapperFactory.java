package com.esotericsoftware.reflectasm;


import com.esotericsoftware.reflectasm.util.AsmUtil;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static com.esotericsoftware.reflectasm.ClassAccess.IS_DEBUG;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 * The WrapperFactory that produces {@link HandleWrapper}s or
 * custom HandleWrappers.
 * <p>
 * MethodHandles are basically as fast as direct invocation,
 * as long as they are <b>static final</b>. This limits them,
 * because we can't create MethodHandles dynamically and have
 * them have the same performance as <b>static final</b> ones.
 * <p>
 * So the idea is as follows: Use ASM to create a class at
 * runtime that has the given {@link MethodHandle} as a
 * <b>static final</b> field. What happens in the background
 * is roughly this:
 *
 * <blockquote><pre>{@code
 * public class ExampleClass {
 *     // Method to wrap:
 *     public ExampleClass exampleMethod(int i) { ... }
 * }
 *
 * public class SomeCreatedNameID+ implements HandleWrapper {
 *     private static final MethodHandle HANDLE;
 *
 *     static {
 *         HANDLE = Handles.getHandle(id); // ID comes from LDC instruction.
 *     }
 *
 *     public Object invoke(Object...args) throws Throwable {
 *         return HANDLE.invoke((ExampleClass) args[0], (int) args[1]);
 *     }
 *
 *     ...
 * }
 * </pre></blockquote>
 */
public class WrapperFactory {


    private WrapperFactory() {
        throw new AssertionError();
    }

    public static HandleWrapper wrapGetter(final MethodHandle handle, final Field field) throws Throwable {
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        return wrap(handle, field.getDeclaringClass(), "get_" + field.getName(), field.getModifiers(), isStatic, field.getType());
    }


    public static HandleWrapper wrapSetter(final MethodHandle handle, final Field field) throws Throwable {
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        return wrap(handle, field.getDeclaringClass(), "set_" + field.getName(), field.getModifiers(), isStatic, void.class, field.getType());
    }


    public static HandleWrapper wrapConstructor(final MethodHandle handle, final Constructor<?> constructor) throws Throwable {
        return wrap(handle, constructor.getDeclaringClass(), "<init>", constructor.getModifiers(), true, constructor.getDeclaringClass(), constructor.getParameterTypes());
    }


    public static HandleWrapper wrap(final MethodHandle handle, final Method method) throws Throwable {
        return wrap(handle, method.getDeclaringClass(), method.getName(), method.getModifiers(), Modifier.isStatic(method.getModifiers()), method.getReturnType(), method.getParameterTypes());
    }


    private static HandleWrapper wrap(final MethodHandle handle, final Class<?> owner, String method, int modifiers, boolean staticOrCtr, Class<?> rType, Class<?>... pTypes) throws Throwable {
        final String name = getName(owner, method, staticOrCtr, rType, pTypes);


        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        int id = Handles.ID.incrementAndGet();

        String description = name.replace(".", "/");

        // Create Implementation of MethodWrapper.
        cw.visit(V1_8, ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC, description, null, Type.getInternalName(HandleWrapper.class), null);
        // Create private static final MethodHandle field.
        cw.visitField(ACC_PRIVATE | ACC_FINAL | ACC_STATIC, "HANDLE", "Ljava/lang/invoke/MethodHandle;", null, null).visitEnd();

        AccessClassLoader loader = AccessClassLoader.get(owner);

        // Static Initializer
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();
        mv.visitLdcInsn(id);
        mv.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Handles.class), "getHandle", "(I)Ljava/lang/invoke/MethodHandle;", false);
        mv.visitFieldInsn(PUTSTATIC, description, "HANDLE", "Ljava/lang/invoke/MethodHandle;");
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        // Default Ctr
        mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(HandleWrapper.class), "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC | ACC_VARARGS | ACC_FINAL, "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", null, new String[]{"java/lang/Throwable"});
        AsmUtil.setInline(mv);
        mv.visitCode();
        mv.visitFieldInsn(GETSTATIC, description, "HANDLE", "Ljava/lang/invoke/MethodHandle;");

        if (!staticOrCtr) {
            mv.visitVarInsn(ALOAD, 1);
            mv.visitTypeInsn(CHECKCAST, owner.getName().replace(".", "/"));
        }

        for (int i = 0; i < pTypes.length; i++) {
            mv.visitVarInsn(ALOAD, 2);
            AsmUtil.iconst(mv, i);
            mv.visitInsn(AALOAD);
            AsmUtil.unbox(mv, Type.getType(pTypes[i]));
        }

        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact", AsmUtil.buildHandleSignature(staticOrCtr, owner, rType, pTypes), false);
        AsmUtil.box(mv, Type.getType(rType));
        mv.visitInsn(ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        Handles.add(id, handle);

        if (IS_DEBUG) {
            File f = new File(".");
            if (!f.exists()) {
                f.mkdir();
            }
            if (f.isDirectory()) f = new File(f.getCanonicalPath() + File.separator + name + ".class");
            try (FileOutputStream writer = new FileOutputStream(f)) {
                byte[] bytes = cw.toByteArray();
                writer.write(bytes);
                writer.flush();
                System.out.println("Class saved to " + f.getCanonicalPath());
                CheckClassAdapter.verify(new ClassReader(bytes), loader, false, new PrintWriter(System.out));
            }
        }
        try {
            synchronized (Handles.CACHES) {
                HandleWrapper wrapper = Handles.CACHES.get(name);
                if (wrapper != null) {
                    return wrapper;
                }
                Class<?> wrapperClass = loader.defineClass(name, cw.toByteArray());
                wrapper = (HandleWrapper) wrapperClass.newInstance();
                Handles.CACHES.put(name, wrapper);
                return wrapper;
            }
        } finally {
            Handles.del(id);
        }
    }


    private static String getName(Class<?> owner, String method, boolean staticOrCtr, Class<?> rType, Class<?>[] pTypes) {
        StringBuilder builder = new StringBuilder(owner.getName());
        if (staticOrCtr) {
            builder.append("_static");
        }

        if (rType != null) {
            builder.append("_").append(rType.getSimpleName());
        }

        for (Class<?> pType : pTypes) {
            builder.append("_").append(pType.getSimpleName());
        }

        return "asm." + (owner.getName() + "_" + method).replaceAll("[<>$]", "") + (staticOrCtr ? "_static" : "") + "_" + Integer.toHexString(builder.toString().hashCode());
    }

}