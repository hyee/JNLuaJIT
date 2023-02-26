/**
 * Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * <p>
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.esotericsoftware.reflectasm;

import java.lang.invoke.MethodHandles;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.WeakHashMap;

public class AccessClassLoader extends ClassLoader {
    // Weak-references to class loaders, to avoid perm gen memory leaks, for example in app servers/web containters if the
    // reflectasm library (including this class) is loaded outside the deployed applications (WAR/EAR) using ReflectASM/Kryo (exts,
    // user classpath, etc).
    // The key is the parent class loader and the value is the AccessClassLoader, both are weak-referenced in the hash table.
    static private final WeakHashMap<ClassLoader, WeakReference<AccessClassLoader>> accessClassLoaders = new WeakHashMap();

    // Fast-path for classes loaded in the same ClassLoader as this class.
    static private final ClassLoader selfContextParentClassLoader = getParentClassLoader(AccessClassLoader.class);
    static private volatile AccessClassLoader selfContextAccessClassLoader = new AccessClassLoader(selfContextParentClassLoader);

    static private volatile Method defineClassMethod;

    private final HashSet<String> localClassNames = new HashSet();
    public static final Method lookupDefineClass;
    public static final Method privateLookupIn;
    private static int loaderInvokeMode = 0;

    private AccessClassLoader(ClassLoader parent) {
        super(parent);
    }

    static {
        Method privateLookupIn1 = null;
        Method lookupDefineClass1 = null;
        MethodHandles.Lookup lookup1 = MethodHandles.lookup();
        try {
            if (Double.valueOf(System.getProperty("java.class.version")) > 52) {
                privateLookupIn1 = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
                privateLookupIn1.setAccessible(true);
                lookupDefineClass1 = MethodHandles.lookup().getClass().getDeclaredMethod("defineClass", byte[].class);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        privateLookupIn = privateLookupIn1;
        lookupDefineClass = lookupDefineClass1;
    }

    /**
     * Returns null if the access class has not yet been defined.
     */
    Class loadAccessClass(String name) {
        // No need to check the parent class loader if the access class hasn't been defined yet.
        if (localClassNames.contains(name)) {
            try {
                return loadClass(name, false);
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex); // Should not happen, since we know the class has been defined.
            }
        }
        return null;
    }

    Class defineAccessClass(String name, byte[] bytes) throws ClassFormatError {
        localClassNames.add(name);
        return defineClass(name, bytes);
    }

    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // These classes come from the classloader that loaded AccessClassLoader.
        if (name.equals(Accessor.class.getName())) return Accessor.class;
        if (name.equals(ClassAccess.class.getName())) return ClassAccess.class;
        if (name.equals(FieldAccess.class.getName())) return FieldAccess.class;
        if (name.equals(MethodAccess.class.getName())) return MethodAccess.class;
        if (name.equals(ConstructorAccess.class.getName())) return ConstructorAccess.class;
        if (name.equals(HandleWrapper.class.getName())) return HandleWrapper.class;
        if (name.equals(Handles.class.getName())) return Handles.class;
        // All other classes come from the classloader that loaded the type we are accessing.
        return super.loadClass(name, resolve);
    }

    private String defineClassSourceLocation(ProtectionDomain pd) {
        CodeSource cs = pd.getCodeSource();
        String source = null;
        if (cs != null && cs.getLocation() != null) {
            source = cs.getLocation().toString();
        }
        return source;
    }

    public Class<?> defineClass(String name, byte[] bytes, Class baseClass) throws ClassFormatError {
        final ProtectionDomain pd = getClass().getProtectionDomain();
        final Method m = getDefineClassMethod();
        final String source = defineClassSourceLocation(pd);
        Throwable t = null;
        for (ClassLoader loader : new ClassLoader[]{getParent(), this}) {
            try {
                if (loaderInvokeMode == 1) {
                    return (Class<?>) m.invoke(loader, new Object[]{name, bytes, 0, bytes.length, pd, source});
                } else {
                    return (Class<?>) m.invoke(null, new Object[]{loader, name, bytes, 0, bytes.length, pd, source});
                }
            } catch (Throwable e) {
                //if(!this.equals(loader)) e.printStackTrace();
                t = e;
            }
        }
        throw new ClassFormatError(t.getMessage());
    }


    public Class<?> defineClass(String name, byte[] bytes) throws ClassFormatError {
        return defineClass(name, bytes, null);
    }

    // As per JLS, section 5.3,
    // "The runtime package of a class or interface is determined by the package name and defining class loader of the class or
    // interface."
    static boolean areInSameRuntimeClassLoader(Class type1, Class type2) {
        if (type1.getPackage() != type2.getPackage()) {
            return false;
        }
        ClassLoader loader1 = type1.getClassLoader();
        ClassLoader loader2 = type2.getClassLoader();
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (loader1 == null) {
            return (loader2 == null || loader2 == systemClassLoader);
        }
        if (loader2 == null) return loader1 == systemClassLoader;
        return loader1 == loader2;
    }

    static private ClassLoader getParentClassLoader(Class type) {
        ClassLoader parent = type.getClassLoader();
        if (parent == null) parent = ClassLoader.getSystemClassLoader();
        return parent;
    }

    static private Method getDefineClassMethod() {
        if (defineClassMethod == null) {
            synchronized (accessClassLoaders) {
                if (defineClassMethod == null) {
                    try {
                        defineClassMethod = ClassLoader.class.getDeclaredMethod("defineClass1",
                                ClassLoader.class, String.class, byte[].class, int.class, int.class, ProtectionDomain.class, String.class);
                        loaderInvokeMode = 0;
                    } catch (NoSuchMethodException e) {
                        try {
                            defineClassMethod = ClassLoader.class.getDeclaredMethod("defineClass1",
                                    String.class, byte[].class, int.class, int.class, ProtectionDomain.class, String.class);
                            loaderInvokeMode = 1;
                        } catch (Exception ignored) {
                        }
                    }
                    try {
                        defineClassMethod.setAccessible(true);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return defineClassMethod;
    }

    public static AccessClassLoader get(Class type) {
        ClassLoader parent = getParentClassLoader(type);
        // 1. fast-path:
        if (selfContextParentClassLoader.equals(parent)) {
            if (selfContextAccessClassLoader == null) {
                synchronized (accessClassLoaders) { // DCL with volatile semantics
                    if (selfContextAccessClassLoader == null)
                        selfContextAccessClassLoader = new AccessClassLoader(selfContextParentClassLoader);
                }
            }
            return selfContextAccessClassLoader;
        }
        // 2. normal search:
        synchronized (accessClassLoaders) {
            WeakReference<AccessClassLoader> ref = accessClassLoaders.get(parent);
            if (ref != null) {
                AccessClassLoader accessClassLoader = ref.get();
                if (accessClassLoader != null)
                    return accessClassLoader;
                else
                    accessClassLoaders.remove(parent); // the value has been GC-reclaimed, but still not the key (defensive sanity)
            }
            AccessClassLoader accessClassLoader = new AccessClassLoader(parent);
            accessClassLoaders.put(parent, new WeakReference<AccessClassLoader>(accessClassLoader));
            return accessClassLoader;
        }
    }

    static public void remove(ClassLoader parent) {
        // 1. fast-path:
        if (selfContextParentClassLoader.equals(parent)) {
            selfContextAccessClassLoader = null;
        } else {
            // 2. normal search:
            synchronized (accessClassLoaders) {
                accessClassLoaders.remove(parent);
            }
        }
    }

    static public int activeAccessClassLoaders() {
        int sz = accessClassLoaders.size();
        if (selfContextAccessClassLoader != null) sz++;
        return sz;
    }
}