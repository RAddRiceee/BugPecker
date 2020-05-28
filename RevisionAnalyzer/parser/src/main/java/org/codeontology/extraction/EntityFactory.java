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

import org.codeontology.extraction.bgontology.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;

import java.lang.reflect.Type;
import java.util.jar.JarFile;

public class EntityFactory {

    private static EntityFactory instance;

    private EntityFactory() {

    }

    public static EntityFactory getInstance() {
        if (instance == null) {
            instance = new EntityFactory();
        }
        return instance;
    }

    public PackageEntity wrap(CtPackage pack) {
        return new PackageEntity(pack);
    }

    public PackageEntity wrap(CtPackageReference pack) {
        return new PackageEntity(pack);
    }

    public FieldEntity wrap(CtField<?> field) {
        return new FieldEntity(field);
    }

    public FieldEntity wrap(CtFieldReference<?> field) {
        return new FieldEntity(field);
    }

    public MethodEntity wrap(CtExecutable<?> method) {
        try{
            method.getReference();
        }catch (NullPointerException e){
            return null;
        }
        return new MethodEntity(method);
    }

    public MethodEntity wrap(CtExecutableReference<?> reference) {
        return new MethodEntity(reference);
    }

    public TypeEntity wrap(CtType<?> type) {
        return wrap(type.getReference());
    }

    public TypeEntity wrap(CtTypeReference<?> reference) {
        if(reference==null){
            return null;
        }
        if (reference.getQualifiedName().equals(CtTypeReference.NULL_TYPE_NAME)) {
            return null;
        }
        TypeKind kind = TypeKind.getKindOf(reference);
        if(kind ==null){
            return null;
        }
        return new TypeEntity<>(reference);
    }

    public ParameterEntity wrap(CtParameter<?> parameter) {
        return new ParameterEntity(parameter);
    }

    public ParameterEntity wrapByTypeReference(CtTypeReference<?> reference) {
        return new ParameterEntity(reference);
    }

    public TypeEntity<?> wrap(Type type) {
        return wrap(reflectionFactory().createTypeReference(type));
    }

    private ReflectionFactory reflectionFactory() {
        return ReflectionFactory.getInstance();
    }

    public Entity<?> wrap(CtVariable<?> variable) {

        if (variable instanceof CtField<?>) {
            return new FieldEntity((CtField) variable);
        }
        if(variable instanceof CtParameter<?>){
            return new ParameterEntity((CtParameter<?>) variable);
        }
        return null;
    }

}