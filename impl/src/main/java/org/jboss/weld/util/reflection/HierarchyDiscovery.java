/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.weld.util.reflection;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.jboss.weld.util.collections.ArraySet;
import org.slf4j.ext.XLogger.Level;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.security.AccessControlException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.jboss.weld.logging.messages.UtilMessage.SECURITY_EXCEPTION_SCANNING;

/**
 * @author Weld Community
 * @author Ales Justin
 * @author Marko Luksa
 */
public class HierarchyDiscovery {

    private final Type type;

    private BiMap<Type, Class<?>> types;
    private Map<Class<?>, Type> cache = new HashMap<Class<?>, Type>();

    public HierarchyDiscovery(Type type) {
        this.type = type;
    }

    protected void add(Class<?> clazz, Type type) {
        types.forcePut(type, clazz);
        cache.put(clazz, type);
    }

    public Set<Type> getTypeClosure() {
        if (types == null) {
            init();
            cache = null;
        }
        // Return an independent set with no ties to the BiMap used
        return new ArraySet<Type>(types.keySet()).trimToSize();
    }

    public Map<Class<?>, Type> getTypeMap() {
        if (types == null) {
            init();
        }
        return types.inverse();
    }

    private void init() {
        this.types = HashBiMap.create();
        try {
            discoverTypes(type);
        } catch (StackOverflowError e) {
            System.out.println("type" + type);
            Thread.dumpStack();
            throw e;
        }
    }

    public Type getResolvedType() {
        if (type instanceof Class<?>) {
            Class<?> clazz = (Class<?>) type;
            return resolveType(clazz);
        }
        if (type instanceof RawType<?>) {
            RawType<?> rawType = (RawType<?>) type;
            return rawType.getType();
        }
        return type;
    }

    private void discoverTypes(Type type) {
        if (type != null) {
            if (type instanceof RawType<?>) {
                RawType<?> rawType = (RawType<?>) type;
                add(rawType.getType(), rawType.getType());
                discoverFromClass(rawType.getType());
            } else if (type instanceof Class<?>) {
                Class<?> clazz = (Class<?>) type;
                add(clazz, resolveType(clazz));
                discoverFromClass(clazz);
            } else if (type instanceof GenericArrayType) {
                GenericArrayType arrayType = (GenericArrayType) type;
                Type genericComponentType = arrayType.getGenericComponentType();
                Class<?> rawComponentType = Reflections.getRawType(genericComponentType);
                if (rawComponentType != null) {
                    Class<?> arrayClass = Array.newInstance(rawComponentType, 0).getClass();
                    add(arrayClass, type);
                    discoverFromClass(arrayClass);
                }
            } else {
                if (type instanceof ParameterizedType) {
                    Type rawType = ((ParameterizedType) type).getRawType();
                    if (rawType instanceof Class<?>) {
                        Class<?> clazz = (Class<?>) rawType;
                        discoverFromClass(clazz);
                        add(clazz, type);
                    }
                }
            }
        }
    }

    private Type resolveType(Class<?> clazz) {
        if (clazz.getTypeParameters().length > 0) {
            TypeVariable<?>[] actualTypeParameters = clazz.getTypeParameters();
            return new ParameterizedTypeImpl(clazz, actualTypeParameters, clazz.getDeclaringClass());
        } else {
            return clazz;
        }
    }

    private void discoverFromClass(Class<?> clazz) {
        try {
            discoverTypes(resolveType(getUnwrappedType(), getUnwrappedType(), clazz.getGenericSuperclass()));
            for (Type c : clazz.getGenericInterfaces()) {
                discoverTypes(resolveType(getUnwrappedType(), getUnwrappedType(), c));
            }
        } catch (AccessControlException e) {
            // TODO Hmm, is this a hack?
            Reflections.log.trace(SECURITY_EXCEPTION_SCANNING, clazz);
            Reflections.xLog.throwing(Level.TRACE, e);
        }
    }

