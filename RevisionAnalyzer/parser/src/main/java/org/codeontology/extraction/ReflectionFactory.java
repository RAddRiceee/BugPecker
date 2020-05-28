/*
Copyright 2017 Mattia Atzeni, Maurizio Atzori

This file is part of CodeOntology.

CodeOntology is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

CodeOntology is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with CodeOntology.  If not, see <http://www.gnu.org/licenses/>
*/

package org.codeontology.extraction;

import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.*;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReflectionFactory {

    private static ReflectionFactory instance;
    private Set<TypeVariable> previousVariables;
    private Factory parent;

    private ReflectionFactory() {
        previousVariables = new HashSet<>();
    }

    public static ReflectionFactory getInstance() {
        if (instance == null) {
            instance = new ReflectionFactory();
        }

        return instance;
    }

    public static void clean(){
        instance = null;
    }

    public CtTypeReference<?> createParameterizedTypeReference(ParameterizedType parameterizedType) {
        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
        Type rawType = parameterizedType.getRawType();
        CtTypeReference<?> reference;

        if (rawType instanceof Class) {
            reference = getParent().Type().createReference((Class) rawType);
        } else {
            reference = getParent().Type().createReference(rawType.getTypeName());
        }

        for (Type actualArgument : actualTypeArguments) {
            reference.addActualTypeArgument(createTypeReference(actualArgument));
        }

        return reference;
    }

    public CtTypeReference createTypeReference(Type type) {
        CtTypeReference reference;
        if (type instanceof ParameterizedType) {
            reference = createParameterizedTypeReference((ParameterizedType) type);
        } else if (type instanceof TypeVariable) {
            reference = createTypeVariableReference((TypeVariable) type);
        } else if (type instanceof GenericArrayType) {
            reference = createGenericArrayReference((GenericArrayType) type);
        } else if (type instanceof Class) {
            reference = getParent().Type().createReference((Class) type);
        } else if (type instanceof WildcardType) {
            reference = createWildcardReference((WildcardType) type);
        } else {
            reference = getParent().Type().createReference(type.getTypeName());
        }

        return reference;
    }

    public CtTypeReference createGenericArrayReference(GenericArrayType array) {
        Type type = array;

        int i = 0;
        do {
            i++;
            type = ((GenericArrayType) type).getGenericComponentType();
        } while (type instanceof GenericArrayType);

        CtTypeReference<?> componentType;

        if (type instanceof TypeVariable) {
            componentType = createTypeVariableReference((TypeVariable) type);
        } else {
            componentType = createParameterizedTypeReference((ParameterizedType) type);
        }
        return getParent().Type().createArrayReference(componentType, i);
    }

    public CtTypeParameterReference createTypeVariableReference(TypeVariable typeVariable) {
        if (previousVariables.contains(typeVariable)) {
            return getParent().Type().createTypeParameterReference(typeVariable.getName());
        }

        previousVariables.add(typeVariable);

        String name = typeVariable.getName();

        previousVariables.remove(typeVariable);

        return getParent().Type().createTypeParameterReference(name);
    }

    public CtTypeParameterReference createWildcardReference(WildcardType wildcard) {
        String name = "?";
        Type[] upperBounds = wildcard.getUpperBounds();
        Type[] lowerBounds = wildcard.getLowerBounds();

        List<CtTypeReference<?>> boundsList = new ArrayList<>();

        for (Type bound : upperBounds) {
            boundsList.add(createTypeReference(bound));
        }

        for (Type bound: lowerBounds) {
            boundsList.add(createTypeReference(bound));
        }

        CtTypeParameterReference wildcardReference = getParent().Type().createTypeParameterReference(name);

        return wildcardReference;
    }

    public Executable createActualExecutable(CtExecutableReference<?> executableReference) {
        Executable executable = null;
        Class<?> declaringClass;

        try {
            executable = executableReference.getActualMethod();

            if (executable == null) {
                executable = executableReference.getActualConstructor();
            }

            try {
                declaringClass = executable.getDeclaringClass();
            } catch (Exception | Error e) {
                declaringClass = Class.forName(executableReference.getDeclaringType().getQualifiedName());
            }

        } catch (Exception | Error e) {
            declaringClass = null;
        }

        if (executable == null && declaringClass != null) {
            Executable[] executables = declaringClass.getDeclaredMethods();
            if (executableReference.isConstructor()) {
                executables = declaringClass.getDeclaredConstructors();
            }

            for (Executable current : executables) {
                if (current.getName().equals(executableReference.getSimpleName()) || current instanceof Constructor) {
                    if (current.getParameterCount() == executableReference.getParameters().size()) {
                        List<CtTypeReference<?>> parameters = executableReference.getParameters();
                        Class<?>[] classes = new Class<?>[parameters.size()];
                        for (int i = 0; i < parameters.size(); i++) {
                            classes[i] = parameters.get(i).getActualClass();
                        }

                        boolean acc = true;

                        Class<?>[] parameterTypes = current.getParameterTypes();
                        for (int i = 0; i < classes.length && acc; i++) {
                            acc = classes[i].isAssignableFrom(parameterTypes[i]);
                        }

                        if (acc) {
                            executable = current;
                            break;
                        }
                    }
                }
            }
        }

        return executable;
    }

    public CtTypeReference<?> createTypeReference(Class<?> clazz) {
        return getParent().Class().createReference(clazz);
    }

    public CtPackageReference createPackageReference(Package pack) {
        return getParent().Package().createReference(pack);
    }

    public CtExecutableReference<?> createMethod(Method method) {
        return getParent().Method().createReference(method);
    }

    public CtExecutableReference<?> createConstructor(Constructor constructor) {
        return getParent().Constructor().createReference(constructor);
    }

    public CtFieldReference<?> createField(Field field) {
        return getParent().Field().createReference(field);
    }

    public void setParent(Factory parent) {
        this.parent = parent;
    }

    public Factory getParent() {
        return parent;
    }

    public <T extends CtElement> T clone(T t) {
        return getParent().Core().clone(t);
    }
}