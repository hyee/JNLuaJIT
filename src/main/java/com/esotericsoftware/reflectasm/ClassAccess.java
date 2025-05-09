package com.esotericsoftware.reflectasm;

import com.esotericsoftware.reflectasm.util.AsmUtil;
import com.esotericsoftware.reflectasm.util.NumberUtils;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.esotericsoftware.reflectasm.util.NumberUtils.convert;
import static com.esotericsoftware.reflectasm.util.NumberUtils.getDistance;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

/* For java8
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
*/
/* For java 7
import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.Type;
import static jdk.internal.org.objectweb.asm.Opcodes.*;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;
*/

@SuppressWarnings({"UnusedDeclaration", "Convert2Diamond", "ConstantConditions", "Unsafe", "deprecation"})
public class ClassAccess<ANY> implements Accessor<ANY> {
    public final static int HASH_BUCKETS = Integer.valueOf(System.getProperty("reflectasm.hash_buckets", "16"));
    public final static int MODIFIER_VARARGS = 262144;
    public final static String NEW = "new";
    public final static String FIELD = "field";
    public final static String SETTER = "set";
    public final static String GETTER = "get";
    public final static String METHOD = "method";
    public final static String ACCESS_CLASS_PREFIX = "asm.";
    public static boolean IS_SINGLE_THREAD_MODE = false;
    public static boolean IS_CACHED = true;
    public static boolean IS_STRICT_CONVERT = false;
    public static boolean IS_DEBUG = false;
    public static boolean IS_INCLUDE_NON_PUBLIC = true;
    public static int totalAccesses = 0;
    public static int cacheHits = 0;
    public static int loaderHits = 0;
    static HashMap[] caches = new HashMap[HASH_BUCKETS];
    static final String thisPath = Type.getInternalName(ClassAccess.class);
    static final String accessorPath = Type.getInternalName(Accessor.class);
    static final String classInfoPath = Type.getInternalName(ClassInfo.class);
    static ReentrantReadWriteLock[] locks = new ReentrantReadWriteLock[HASH_BUCKETS];
    public static final MethodHandles.Lookup lookup = MethodHandles.lookup();
    public HandleWrapper[][] methodHandles;
    public static Field methodWriterCodeField = null;
    public static Field byteVectorLengthField = null;
    volatile static boolean isInvokeHandle = true;
    private volatile static boolean isMagicImpl = false;

    static {
        try {
            if(Double.valueOf(System.getProperty("java.version"))==52.0) {
                // and then we mark it as trusted for private lookup via reflection on private field
                Field field = lookup.getClass().getDeclaredField("allowedModes");
                field.setAccessible(true);
                field.set(lookup, -1);
            }
        } catch (Exception e) {

        }

        if (System.getProperty("reflectasm.is_cache", "true").equalsIgnoreCase("false")) {
            IS_CACHED = false;
        } else for (int i = 0; i < HASH_BUCKETS; i++) {
            caches[i] = new HashMap(HASH_BUCKETS);
        }
        if (System.getProperty("reflectasm.is_debug", "false").equalsIgnoreCase("true")) {
            IS_DEBUG = true;
        }
        if (System.getProperty("reflectasm.is_strict_convert", "false").equalsIgnoreCase("true")) {
            IS_STRICT_CONVERT = true;
        }
        for (int i = 0; i < HASH_BUCKETS; i++) {
            locks[i] = new ReentrantReadWriteLock();
        }
    }

    public final Accessor<ANY> accessor;
    public final ClassInfo classInfo;

    static void reset() {
        isInvokeHandle = true;
        isMagicImpl = !isInvokeHandle;
    }

    protected ClassAccess(Accessor accessor) {
        this.classInfo = accessor.getInfo();
        this.accessor = accessor;
        MethodHandle[][] mh = accessor.getMethodHandles();
        this.methodHandles = new HandleWrapper[mh.length][mh[1].length];
    }

    public static boolean isVarArgs(int modifier) {
        return (modifier & MODIFIER_VARARGS) != 0;
    }

    public final static int getBucket(Class clz) {
        return Math.abs(clz.getName().hashCode()) % HASH_BUCKETS;
    }

    public final static void lock(int bucket, String mode, boolean isEnable) {
        if (IS_SINGLE_THREAD_MODE) return;
        if (mode.equals("read")) {
            if (isEnable) locks[bucket].readLock().lock();
            else locks[bucket].readLock().unlock();
        } else {
            if (isEnable) locks[bucket].writeLock().lock();
            else locks[bucket].writeLock().unlock();
        }
    }

    public final static Object readCache(Class clz, String key) {
        int bucket = getBucket(clz);
        String k = clz.getName() + "." + key;
        lock(bucket, "read", true);
        try {
            return caches[bucket].get(k);
        } finally {
            lock(bucket, "read", false);
        }
    }

    public final static void writeCache(Class clz, String key, Object value) {
        int bucket = getBucket(clz);
        String k = clz.getName() + "." + key;
        lock(bucket, "write", true);
        try {
            caches[bucket].put(k, value);
        } finally {
            lock(bucket, "write", false);
        }
    }

    public static int activeAccessClassLoaders() {
        return AccessClassLoader.activeAccessClassLoaders();
    }

    public final static ClassInfo buildIndex(int id) {
        ClassInfo info = Handles.getInfo(id);
        if (info == null || info.attrIndex != null) return info;
        info.methodCount = info.methodNames.length;
        info.fieldCount = info.fieldNames.length;
        info.constructorCount = info.constructorModifiers.length;
        Class clz = info.baseClass;
        info.attrIndex = new HashMap<>();
        String[] constructors = new String[info.constructorParamTypes.length];
        Arrays.fill(constructors, NEW);
        String[][] attrs = new String[][]{info.fieldNames, info.methodNames, constructors};
        HashMap<String, ArrayList<Integer>> map = new HashMap<>();
        for (int i = 0; i < attrs.length; i++) {
            for (int j = 0; j < attrs[i].length; j++) {
                String attr = (char) (i + 1) + attrs[i][j];
                if (!map.containsKey(attr)) map.put(attr, new ArrayList<Integer>());
                map.get(attr).add(j);
            }
        }
        for (String key : map.keySet())
            info.attrIndex.put(key, map.get(key).toArray(new Integer[]{}));
        return info;
    }