    /**
     * Gets the actual types by resolving TypeParameters.
     *
     * @param beanType  the bean type
     * @param beanType2 the initial bean type
     * @param type      current bean type
     * @return actual type
     */
    private Type resolveType(Type beanType, Type beanType2, Type type) {
        if (type instanceof ParameterizedType) {
            if (beanType instanceof ParameterizedType) {
                return resolveParameterizedType((ParameterizedType) beanType, (ParameterizedType) type);
            }
            if (beanType instanceof Class<?>) {
                return resolveType(((Class<?>) beanType).getGenericSuperclass(), beanType2, type);
            }
        }

        if (type instanceof TypeVariable<?>) {
            if (beanType instanceof ParameterizedType) {
                return resolveTypeParameter((ParameterizedType) beanType, beanType2, (TypeVariable<?>) type);
            }
            if (beanType instanceof Class<?>) {
                return resolveType(((Class<?>) beanType).getGenericSuperclass(), beanType2, type);
            }
        }
        return type;
    }

    private Type resolveParameterizedType(ParameterizedType beanType, ParameterizedType parameterizedType) {
        Type rawType = parameterizedType.getRawType();
        Type[] actualTypes = parameterizedType.getActualTypeArguments();

        Type resolvedRawType = resolveType(beanType, beanType, rawType);
        Type[] resolvedActualTypes = new Type[actualTypes.length];

        for (int i = 0; i < actualTypes.length; i++) {
            resolvedActualTypes[i] = resolveType(beanType, beanType, actualTypes[i]);
        }

        // reconstruct ParameterizedType by types resolved TypeVariable.
        ParameterizedTypeImpl pt = new ParameterizedTypeImpl(resolvedRawType, resolvedActualTypes, parameterizedType.getOwnerType());
        if (resolvedRawType instanceof Class<?>) {
            cache.put((Class<?>) resolvedRawType, pt); // cache things, we need it later
        }
        return pt;
    }

    private Type resolveTypeParameter(ParameterizedType type, Type beanType, TypeVariable<?> typeVariable) {
        // step1. raw type
        Class<?> actualType = (Class<?>) type.getRawType();
        TypeVariable<?>[] typeVariables = actualType.getTypeParameters();
        Type[] actualTypes = type.getActualTypeArguments();
        for (int i = 0; i < typeVariables.length; i++) {
            if (actualTypes[i] instanceof TypeVariable)
                continue; // still no idea on how to match

            if (typeVariables[i].equals(typeVariable) && !actualTypes[i].equals(typeVariable)) {
                return resolveType(getUnwrappedType(), beanType, actualTypes[i]);
            }
        }

        // step2. generic super class
        Class<?> superClass = actualType.getSuperclass();
        Type genericSuperType = cache.get(superClass); // did we resolve already
        if (genericSuperType == null)
            genericSuperType = actualType.getGenericSuperclass();

        Type resolvedGenericSuperType = resolveType(genericSuperType, beanType, typeVariable);
        if (!(resolvedGenericSuperType instanceof TypeVariable<?>)) {
            return resolvedGenericSuperType;
        }

        // step3. generic interfaces
        for (Type interfaceType : actualType.getGenericInterfaces()) {
            if (interfaceType instanceof ParameterizedType) {
                Type rawType = ((ParameterizedType) interfaceType).getRawType();
                if (rawType instanceof Class<?>) {
                    Type cached = cache.get(Class.class.cast(rawType));
                    if (cached != null)
                        interfaceType = cached;
                }
            }
            Type resolvedType = resolveType(interfaceType, interfaceType, typeVariable);
            if (!(resolvedType instanceof TypeVariable<?>)) {
                return resolvedType;
            }
        }

        // don't resolve type variable
        return typeVariable;
    }

    private Type getUnwrappedType() {
        return RawType.unwrap(type);
    }
}