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

import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import org.apache.commons.collections.CollectionUtils;
import org.codeontology.BGOntology;
import org.codeontology.CodeOntology;
import org.codeontology.extraction.Entity;
import org.codeontology.extraction.NamedElementEntity;
import org.codeontology.extraction.RDFLogger;
import org.codeontology.extraction.ReflectionFactory;
import org.codeontology.neo4j.Neo4jNode;
import org.codeontology.neo4j.Neo4jQuery;
import org.codeontology.neo4j.Neo4jRelation;
import spoon.SpoonException;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TypeEntity<T extends CtType<?>> extends NamedElementEntity<T>{

    private List<MethodEntity> methods;
    private List<MethodEntity> constructors;
    private List<FieldEntity> fields;
    TypeKind kind;

    public TypeEntity(T type) {
        super(type);
        checkNullType();
        kind = TypeKind.getKindOf(getReference());
    }

    public TypeEntity(CtTypeReference<?> reference) {
        super(reference);
        checkNullType();
        kind = TypeKind.getKindOf(getReference());
    }

    private void checkNullType() {
        if (getReference().getQualifiedName().equals(CtTypeReference.NULL_TYPE_NAME)) {
            throw new RuntimeException("NullTypeException");
        }
    }

    @Override
    public String buildRelativeURI() {
        if(kind == null) {
            return getReference().getQualifiedName();
        }
        String uri;
        switch (kind){
            case PRIMITIVE:
                return getReference().getQualifiedName();
            case ARRAY:
                CtTypeReference<?> componentReference = ((CtArrayTypeReference<?>) getReference()).getArrayType();
                componentReference.setParent(getReference());
                TypeEntity<?> componentType = getFactory().wrap(componentReference);
                if(componentType == null){
                    return componentReference.getQualifiedName() + "[]";
                }
                return componentType.getRelativeURI() + "[]";
//            case PARAMETERIZED_TYPE:
//                uri = getReference().getQualifiedName();
//                String argumentsString = "";
//                Entity<?> parent = getParent();
//
//                List<CtTypeReference<?>> arguments = getReference().getActualTypeArguments();
//                if (!arguments.isEmpty() && arguments.get(0) instanceof CtIntersectionTypeReference<?>) {
//                    return uri + SEPARATOR + "diamond";
//                }
//                for (CtTypeReference<?> argument : arguments) {
//                    TypeEntity<?> argumentEntity = getFactory().wrap(argument);
//                    if(argumentEntity == null){
//                        argumentsString = argument.getQualifiedName();
//                        continue;
//                    }
//                    argumentEntity.setParent(parent);
//
//                    if (argumentsString.equals("")) {
//                        argumentsString = argumentEntity.getRelativeURI();
//                    } else {
//                        argumentsString = argumentsString + SEPARATOR + argumentEntity.getRelativeURI();
//                    }
//                }
//                uri = uri + "[" + argumentsString + "]";
//                uri = uri.replace(" ", "_");
//                return uri;
//            case TYPE_VARIABLE:
//                //TODO 非？的泛型标识符无法拿到边界
//                uri = getReference().getQualifiedName();
//
//                CtTypeParameterReference reference = (CtTypeParameterReference) getReference();
//                if(!reference.isDefaultBoundingType()) {
//                    List<CtTypeReference<?>> bounds = new ArrayList<>();
//                    CtTypeReference<?> boundingType = null;
//                    try {
//                        boundingType = reference.getBoundingType();
//                    } catch (SpoonException e) {
//                    }
//                    if (boundingType instanceof CtIntersectionTypeReference) {
//                        bounds = boundingType.asCtIntersectionTypeReference().getBounds();
//                    } else if (boundingType != null) {
//                        bounds.add(boundingType);
//                    }
//                    if (bounds.size() > 0) {
//                        String separator = "_";
//
//                        String clause = "extends";
//                        uri = uri + separator + clause;
//                        for (CtTypeReference bound : bounds) {
//                            TypeEntity<?> entity = getFactory().wrap(bound);
//                            entity.setParent(getParent());
//                            uri = uri + separator + entity.getRelativeURI();
//                        }
//                    }
//                    if (getReference().getSimpleName().equals("?") || getParent() == null) {
//                        return uri;
//                    }
//                }
//                return uri + ":" + getParent().getRelativeURI();
            default://Enum or Class
                return getReference().getQualifiedName();
        }
    }

    @Override
    protected RDFNode getType() {
        return BGOntology.CLASS_ENTITY;
    }

    @Override
    public void extract() {
        tagType();
        tagCommitID();
        if (!isDeclarationAvailable()) {
            tagNotDeclared();
            return;
        }
        tagPosition();
        if(TypeKind.getIgnoreTypeKind().contains(kind)){
            return;
        }
        tagPosition();
        tagSuperClass();
        tagFields();
        tagMethods();
        if(kind.equals(TypeKind.CLASS)){
            tagConstructors();
        }
    }

    public void tagNotDeclared() {
        getLogger().addTriple(this, BGOntology.NOT_DECLARED_PROPERTY, RDFLogger.getInstance().getModel().createLiteral("true"));
    }

    public void tagSuperClass() {
        CtTypeReference<?> superclass = getReference().getSuperclass();
        if (superclass == null) {
            return;
        }
        TypeEntity<?> superClass = getFactory().wrap(superclass);
        superClass.setParent(this);
        getLogger().addTriple(this, BGOntology.INHERITANCE_PROPERTY, superClass);
        superClass.follow();
    }

    public CtTypeReference<?> getReference() {
        return (CtTypeReference<?>) super.getReference();
    }

    public void tagMethods() {
        List<MethodEntity> methods = getMethods();
        methods.forEach(method ->
                getLogger().addTriple(this, BGOntology.HAS_PROPERTY, method)
        );
        methods.forEach(MethodEntity::extract);
    }

    public List<MethodEntity> getMethods() {
        if (methods == null) {
            setMethods();
        }
        return methods;
    }

    private void setMethods() {
        methods = new ArrayList<>();

        if (!isDeclarationAvailable()) {
            setMethodsByReflection();
            return;
        }
        Set<CtMethod<?>> ctMethods = getElement().getMethods();
        for (CtMethod ctMethod : ctMethods) {
            MethodEntity method = getFactory().wrap(ctMethod);
            if(method == null){
                continue;
            }
            method.setParent(this);
            methods.add(method);
        }
    }

    private void setMethodsByReflection() {
        try {
            Set<CtMethod<?>> actualMethods = getReference().getTypeDeclaration().getMethods();
            for (CtMethod<?> actualMethod : actualMethods) {
                MethodEntity method = getFactory().wrap(actualMethod);
                method.setParent(this);
                methods.add(method);
            }
        } catch (Throwable t) {
            showMemberAccessWarning();
        }
    }

    public void tagConstructors() {
        List<MethodEntity> constructors = getConstructors();
        constructors.forEach(constructor ->
                getLogger().addTriple(this, BGOntology.HAS_PROPERTY, constructor)
        );
        constructors.forEach(MethodEntity::extract);
    }

    public List<MethodEntity> getConstructors() {
        if (constructors == null) {
            setConstructors();
        }

        return constructors;
    }

    private void setConstructors() {
        constructors = new ArrayList<>();

        if(!(getElement() instanceof CtClass)){
            return;
        }

        if (!isDeclarationAvailable()) {
            setConstructorsByReflection();
            return;
        }

        CtClass<T> element = (CtClass<T>)getElement();
        Set<CtConstructor<T>> ctConstructors = element.getConstructors();
        for (CtConstructor ctConstructor : ctConstructors) {
            MethodEntity constructor = getFactory().wrap(ctConstructor);
            constructor.setParent(this);
            constructor.setIsConstructor(true);
            constructors.add(constructor);
        }
    }

    private void setConstructorsByReflection() {
        try {
            Constructor[] actualConstructors = getReference().getActualClass().getDeclaredConstructors();
            for (Constructor actualConstructor : actualConstructors) {
                CtExecutableReference<?> reference = ReflectionFactory.getInstance().createConstructor(actualConstructor);
                MethodEntity constructor = (MethodEntity) getFactory().wrap(reference);
                constructor.setParent(this);
                constructors.add(constructor);
            }
        } catch (Throwable t) {
            showMemberAccessWarning();
        }
    }

    public List<FieldEntity> getFields() {
        if (fields == null) {
            setFields();
        }

        return fields;
    }

    private void setFields() {
        fields = new ArrayList<>();
        if (!isDeclarationAvailable()) {
            setFieldsByReflection();
            return;
        }

        List<CtField<?>> ctFields = getElement().getFields();
        for (CtField<?> current : ctFields) {
            FieldEntity currentField = getFactory().wrap(current);
            currentField.setParent(this);
            fields.add(currentField);
        }
    }

    private void setFieldsByReflection() {
        try {
            List<CtField<?>> actualFields = getReference().getTypeDeclaration().getFields();
            for (CtField<?> current : actualFields) {
                FieldEntity currentField = getFactory().wrap(current);
                currentField.setParent(this);
                fields.add(currentField);
            }
        } catch (Throwable t) {
            showMemberAccessWarning();
        }
    }

    public void tagFields() {
        List<FieldEntity> fields = getFields();
        fields.forEach(field -> getLogger().addTriple(this, BGOntology.HAS_PROPERTY, field));
        fields.forEach(FieldEntity::extract);
    }

    protected void showMemberAccessWarning() {
        if (CodeOntology.verboseMode()) {
           CodeOntology.showWarning("Could not extract members of " + getReference().getQualifiedName());
        }
    }

    public TypeKind getKind() {
        return kind;
    }

    /**
     * 版本更新时修改类名
     */
    public void updateEntityName(String oldName){
        //建立新旧版本实体的update关系
        Neo4jNode lastClass = Neo4jQuery.getLastVersionEntity(oldName, BGOntology.CLASS_ENTITY);
        if(lastClass== null){
            throw new RuntimeException("cannot find last version Class: " + oldName + ", new Class Name: "+getReference().getQualifiedName());
        }
        String uri = lastClass.uri();
        getLogger().addTriple(getModel().createResource(uri), BGOntology.UPDATE_PROPERTY, getResource());

        tagType();
        tagCommitID();
        tagPosition();

        //复制本实体作为发出方的关系，包括has，inheritance
        List<String> relationTypes = new ArrayList<>();
        relationTypes.add(Neo4jQuery.prefixedName(BGOntology.HAS_PROPERTY));
        relationTypes.add(Neo4jQuery.prefixedName(BGOntology.INHERITANCE_PROPERTY));
        List<Neo4jRelation> relations = Neo4jQuery.getEntityEmitRelations(uri, BGOntology.CLASS_ENTITY, relationTypes);
        if(CollectionUtils.isEmpty(relations)){
            return;
        }
        for(Neo4jRelation relation : relations){
            String relationName = relation.getEdges().get(0);
            Neo4jNode destNode = relation.getNodes().get(relation.getNodes().size()-1);
            Property property = BGOntology.HAS_PROPERTY;
            if(Neo4jQuery.prefixedName(BGOntology.INHERITANCE_PROPERTY).equals(relationName)) {
                property = BGOntology.INHERITANCE_PROPERTY;
            }
            getLogger().addTriple(getResource(), property, getModel().createResource(destNode.uri()));
        }
    }
}