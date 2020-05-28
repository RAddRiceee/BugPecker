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

package org.codeontology.extraction.bgontology;

import spoon.reflect.declaration.CtAnnotationType;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.Arrays;

public enum TypeKind {
    INTERFACE, ANNOTATION, ENUM,
    CLASS,
    PRIMITIVE,
    ARRAY,
    TYPE_VARIABLE,
    PARAMETERIZED_TYPE;

    /**
     * 返回需要忽略不提取的类型
     * @return
     */
    public static ArrayList<TypeKind> getIgnoreTypeKind(){
        return new ArrayList<TypeKind>(Arrays.asList(TypeKind.INTERFACE, TypeKind.ENUM, TypeKind.ANNOTATION));
    }

    public static TypeKind getKindOf(CtTypeReference<?> reference) {


        if (reference instanceof CtArrayTypeReference<?>) {
            return ARRAY;
        }

        if (reference instanceof CtTypeParameterReference) {
            return TYPE_VARIABLE;
        }

        if (!reference.getActualTypeArguments().isEmpty()) {
            return PARAMETERIZED_TYPE;
        }

        if (reference.isPrimitive()) {
            return PRIMITIVE;
        }

        if(reference.isClass()){
            return CLASS;
        }if (reference.isAnnotationType()) {
            return ANNOTATION;
        } else if (reference.isEnum()) {
            return ENUM;
        } else if (reference.isInterface()) {
            return INTERFACE;
        }

        CtType<?> type = reference.getDeclaration();
        if (type != null) {
            return getKindOf(type);
        }

        try {
            CtType<?> actualClass = reference.getTypeDeclaration();
            if (actualClass.isAnnotationType()) {
                return ANNOTATION;
            } else if (actualClass.isEnum()) {
                return ENUM;
            } else if (actualClass.isInterface()) {
                return INTERFACE;
            } else {
                return CLASS;
            }
        } catch (Exception e) {
            System.out.println("TypeKind.getKindOf() exception:" + e.getMessage() + ", class: " + reference.getQualifiedName() + ", position: "+ reference.getPosition().toString());
            return CLASS;
        }

    }

    public static TypeKind getKindOf(CtType type) {

        if (type.getReference() instanceof CtArrayTypeReference) {
            return ARRAY;
        } else if (type.getReference().getActualTypeArguments().size() > 0) {
            return PARAMETERIZED_TYPE;
        } else if (type.isPrimitive()) {
            return PRIMITIVE;
        }  else if (type instanceof CtAnnotationType<?>) {
            return ANNOTATION;
        } else if (type instanceof CtEnum<?>) {
            return ENUM;
        } else if (type instanceof CtInterface<?>) {
            return INTERFACE;
        } else {
            return CLASS;
        }
    }
}