    /**
     * @param type     Target class for reflection
     * @param dumpFile Optional to specify the path/directory to dump the reflection class over the target class
     * @return A dynamic object that wraps the target class
     */
    public static <ANY> ClassAccess access(Class<ANY> type, String... dumpFile) {
        if (type.isArray())
            throw new IllegalArgumentException(String.format("Input class '%s' cannot be an array!", type.getCanonicalName()));
        String className = type.getName();
        final String accessClassName = (className.startsWith("java.") ? ACCESS_CLASS_PREFIX + className : className + "_asm").replace("$", "");
        final String source = String.valueOf(type.getResource(""));
        Class<ANY> accessClass = null;
        Accessor<ANY> accessor;
        byte[] bytes = null;
        ClassInfo<ANY> info = null;
        ClassAccess<ANY> self;
        int bucket = getBucket(type);
        AccessClassLoader loader = null;
        ++totalAccesses;
        int lockFlag = 0;
        reset();
        try {
            lock(bucket, "write", true);
            lockFlag |= 2;
            /*Cache: className={Class,classResourcePath,ClassAccess(),byte[]}*/
            if (IS_CACHED) {
                Object cachedObject = caches[bucket].get(className);
                if (cachedObject != null) {
                    Object[] cache = (Object[]) cachedObject;
                    //Class equals then directly return from cache
                    if (type == cache[0]) {
                        ++cacheHits;
                        self = (ClassAccess<ANY>) cache[2];
                        return self;
                    }
                    //Else if resources are equal then load from pre-built bytes
                    if (cache[3] != null) {
                        if (cache[1] == null && source == null || cache[1].equals(source)) {
                            bytes = (byte[]) cache[3];
                            ++loaderHits;
                        }
                    }
                }
            } else {
                loader = AccessClassLoader.get(type);
                try {
                    accessClass = (Class<ANY>) loader.loadClass(accessClassName);
                } catch (ClassNotFoundException ignore1) {
                    synchronized (loader) {
                        try {
                            accessClass = (Class<ANY>) loader.loadClass(accessClassName);
                        } catch (ClassNotFoundException ignore2) {
                        }
                    }
                }
                if (accessClass != null) {
                    ++loaderHits;
                    return new ClassAccess((Accessor) accessClass.newInstance());
                }
            }

            if (bytes == null) {//Otherwise rebuild the bytes
                ArrayList<Method> methods = new ArrayList<Method>();
                ArrayList<Constructor<?>> constructors = new ArrayList<Constructor<?>>();
                ArrayList<Field> fields = new ArrayList<Field>();
                collectMembers(type, methods, fields, constructors);
                info = new ClassInfo();
                info.bucket = bucket;
                int n = constructors.size();
                info.constructorModifiers = new Integer[n];
                info.constructorParamTypes = new Class[n][];
                info.constructorDescs = new String[n];
                info.constructorCount = n;
                info.constructors = new Constructor[n];
                for (int i = 0; i < n; i++) {
                    Constructor<?> c = constructors.get(i);
                    info.constructors[i] = c;
                    info.constructorModifiers[i] = c.getModifiers();
                    if (c.isVarArgs()) info.constructorModifiers[i] |= MODIFIER_VARARGS;
                    info.constructorParamTypes[i] = c.getParameterTypes();
                    info.constructorDescs[i] = Type.getConstructorDescriptor(c);
                }

                n = methods.size();
                info.methodDescs = new String[n][2];
                info.methodModifiers = new Integer[n];
                info.methodParamTypes = new Class[n][];
                info.returnTypes = new Class[n * 2];
                info.methodNames = new String[n];
                info.baseClass = type;
                info.methodCount = n;
                info.methods = new Method[n];
                for (int i = 0; i < n; i++) {
                    Method m = methods.get(i);
                    info.methods[i] = m;
                    info.methodModifiers[i] = m.getModifiers();
                    Class clz = m.getDeclaringClass();
                    if (m.isVarArgs()) info.methodModifiers[i] |= MODIFIER_VARARGS;
                    info.methodModifiers[i] |= clz.isInterface() ? Modifier.INTERFACE : 0;
                    info.methodParamTypes[i] = m.getParameterTypes();
                    info.returnTypes[i] = m.getReturnType();
                    info.returnTypes[n + i] = clz == type ? null : clz;
                    info.methodNames[i] = m.getName();
                    info.methodDescs[i] = new String[]{m.getName(), Type.getMethodDescriptor(m)};
                }

                n = fields.size();
                info.fieldModifiers = new Integer[n];
                info.fieldNames = new String[n];
                info.fieldTypes = new Class[n * 2];
                info.fieldDescs = new String[n][2];
                info.fieldCount = n;
                info.fields = new Field[n];
                for (int i = 0; i < n; i++) {
                    Field f = fields.get(i);
                    info.fields[i] = f;
                    Class clz = f.getDeclaringClass();
                    info.fieldNames[i] = f.getName();
                    info.fieldTypes[i] = f.getType();
                    info.fieldTypes[n + i] = clz == type ? null : clz;
                    info.fieldModifiers[i] = f.getModifiers();
                    info.fieldDescs[i] = new String[]{f.getName(), Type.getDescriptor(f.getType())};
                    info.fieldModifiers[i] |= clz.isInterface() ? Modifier.INTERFACE : 0;
                }
                info.id = Handles.addInfo(info);
                String accessClassNameInternal = accessClassName.replace('.', '/');
                String classNameInternal = className.replace('.', '/');
                //Remove "type.getEnclosingClass()==null" due to may trigger error
                int position = className.lastIndexOf('$');
                info.isNonStaticMemberClass = position > 0 && classNameInternal.substring(position).indexOf('/') == -1 && !Modifier.isStatic(type.getModifiers());
                bytes = byteCode(info, accessClassNameInternal, classNameInternal);
            }
            if (dumpFile.length > 0) try {
                File f = new File(dumpFile[0]);
                if (!f.exists()) {
                    if (!dumpFile[0].endsWith(".class")) f.createNewFile();
                    else f.mkdir();
                }
                if (f.isDirectory()) f = new File(f.getCanonicalPath() + File.separator + accessClassName + ".class");
                try (FileOutputStream writer = new FileOutputStream(f)) {
                    writer.write(bytes);
                    writer.flush();
                    System.out.println("Class saved to " + f.getCanonicalPath());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (loader == null) loader = AccessClassLoader.get(type);
            if (IS_DEBUG) CheckClassAdapter.verify(new ClassReader(bytes), loader, false, new PrintWriter(System.out));
            accessClass = (Class<ANY>) loader.defineClass(accessClassName, bytes, type);
            accessor = (Accessor) accessClass.newInstance();
            self = new ClassAccess(accessor);
            if (IS_CACHED) {
                caches[bucket].put(className, new Object[]{type, source, self, bytes});
            }
            return self;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("Error constructing method access class: " + accessClassName + ": " + ex.getMessage(), ex);
        } finally {
            if ((lockFlag & 2) > 0) lock(bucket, "write", false);
            if ((lockFlag & 1) > 0) lock(bucket, "read", false);
        }
    }

    private static void collectClasses(Class clz, ArrayList classes) {
        if (clz == Object.class && classes.size() > 0) return;
        classes.add(clz);
        Class superClass = clz.getSuperclass();
        if (superClass != null && superClass != clz) {
            collectClasses(superClass, classes);
        }
        for (Class c : clz.getInterfaces()) {
            collectClasses(c, classes);
        }
    }

    private static int calcPriority(int modifier) {
        return (Modifier.isPublic(modifier) ? 8 : 0) +
                (Modifier.isStatic(modifier) ? 4 : 0) +
                (Modifier.isProtected(modifier) ? 2 : 0) +
                (Modifier.isPrivate(modifier) ? 0 : 1);
    }

    private static void collectMembers(Class<?> type, List<Method> methods, List<Field> fields, List<Constructor<?>> constructors) {
        boolean search = true;

        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            //if (!IS_INCLUDE_NON_PUBLIC && !Modifier.isPublic(constructor.getModifiers())) continue;
            constructors.add(constructor);
        }

        ArrayList<Class> classes = new ArrayList<>();
        collectClasses(type, classes);
        LinkedHashMap<String, Object> map = new LinkedHashMap();
        LinkedHashMap<String, Object> candidates = new LinkedHashMap();
        HashMap<String, Integer> names = new HashMap<>();
        for (Class clz : classes) {
            boolean isPublic = Modifier.isPublic(clz.getModifiers());
            for (Method m : clz.getDeclaredMethods()) {
                int md1 = m.getModifiers();
                if (!IS_INCLUDE_NON_PUBLIC && !Modifier.isPublic(md1)) continue;
                String name = m.getName();
                //if (Modifier.isAbstract(md1) && !type.isInterface() && !Modifier.isAbstract(typeModifier)) continue;
                String desc = name + Type.getMethodDescriptor(m);
                int modifier = 16 + calcPriority(md1);
                Method m0 = (Method) map.get(desc);
                int md0 = m0 == null ? 0 : m0.getModifiers();
                if (m0 == null) map.put(desc, m);
                else if ((Modifier.isPublic(md1) && isPublic && !Modifier.isPublic(m0.getDeclaringClass().getModifiers()))
                        || (Modifier.isPublic(md1) && !Modifier.isPublic(md0))//
                        || (Modifier.isStatic(md1) && !Modifier.isStatic(md0))//
                        || (Modifier.isAbstract(md0) && !Modifier.isAbstract(md1))) {
                    map.put(desc, m);
                    candidates.put(desc, m0);
                } else candidates.put(desc, m);
                Integer org = names.get(name);
                if (org == null || org < modifier) names.put(name, modifier);
            }

            for (Field f : clz.getDeclaredFields()) {
                int md1 = f.getModifiers();
                String desc = f.getName();
                if (!IS_INCLUDE_NON_PUBLIC && !Modifier.isPublic(md1)) continue;
                Field f0 = (Field) map.get(desc);
                int modifier = calcPriority(md1);
                Integer org = names.get(desc);
                //deal with conflict between method and field
                if (org != null) {
                    boolean isOverload = modifier <= (org ^ 16);
                    if (org >= 16) {
                        if (isOverload) continue;
                        else names.put(desc, modifier);
                    } else if (!isOverload) {
                        names.put(desc, modifier);
                    }
                } else names.put(desc, modifier);
                int md0 = f0 == null ? 0 : f0.getModifiers();
                if (!map.containsKey(desc)) map.put(desc, f);
                else if ((Modifier.isPublic(md1) && isPublic && !Modifier.isPublic(f0.getDeclaringClass().getModifiers()))
                        || (Modifier.isPublic(md1) && !Modifier.isPublic(md0))//
                        || (Modifier.isStatic(md1) && !Modifier.isStatic(md0))//
                        || (Modifier.isAbstract(md0) && !Modifier.isAbstract(md1))) {
                    map.put(desc, f);
                    candidates.put(desc, f0);
                } else candidates.put(desc, f);
            }
        }

        for (String desc : map.keySet()) {
            int md = names.get(desc.indexOf('(') == -1 ? desc : desc.substring(0, desc.indexOf('(')));
            Object value = map.get(desc);
            if (value.getClass() == Method.class && md >= 16) methods.add((Method) value);
            else if (value.getClass() == Field.class) fields.add((Field) value);
        }

        for (String desc : candidates.keySet()) {
            int md = names.get(desc.indexOf('(') == -1 ? desc : desc.substring(0, desc.indexOf('(')));
            Object value = candidates.get(desc);
            if (value.getClass() == Method.class && md >= 16) methods.add((Method) value);
            else if (value.getClass() == Field.class) fields.add((Field) value);
        }
    }

    /**
     * Build ClassInfo of the underlying class while contructing
     *
     * @param info                    ClassInfo of the underlying class
     * @param cw                      ClassWriter
     * @param accessClassNameInternal The class name of the wrapper class
     * @param classNameInternal       The class name of the underlying class
     */
    private static void insertClassInfo(ClassVisitor cw, ClassInfo info, String accessClassNameInternal, String classNameInternal) {
        //final String baseName = "sun/reflect/MagicAccessorImpl";
        final String baseName = isMagicImpl ? "sun/reflect/MagicAccessorImpl" : "java/lang/Object";

        final String clzInfoDesc = Type.getDescriptor(ClassInfo.class);
        final String genericName = "<L" + classNameInternal + ";>;";
        final String clzInfoGenericDesc = "L" + Type.getInternalName(ClassInfo.class) + genericName;
        cw.visit(V1_8, ACC_PUBLIC + ACC_FINAL + ACC_SYNTHETIC, accessClassNameInternal, "L" + baseName + ";L" + accessorPath + genericName, baseName, new String[]{accessorPath});
        String className = classNameInternal;
        try {
            int position = className.lastIndexOf('$');
            if (position >= 0 && classNameInternal.substring(position).indexOf('/') == -1) {
                String outerClass = classNameInternal.substring(0, position);
                cw.visitOuterClass(outerClass, null, null);
                cw.visitInnerClass(classNameInternal, outerClass, info.baseClass.getSimpleName(), info.baseClass.getModifiers());
            }
        } catch (Throwable e) {
        }
        MethodVisitor mv;

        //Constructor
        {
            mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, baseName, "<init>", "()V");
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        //Static block
        {
            FieldVisitor fv = cw.visitField(ACC_STATIC, "methodHandles", "[[Ljava/lang/invoke/MethodHandle;", null, null);
            fv.visitEnd();
            mv = cw.visitMethod(ACC_PUBLIC, "getMethodHandles", "()[[Ljava/lang/invoke/MethodHandle;", null, null);
            mv.visitCode();
            mv.visitFieldInsn(GETSTATIC, accessClassNameInternal, "methodHandles", "[[Ljava/lang/invoke/MethodHandle;");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            fv = cw.visitField(ACC_FINAL + ACC_STATIC, "classInfo", clzInfoDesc, clzInfoDesc.substring(0, clzInfoDesc.length() - 1) + "<L" + classNameInternal + ";>;", null);
            fv.visitEnd();
            mv = cw.visitMethod(ACC_PUBLIC, "getInfo", "()" + clzInfoDesc, "()" + clzInfoGenericDesc, null);
            mv.visitCode();
            mv.visitFieldInsn(GETSTATIC, accessClassNameInternal, "classInfo", clzInfoDesc);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        {//Static block
            mv = cw.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
            mv.visitCode();

            mv.visitInsn(ICONST_3);
            AsmUtil.iconst(mv, Math.max(Math.max(info.constructorCount, info.methodCount), info.fieldCount * 2));
            mv.visitMultiANewArrayInsn("[[Ljava/lang/invoke/MethodHandle;", 2);
            mv.visitFieldInsn(PUTSTATIC, accessClassNameInternal, "methodHandles", "[[Ljava/lang/invoke/MethodHandle;");

            AsmUtil.iconst(mv, info.id);
            mv.visitMethodInsn(INVOKESTATIC, thisPath, "buildIndex", "(I)Lcom/esotericsoftware/reflectasm/ClassInfo;", false);
            mv.visitFieldInsn(PUTSTATIC, accessClassNameInternal, "classInfo", clzInfoDesc);

            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private static byte[] byteCode(ClassInfo info, String accessClassNameInternal, String classNameInternal) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        insertClassInfo(cw, info, accessClassNameInternal, classNameInternal);

        //***********************************************************************************************
        // method access
        insertInvoke(cw, classNameInternal, info, accessClassNameInternal);

        //***********************************************************************************************
        // field access
        insertGetField(cw, classNameInternal, info, accessClassNameInternal);
        insertSetField(cw, classNameInternal, info, accessClassNameInternal);

        //***********************************************************************************************
        // constructor access
        insertNewInstance(cw, classNameInternal, info, accessClassNameInternal);
        insertNewRawInstance(cw, classNameInternal, accessClassNameInternal);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void insertNewRawInstance(ClassVisitor cw, String classNameInternal, String accessClassNameInternal) {
        MethodVisitor mv;
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "newInstance", "()L" + classNameInternal + ";", null, null);
            AsmUtil.setInline(mv);
            mv.visitTypeInsn(Opcodes.NEW, classNameInternal);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(INVOKESPECIAL, classNameInternal, "<init>", "()V");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
        {
            mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC + ACC_FINAL, "newInstance", "()Ljava/lang/Object;", null, null);
            AsmUtil.setInline(mv);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, accessClassNameInternal, "newInstance", "()L" + classNameInternal + ";");
            mv.visitInsn(ARETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }
    }

    private static void insertNewInstance(ClassVisitor cw, String classNameInternal, ClassInfo info, String accessClassNameInternal) {
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS + ACC_FINAL, "newInstanceWithIndex", "(I[Ljava/lang/Object;)L" + classNameInternal + ";", "<T:Ljava/lang/Object;>(I[TT;)L" + classNameInternal + ";", null);
        AsmUtil.setInline(mv);

        int n = info.constructorCount;

        if (n != 0) {
            mv.visitVarInsn(ILOAD, 1);
            Label[] labels = new Label[n];
            for (int i = 0; i < n; i++)
                labels[i] = new Label();
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0; i < n; i++) {
                mv.visitLabel(labels[i]);
                if (i == 0) mv.visitFrame(Opcodes.F_APPEND, 1, new Object[]{classNameInternal}, 0, null);
                else mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                if (!isMagicImpl && !Modifier.isPublic(info.constructorModifiers[i])) {
                    insertThrowException(mv, "Constructor is private: " + info.constructorDescs[i]);
                } else {
                    mv.visitTypeInsn(Opcodes.NEW, classNameInternal);
                    mv.visitInsn(DUP);

                    Class[] paramTypes = info.constructorParamTypes[i];
                    for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
                        Type paramType = Type.getType(paramTypes[paramIndex]);
                        mv.visitVarInsn(ALOAD, 2);
                        AsmUtil.iconst(mv, paramIndex);
                        mv.visitInsn(AALOAD);
                        AsmUtil.unbox(mv, paramType);
                    }
                    mv.visitMethodInsn(INVOKESPECIAL, classNameInternal, "<init>", info.constructorDescs[i]);
                    mv.visitInsn(ARETURN);
                }
            }
            mv.visitLabel(defaultLabel);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        }
        insertThrowException(mv, "Constructor not found.");
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC + ACC_FINAL, "newInstanceWithIndex", "(I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        AsmUtil.setInline(mv);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ILOAD, 1);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, accessClassNameInternal, "newInstanceWithIndex", "(I[Ljava/lang/Object;)L" + classNameInternal + ";");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();
    }

    private static void insertInvoke(ClassVisitor cw, String classNameInternal, ClassInfo info, String accessClassNameInternal) {
        MethodVisitor mv;
        mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS + ACC_FINAL, "invokeWithIndex", "(L" + classNameInternal + ";I[Ljava/lang/Object;)Ljava/lang/Object;", "<T:Ljava/lang/Object;V:Ljava/lang/Object;>(L" + classNameInternal + ";I[TV;)TT;", null);
        AsmUtil.setInline(mv);

        int n = info.methodCount;

        if (n != 0) {
            mv.visitVarInsn(ILOAD, 2);
            Label[] labels = new Label[n];
            for (int i = 0; i < n; i++)
                labels[i] = new Label();
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0; i < n; i++) {
                boolean isInterface = Modifier.isInterface(info.methodModifiers[i]);
                boolean isStatic = Modifier.isStatic(info.methodModifiers[i]);

                mv.visitLabel(labels[i]);
                if (i == 0) mv.visitFrame(Opcodes.F_APPEND, 1, new Object[]{classNameInternal}, 0, null);
                else mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                if (!isMagicImpl && !Modifier.isPublic(info.methodModifiers[i])) {
                    insertThrowException(mv, "Method is not public: " + info.methodNames[i] + " => " + info.methodDescs[i][1]);
                } else {
                    if (!isStatic) {
                        mv.visitVarInsn(ALOAD, 1);
                    }
                    String methodName = info.methodNames[i];
                    Class[] paramTypes = info.methodParamTypes[i];
                    Class returnType = info.returnTypes[i];
                    for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
                        mv.visitVarInsn(ALOAD, 3);
                        AsmUtil.iconst(mv, paramIndex);
                        mv.visitInsn(AALOAD);
                        Type paramType = Type.getType(paramTypes[paramIndex]);
                        AsmUtil.unbox(mv, paramType);
                    }
                    //4096: SYNTHETIC
                    //final int inv = (isInterface && (info.methodModifiers[i] & ACC_SYNTHETIC) == 0) ? INVOKEINTERFACE : (isStatic ? INVOKESTATIC : INVOKESPECIAL);
                    final int inv = isStatic ? INVOKESTATIC : (isInterface ? INVOKEINTERFACE : INVOKEVIRTUAL);
                    Class clz = info.returnTypes[i + info.methodCount];
                    mv.visitMethodInsn(inv, clz != null ? Type.getInternalName(clz) : classNameInternal, methodName, info.methodDescs[i][1], isInterface);
                    final Type retType = Type.getType(returnType);
                    AsmUtil.box(mv, retType);
                    mv.visitInsn(ARETURN);
                }
            }

            mv.visitLabel(defaultLabel);
            mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        }

        insertThrowException(mv, "Method not found");
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC + ACC_FINAL, "invokeWithIndex", "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        AsmUtil.setInline(mv);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, classNameInternal);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKEVIRTUAL, accessClassNameInternal, "invokeWithIndex", "(L" + classNameInternal + ";I[Ljava/lang/Object;)Ljava/lang/Object;");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(4, 4);
        mv.visitEnd();
    }

    static private void insertSetField(ClassVisitor cw, String classNameInternal, ClassInfo info, String accessClassNameInternal) {
        int maxStack = 6;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "set", "(L" + classNameInternal + ";ILjava/lang/Object;)V", "<T:Ljava/lang/Object;V:Ljava/lang/Object;>(L" + classNameInternal + ";ITV;)V", null);
        AsmUtil.setInline(mv);
        mv.visitVarInsn(ILOAD, 2);

        if (info.fieldCount > 0) {
            maxStack--;
            Label[] labels = new Label[info.fieldCount];
            for (int i = 0, n = labels.length; i < n; i++)
                labels[i] = new Label();
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                Type fieldType = Type.getType(info.fieldTypes[i]);
                boolean st = Modifier.isStatic(info.fieldModifiers[i]);
                mv.visitLabel(labels[i]);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                if (!isMagicImpl && !Modifier.isPublic(info.fieldModifiers[i])) {
                    insertThrowException(mv, "Method is not public: " + info.fieldNames[i] + " => " + info.fieldDescs[i][1]);
                } else {
                    if (!st) mv.visitVarInsn(ALOAD, 1);
                    mv.visitVarInsn(ALOAD, 3);
                    AsmUtil.unbox(mv, fieldType);
                    Class clz = info.fieldTypes[i + info.fieldCount];
                    mv.visitFieldInsn(st ? PUTSTATIC : PUTFIELD, clz != null ? Type.getInternalName(clz) : classNameInternal, info.fieldNames[i], info.fieldDescs[i][1]);
                    mv.visitInsn(RETURN);
                }
            }
            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        mv = insertThrowException(mv, "Field not found.");
        mv.visitMaxs(maxStack, 4);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC + ACC_FINAL, "set", "(Ljava/lang/Object;ILjava/lang/Object;)V", null, null);
        AsmUtil.setInline(mv);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, classNameInternal);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKEVIRTUAL, accessClassNameInternal, "set", "(L" + classNameInternal + ";ILjava/lang/Object;)V");
        mv.visitInsn(RETURN);
        mv.visitMaxs(4, 4);
        mv.visitEnd();
    }

    static private void insertGetField(ClassVisitor cw, String classNameInternal, ClassInfo info, String accessClassNameInternal) {
        int maxStack = 6;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL, "get", "(L" + classNameInternal + ";I)Ljava/lang/Object;", "<T:Ljava/lang/Object;>(L" + classNameInternal + ";I)TT;", null);
        AsmUtil.setInline(mv);
        mv.visitVarInsn(ILOAD, 2);

        if (info.fieldCount > 0) {
            maxStack--;
            Label[] labels = new Label[info.fieldCount];
            for (int i = 0, n = labels.length; i < n; i++)
                labels[i] = new Label();
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                mv.visitLabel(labels[i]);
                mv.visitFrame(F_SAME, 0, null, 0, null);

                if (!isMagicImpl && !Modifier.isPublic(info.fieldModifiers[i])) {
                    insertThrowException(mv, "Method is not public: " + info.fieldNames[i] + " => " + info.fieldDescs[i][1]);
                } else {
                    Class clz = info.fieldTypes[i + info.fieldCount];
                    String clzName = clz != null ? Type.getInternalName(clz) : classNameInternal;
                    if (Modifier.isStatic(info.fieldModifiers[i])) {
                        mv.visitFieldInsn(GETSTATIC, clzName, info.fieldNames[i], info.fieldDescs[i][1]);
                    } else {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitFieldInsn(GETFIELD, clzName, info.fieldNames[i], info.fieldDescs[i][1]);
                    }
                    Type fieldType = Type.getType(info.fieldTypes[i]);
                    AsmUtil.box(mv, fieldType);
                    mv.visitInsn(ARETURN);
                }
            }
            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        insertThrowException(mv, "Field not found.");
        mv.visitInsn(ATHROW);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        mv = cw.visitMethod(ACC_PUBLIC + ACC_BRIDGE + ACC_SYNTHETIC + ACC_FINAL, "get", "(Ljava/lang/Object;I)Ljava/lang/Object;", null, null);
        AsmUtil.setInline(mv);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitTypeInsn(CHECKCAST, classNameInternal);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, accessClassNameInternal, "get", "(L" + classNameInternal + ";I)Ljava/lang/Object;");
        mv.visitInsn(ARETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();
    }

    static private MethodVisitor insertThrowException(MethodVisitor mv, String error) {
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException");
        mv.visitInsn(DUP);
        mv.visitLdcInsn(error);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V");
        mv.visitInsn(ATHROW);
        return mv;
    }

    @Override
    public String toString() {
        return accessor.toString();
    }

    public boolean isNonStaticMemberClass() {
        return classInfo.isNonStaticMemberClass;
    }

    public String getNameType(String name) {
        String item = null;
        if (classInfo.attrIndex.containsKey(name)) {
            char c = name.charAt(0);
            return c == 1 ? FIELD : c == 2 ? METHOD : NEW;
        }
        for (int i = 1; i <= 3; i++) {
            if (classInfo.attrIndex.containsKey((char) i + name))
                item = i == 1 ? FIELD : i == 2 ? METHOD : NEW;
        }
        return item;
    }

    public Integer[] indexesOf(Class clz, String name, String type) {
        char index;
        if (name.equals(NEW)) index = 3;
        else switch (type) {
            case FIELD:
                index = 1;
                break;
            case METHOD:
                index = 2;
                break;
            case NEW:
            case "constructor":
                index = 3;
                break;
            default:
                throw new IllegalArgumentException("No such type " + type);
        }
        final String attr = index + name;
        Integer[] list = (Integer[]) classInfo.attrIndex.get(attr);
        if (list != null) {
            if (clz == null || index == 3) return list;
            ArrayList<Integer> ary = new ArrayList();
            Class[] classes = index == 1 ? classInfo.fieldTypes : classInfo.returnTypes;
            final int offset = index == 1 ? classInfo.fieldCount : classInfo.methodCount;
            String className = Type.getInternalName(clz);
            for (Integer e : list)
                if (clz == classes[offset + e] || (clz == classInfo.baseClass && classes[offset + e] == null))
                    ary.add(e);
            list = ary.toArray(new Integer[]{});
            if (list.length > 0) return list;
        }
        throw new IllegalArgumentException("Unable to find " + type + ": " + name);
    }

    public static <T> Class<T>[] args2Types(final T... args) {
        Class<T>[] classes = new Class[args.length];
        for (int i = 0, n = args.length; i < n; i++)
            classes[i] = args[i] == null ? null : (Class<T>) args[i].getClass();
        return classes;
    }

    /**
     * Get MethodHandle of Field/Method/Constructor
     *
     * @param index The index that can be retrieved from indexOfField/indexOfMethod/indexOfConstructor
     * @param type  Can be: ClassAccess.GETTER/ClassAccess.SETTER/ClassAccess.METHOD/ClassAccess.NEW
     * @return The direct MethodHandle
     */
    public final HandleWrapper getHandleWithIndex(int index, String type) {
        HandleWrapper handle = null;
        int d1 = (type.equals(SETTER) || type.equals(GETTER)) ? 2 : type.equals(METHOD) ? 1 : 0;
        int d2 = type.equals(SETTER) ? classInfo.fieldCount + index : index;
        handle = methodHandles[d1][d2];
        Class clz;
        if (handle == null) try {
            switch (type) {
                case NEW:
                    final Constructor<?> c = classInfo.constructors[index];
                    c.setAccessible(true);
                    if (!Modifier.isStatic(classInfo.constructorModifiers[index]) && !Modifier.isPublic(c.getDeclaringClass().getModifiers())) {
                        handle = new HandleWrapper() {
                            @Override
                            public Object invoke(Object instance, Object... args) throws Throwable {
                                return c.newInstance(args);
                            }
                        };
                    } else {
                        handle = WrapperFactory.wrapConstructor(lookup.unreflectConstructor(c), c);
                    }
                    break;
                case METHOD:
                    final Method m = classInfo.methods[index];
                    m.setAccessible(true);
                    clz = m.getDeclaringClass();
                    if (!Modifier.isStatic(classInfo.methodModifiers[index]) && !Modifier.isPublic(clz.getModifiers())) {
                        handle = new HandleWrapper() {
                            @Override
                            public Object invoke(Object instance, Object... args) throws Throwable {
                                return m.invoke(instance, args);
                            }
                        };
                    } else {
                        handle = WrapperFactory.wrap(lookup.unreflect(m), m);
                    }
                    break;
                default:
                    final Field f = classInfo.fields[index];
                    f.setAccessible(true);
                    final boolean isPrivate = !Modifier.isStatic(classInfo.fieldModifiers[index]) && !Modifier.isPublic(f.getDeclaringClass().getModifiers());
                    if (type.equals(GETTER)) {
                        if (isPrivate) {
                            handle = new HandleWrapper() {
                                @Override
                                public Object invoke(Object instance, Object... args) throws Throwable {
                                    return f.get(instance);
                                }
                            };
                        } else {
                            handle = WrapperFactory.wrapGetter(lookup.unreflectGetter(f), f);
                        }
                    } else {
                        if (isPrivate) {
                            handle = new HandleWrapper() {
                                @Override
                                public Object invoke(Object instance, Object... args) throws Throwable {
                                    f.set(instance, args);
                                    return null;
                                }
                            };
                        } else {
                            handle = WrapperFactory.wrapSetter(lookup.unreflectSetter(f), f);
                        }
                    }
                    break;
            }
            //handle=new ConstantCallSite(handle).dynamicInvoker();
            methodHandles[d1][d2] = handle;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            throw new IllegalArgumentException(throwable.getMessage());
        }

        return handle;
    }

    /**
     * Get MethodHandle of Field/Method/Constructor
     *
     * @param clz        Target class, null means auto, can be super-class or interface
     * @param name       Field name or method name, for constructor, specify as null
     * @param type       Can be: ClassAccess.GETTER/ClassAccess.SETTER/ClassAccess.METHOD/ClassAccess.NEW
     * @param paramTypes The parameter types of target method or constructor
     * @return The direct MethodHandle
     */
    public <ANY> HandleWrapper getHandle(Class clz, String name, String type, Class... paramTypes) {
        int index;
        if (type.equals(SETTER) || type.equals(GETTER)) {
            index = indexesOf(clz, name, FIELD)[0];
        } else {
            index = indexOfMethod(clz, NEW.equals(type) ? NEW : name, paramTypes);
        }
        return getHandleWithIndex(index, type);
    }

    /**
     * Get MethodHandle of Field/Method/Constructor
     *
     * @param clz  Target class, null means auto, can be super-class or interface
     * @param name Field name or method name, for constructor, specify as null
     * @param type Can be: ClassAccess.GETTER/ClassAccess.SETTER/ClassAccess.METHOD/ClassAccess.NEW
     * @param args Parameters for further invoke
     * @return The direct MethodHandle
     */
    public <ANY, T> HandleWrapper getHandleWithArgs(Class clz, String name, String type, T... args) {
        return getHandle(clz, name, type, args2Types(args));
    }

    public Integer[] indexesOf(String name, String type) {
        return indexesOf(null, name, type);
    }

    public Integer indexOf(String name, String type) {
        Integer[] indexes = indexesOf(name, type);
        return indexes.length == 1 ? indexes[0] : null;
    }

    public int indexOfField(Class clz, String fieldName) {
        return indexesOf(clz, fieldName, FIELD)[0];
    }

    public int indexOfField(String fieldName) {
        return indexesOf(null, fieldName, FIELD)[0];
    }

    public int indexOfField(Field field) {
        return indexOfField(field.getDeclaringClass(), field.getName());
    }

    public int indexOfConstructor(Constructor<?> constructor) {
        return indexOfMethod(null, NEW, constructor.getParameterTypes());
    }

    public int indexOfConstructor(Class... parameterTypes) {
        return indexOfMethod(null, NEW, parameterTypes);
    }

    public int indexOfMethod(Method method) {
        return indexOfMethod(method.getDeclaringClass(), method.getName(), method.getParameterTypes());
    }

    /**
     * Returns the index of the first method with the specified name.
     */
    public int indexOfMethod(String methodName) {
        return indexesOf(null, methodName, METHOD)[0];
    }

    public Long getSignature(Class clz, String methodName, Class... paramTypes) {
        long signature = classInfo.baseClass.hashCode() * 65599 + methodName.hashCode();
        if (clz != null) signature += clz.getClass().getName().hashCode() * 65599 * 65599;
        if (paramTypes != null) for (int i = 0; i < paramTypes.length; i++) {
            signature = signature * 65599 + (paramTypes[i] == null ? 0 : paramTypes[i].hashCode());
        }
        return signature;
    }

    @SafeVarargs
    private final String typesToString(String methodName, Object... argTypes) {
        StringBuilder sb = new StringBuilder(classInfo.baseClass.getName());
        sb.append(".").append(methodName).append("(");
        for (int i = 0; i < argTypes.length; i++) {
            Class clz;
            if (argTypes[i] == null) clz = null;
            else if (argTypes[i] instanceof Class) clz = (Class) argTypes[i];
            else clz = argTypes[i].getClass();
            if (clz == null) sb.append("null");
            else {
                String clzName = clz.getCanonicalName();
                if (clzName == null) clzName = clz.getName();
                sb.append(clz == null ? "null" : clzName.startsWith("java.lang.") ? clz.getSimpleName() : clzName);
            }
            sb.append(i == argTypes.length - 1 ? "" : ",");
        }
        sb.append(")");
        return sb.toString();
    }


    @SafeVarargs
    public final int indexOfMethod(final Class clz, final String methodName, final Integer[] candidates, final Class... argTypes) {
        //if(IS_STRICT_CONVERT) return candidates[0];
        int result = -1;
        Class[][] paramTypes;
        Integer[] modifiers;
        Long signature = -1L;
        int distance = 0;
        int minDistance = 10;
        final int stepSize = 100;
        if (methodName.equals(NEW)) {
            for (int i = 0, n = candidates.length; i < n; i++) candidates[i] = i;
            paramTypes = classInfo.constructorParamTypes;
            modifiers = classInfo.constructorModifiers;
        } else {
            paramTypes = classInfo.methodParamTypes;
            modifiers = classInfo.methodModifiers;
        }
        final int bucket = classInfo.bucket;
        int lockFlag = 0;
        try {
            if (IS_CACHED) {
                signature = getSignature(clz, methodName, argTypes);
                lock(bucket, "read", true);
                lockFlag |= 1;
                Integer targetIndex = (Integer) caches[bucket].get(signature);
                lock(bucket, "read", false);
                lockFlag ^= 1;
                if (targetIndex != null) {
                    minDistance = targetIndex / 10000;
                    targetIndex = targetIndex % 10000;
                    if (10000 - targetIndex == 1) result = -2;
                    else for (int index : candidates)
                        if (index == targetIndex) result = index;
                }
            }
            final int argCount = argTypes.length;
            int[] distances = new int[0];
            if (result == -1) for (int index : candidates) {
                int min = 10;
                int[] val = new int[argCount + 1];
                if (Arrays.equals(argTypes, paramTypes[index])) {
                    if (IS_CACHED) {
                        lock(bucket, "write", true);
                        lockFlag |= 2;
                        caches[bucket].put(signature, Integer.valueOf(index + 50000));
                    }
                    return index;
                }
                int thisDistance = 0;
                final int paramCount = paramTypes[index].length;
                final int last = paramCount - 1;
                final Class lastClass = last < 0 ? null : paramTypes[index][last];
                final boolean isVarArgs = isVarArgs(modifiers[index]) || (lastClass != null && lastClass.isArray() && argCount > last);
                for (int i = 0, n = Math.min(argCount, paramCount); i < n; i++) {
                    if (i == last && isVarArgs) break;
                    val[i] = IS_STRICT_CONVERT ? 10 : NumberUtils.getDistance(argTypes[i], paramTypes[index][i]);
                    min = Math.min(val[i], min);
                    thisDistance += stepSize + val[i];
                    //System.out.println((argTypes[i]==null?"null":argTypes[i].getCanonicalName())+" <-> "+paramTypes[index][i].getCanonicalName()+": "+dis);
                }
                if (argCount > last && isVarArgs) {
                    if (!IS_STRICT_CONVERT) {
                        final Class arrayType = paramTypes[index][last].getComponentType();
                        int sum = 0;
                        for (int i = last; i < argCount; i++) {
                            thisDistance += stepSize;
                            val[i] = Math.max(getDistance(argTypes[i], arrayType), getDistance(argTypes[i], paramTypes[index][last]));
                            min = Math.min(min, val[i]);
                            if (val[i] <= 0) sum = -stepSize;
                            else sum += val[i];
                        }
                        thisDistance += sum;
                    }
                } else if (paramCount != argCount) {
                    thisDistance -= (Math.abs(paramCount - argCount) - (isVarArgs(modifiers[index]) ? 1 : 0)) * stepSize / (argCount > paramCount ? 2 : 1);
                }
                if (thisDistance > distance) {
                    distance = thisDistance;
                    distances = val;
                    result = index;
                    minDistance = min;
                }
            }
            if (result < -1) result = -1;
            if (IS_CACHED) {
                lock(bucket, "write", true);
                lockFlag |= 2;
                caches[bucket].put(signature, Integer.valueOf(minDistance * 10000 + result));
            }
            if (result >= 0 && argCount == 0 && paramTypes[result].length == 0) return result;
            if (result < 0 || minDistance == 0 //
                    || (argCount < paramTypes[result].length && !isVarArgs(modifiers[result])) //
                    || (isVarArgs(modifiers[result]) && argCount < paramTypes[result].length - 1)) {
                String str = "Unable to apply " + (methodName.equals(NEW) ? "constructor" : METHOD) + ":\n    " + typesToString(methodName, argTypes) //
                        + (result == -1 ? "" : "\n => " + typesToString(methodName, paramTypes[result]));
                if (IS_DEBUG && result >= 0) {
                    System.out.println(String.format("Method=%s, Index=%d, isVarArgs=%s, MinDistance=%d%s", methodName, result, isVarArgs(modifiers[result]) + "(" + modifiers[result] + ")", minDistance, Arrays.toString(distances)));
                    for (int i = 0; i < Math.max(argCount, paramTypes[result].length); i++) {
                        int flag = i >= argCount ? 1 : i >= paramTypes[result].length ? 2 : 0;
                        System.out.println(String.format("Parameter#%2d: %20s -> %-20s : %2d",//
                                i, flag == 1 ? "N/A" : argTypes[i] == null ? "null" : argTypes[i].getSimpleName(),//
                                flag == 2 ? "N/A" : paramTypes[result][i] == null ? "null" : paramTypes[result][i].getSimpleName(),//
                                flag > 0 ? -1 : distances[i]));
                    }
                }
                throw new IllegalArgumentException(str);
            }
            return result;
        } finally {
            if ((lockFlag & 2) > 0) lock(bucket, "write", false);
            if ((lockFlag & 1) > 0) lock(bucket, "read", false);
        }
    }

    /**
     * Returns the index of the first method with the specified name and param types.
     *
     * @param methodName Method name or '<new>' for constructing
     * @param argTypes   Arguments class types
     * @return
     */
    public final int indexOfMethod(final Class clz, final String methodName, Class... argTypes) {
        Integer[] candidates = indexesOf(clz, methodName, METHOD);
        return indexOfMethod(clz, methodName, indexesOf(clz, methodName, METHOD), argTypes);
    }

    public final int indexOfMethod(String methodName, Class... argTypes) {
        return indexOfMethod(null, methodName, argTypes);
    }

    /**
     * Returns the index of the first method with the specified name and the specified number of arguments.
     */
    public int indexOfMethod(String methodName, int paramsCount) {
        for (int index : indexesOf(null, methodName, METHOD)) {
            final int modifier = (methodName == NEW) ? classInfo.constructorModifiers[index] : classInfo.methodModifiers[index];
            final int len = (methodName == NEW) ? classInfo.constructorParamTypes[index].length : classInfo.methodParamTypes[index].length;
            if (len == paramsCount || isVarArgs(modifier) && paramsCount >= len - 1) return index;
        }
        throw new IllegalArgumentException("Unable to find method: " + methodName + " with " + paramsCount + " params.");
    }

    private String getMethodNameByParamTypes(Class[] paramTypes) {
        String methodName = NEW;
        for (int i = 0; i < classInfo.methodParamTypes.length; i++) {
            if (classInfo.methodParamTypes[i] == paramTypes) {
                methodName = classInfo.methodNames[i];
            }
        }
        return methodName;
    }

    private <T, V> T[] reArgs(final String method, final int index, V[] args) {
        final boolean isNewInstance = (method instanceof String) && (method.equals(NEW));
        if (index >= (isNewInstance ? classInfo.constructorCount : classInfo.methodCount))
            throw new IllegalArgumentException("No index: " + index);
        final Class<T>[] paramTypes = isNewInstance ? classInfo.constructorParamTypes[index] : classInfo.methodParamTypes[index];
        final int paramCount = paramTypes.length;
        if (paramCount == 0) return null;
        final int modifier = isNewInstance ? classInfo.constructorModifiers[index] : classInfo.methodModifiers[index];
        final int argCount = args.length;
        final int last = paramCount - 1;
        final Class lastClass = last < 0 ? null : paramTypes[last];
        final boolean isVarArgs = isVarArgs(modifier) || (argCount >= paramCount && (lastClass != null && lastClass.isArray()));

        if (argCount < (isVarArgs ? last : paramCount)) {
            String methodName = getMethodNameByParamTypes(paramTypes);
            throw new IllegalArgumentException("Unable to " + (isNewInstance ? "construct instance" : "invoke method") + " with index " + index + ": "//
                    + "\n    " + typesToString(methodName, args) //
                    + "\n    =>" + typesToString(methodName, paramTypes));
        }
        if (!isVarArgs && IS_STRICT_CONVERT) return (T[]) args;
        try {
            Object[] arg = new Object[paramCount];
            for (int i = 0; i < (isVarArgs ? last : paramCount); i++) {
                if (args[i] == null && paramTypes[i].isPrimitive())
                    throw new IllegalArgumentException("Cannot assign null to element#" + (i + 1) + ": " + paramTypes[i].getCanonicalName());
                arg[i] = IS_STRICT_CONVERT ? args[i] : convert(args[i], paramTypes[i]);
            }
            if (isVarArgs) {
                final Class varArgsType = paramTypes[last];
                final Class subType = varArgsType.getComponentType();
                Object var = null;
                if (argCount > paramCount) {
                    var = Arrays.copyOfRange(args, last, argCount);
                } else if (argCount == paramCount) {
                    var = args[last];
                    if (var == null) {
                        if (subType.isPrimitive())
                            throw new IllegalArgumentException("Cannot assign null to element#" + paramCount + ": " + varArgsType.getCanonicalName());
                        var = Array.newInstance(subType, 1);
                    } else if (getDistance(var.getClass(), varArgsType) <= getDistance(var.getClass(), subType))
                        var = Arrays.copyOfRange(args, last, argCount);
                } else {
                    var = Array.newInstance(subType, 0);
                }
                arg[last] = IS_STRICT_CONVERT ? var : convert(var, varArgsType);
            }
            return (T[]) arg;
        } catch (Exception e) {
            if (isNonStaticMemberClass() && (args[0] == null || args[0].getClass() != classInfo.baseClass.getEnclosingClass()))
                throw new IllegalArgumentException("Cannot initialize a non-static inner class " + classInfo.baseClass.getCanonicalName() + " without specifying the enclosing instance!");
            String methodName = getMethodNameByParamTypes(paramTypes);
            if (IS_DEBUG) e.printStackTrace();
            throw new IllegalArgumentException("Data conversion error when invoking method: " + e.getMessage()//
                    + "\n    " + typesToString(methodName, args) //
                    + "\n    =>" + typesToString(methodName, paramTypes));
        }
    }

    public void printFieldStack(Class clz, int level, String prefix) {
        for (Field f : clz.getDeclaredFields()) {
            System.out.println(String.format("%" + (level * 4) + "s%s => %s", "", prefix + f.getName(), f.getType()));
            if (f.getType() instanceof Class) printFieldStack(f.getType(), level + 1, prefix + f.getName() + ".");
        }
    }

    final public <T, V> T invokeHandle(ANY instance, final int index, String type, V... args) {
        try {
            return (T) getHandleWithIndex(index, type).invoke(instance, args);
        } catch (Throwable e2) {
            throw new IllegalArgumentException(e2);
        }
    }

    final public <T, V> T invokeWithIndex(ANY instance, final int methodIndex, V... args) {
        Object[] arg = args;
        if (classInfo.methodCount <= methodIndex)
            throw new IllegalArgumentException("No such method index: " + methodIndex);
        if (!IS_STRICT_CONVERT) arg = reArgs(METHOD, methodIndex, args);
        if (instance != null) {
            instance = Modifier.isStatic(classInfo.methodModifiers[methodIndex]) ? null : instance;
        }
        return isInvokeHandle ? invokeHandle(instance, methodIndex, METHOD, arg) : accessor.invokeWithIndex(instance, methodIndex, arg);
    }

    final public <T, V> T invoke(ANY instance, String methodName, V... args) {
        final int index = indexOfMethod(null, methodName, args2Types(args));
        return invokeWithIndex(instance, index, args);
    }

    final public <T, V> T invokeWithTypes(ANY instance, String methodName, Class[] paramTypes, V... args) {
        final int index = indexOfMethod(null, methodName, paramTypes);
        return invokeWithIndex(instance, index, args);
    }

    @Override
    public ClassInfo getInfo() {
        return classInfo;
    }

    @Override
    public MethodHandle[][] getMethodHandles() {
        return new MethodHandle[0][];
    }

    final public <V> ANY newInstanceWithIndex(int constructorIndex, V... args) {
        if (!IS_STRICT_CONVERT) {
            args = reArgs(NEW, constructorIndex, args);
        }
        return isInvokeHandle ? invokeHandle(null, constructorIndex, NEW, args) : accessor.newInstanceWithIndex(constructorIndex, args);
    }

    final public <T> ANY newInstanceWithTypes(Class[] paramTypes, T... args) {
        return newInstanceWithIndex(indexOfMethod(null, NEW, paramTypes), args);
    }

    final public ANY newInstance(Object... args) {
        Integer index = indexOf(NEW, METHOD);
        if (index == null) {
            Class[] paramTypes = new Class[args.length];
            for (int i = 0; i < args.length; i++) paramTypes[i] = args[i] == null ? null : args[i].getClass();
            index = indexOfMethod(null, NEW, paramTypes);
        }
        return newInstanceWithIndex(index, args);
    }

    final public ANY newInstance() {
        return newInstance(new Object[0]);
    }

    final public <T, V> void set(ANY instance, int fieldIndex, V value) {
        instance = Modifier.isStatic(classInfo.fieldModifiers[fieldIndex]) ? null : instance;
        if (!IS_STRICT_CONVERT) try {
            Class<T> clz = classInfo.fieldTypes[fieldIndex];
            if (isInvokeHandle) {
                invokeHandle(instance, fieldIndex, SETTER, convert(value, clz));
            } else {
                accessor.set(instance, fieldIndex, convert(value, clz));
            }
            return;
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Unable to set field '%s.%s' as '%s':\n  %s ",  //
                    classInfo.baseClass.getName(), classInfo.fieldNames[fieldIndex], value == null ? "null" : value.getClass().getCanonicalName(), e.getMessage()));
        }
        if (isInvokeHandle) {
            invokeHandle(instance, fieldIndex, SETTER, value);
        } else {
            accessor.set(instance, fieldIndex, value);
        }
    }

    public <T> void set(ANY instance, String fieldName, T value) {
        set(instance, indexOfField(fieldName), value);
    }

    public void setBoolean(ANY instance, int fieldIndex, boolean value) {
        set(instance, fieldIndex, value);
    }

    public void setByte(ANY instance, int fieldIndex, byte value) {
        set(instance, fieldIndex, value);
    }

    public void setShort(ANY instance, int fieldIndex, short value) {
        set(instance, fieldIndex, value);
    }

    public void setInt(ANY instance, int fieldIndex, int value) {
        set(instance, fieldIndex, value);
    }

    public void setLong(ANY instance, int fieldIndex, long value) {
        set(instance, fieldIndex, value);
    }

    public void setDouble(ANY instance, int fieldIndex, double value) {
        set(instance, fieldIndex, value);
    }

    public void setFloat(ANY instance, int fieldIndex, float value) {
        set(instance, fieldIndex, value);
    }

    public void setChar(ANY instance, int fieldIndex, char value) {
        set(instance, fieldIndex, value);
    }

    public <T> T get(ANY instance, int fieldIndex) {
        if (classInfo.fieldCount <= fieldIndex)
            throw new IllegalArgumentException("No such field index: " + fieldIndex);
        return isInvokeHandle ? invokeHandle(instance, fieldIndex, GETTER) : accessor.get(Modifier.isStatic(classInfo.fieldModifiers[fieldIndex]) ? null : instance, fieldIndex);
    }

    public <T> T get(ANY instance, String fieldName) {
        return get(instance, indexOfField(fieldName));
    }

    public <T> T get(ANY instance, int fieldIndex, Class<T> clz) {
        try {
            return convert(get(instance, fieldIndex), clz);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Unable to set field '%s': %s", classInfo.fieldNames[fieldIndex], e.getMessage()));
        }
    }

    public <T> T get(ANY instance, String fieldName, Class<T> clz) {
        return get(instance, indexOfField(fieldName), clz);
    }

    public char getChar(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, char.class);
    }

    public boolean getBoolean(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, boolean.class);
    }

    public byte getByte(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, byte.class);
    }

    public short getShort(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, short.class);
    }

    public int getInt(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, int.class);
    }

    public long getLong(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, long.class);
    }

    public double getDouble(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, double.class);
    }

    public float getFloat(ANY instance, int fieldIndex) {
        return get(instance, fieldIndex, float.class);
    }


}