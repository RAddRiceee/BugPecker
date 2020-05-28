package org.codeontology.extraction.bgontology;

import com.hp.hpl.jena.rdf.model.RDFNode;
import org.codeontology.BGOntology;
import org.codeontology.extraction.Entity;
import org.codeontology.extraction.NamedElementEntity;
import org.codeontology.extraction.ReflectionFactory;
import org.codeontology.extraction.support.JavaTypeTagger;
import org.codeontology.extraction.support.TypedElementEntity;
import org.codeontology.neo4j.Neo4jNode;
import org.codeontology.neo4j.Neo4jQuery;
import spoon.reflect.declaration.CtField;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;

public class FieldEntity extends NamedElementEntity<CtField<?>> implements TypedElementEntity<CtField<?>> {

    public FieldEntity(CtField<?> field) {
        super(field);
    }

    public FieldEntity(CtFieldReference<?> field) {
        super(field);
    }

    @Override
    protected RDFNode getType() {
        return BGOntology.VARIABLE_ENTITY;
    }

    @Override
    public void extract() {
        tagType();
        tagCommitID();
        tagJavaType();
    }

    public void tagJavaType() {
        new JavaTypeTagger(this).tagJavaType();
    }

    @Override
    public TypeEntity<?> getJavaType() {
        TypeEntity<?> type;
        if (isDeclarationAvailable()) {
            type = getFactory().wrap(getElement().getType());
        } else {
            type = getGenericType();
            if (type == null) {
                CtTypeReference<?> typeReference = ((CtFieldReference<?>) getReference()).getType();
                type = getFactory().wrap(typeReference);
            }
        }
        if(type != null) {
            type.setParent(getDeclaringElement());
        }
        return type;
    }


    private TypeEntity<?> getGenericType() {
        TypeEntity<?> result = null;
        if (isDeclarationAvailable()) {
            return null;
        }
        try {
            CtFieldReference<?> reference = ((CtFieldReference<?>) getReference());
            Field field = (Field) reference.getActualField();
            Type genericType = field.getGenericType();

            if (genericType instanceof GenericArrayType ||
                    genericType instanceof TypeVariable<?>) {

                result = getFactory().wrap(genericType);
            }

        } catch (Throwable t) {
            return null;
        }

        return result;
    }

    @Override
    public String buildRelativeURI() {
        return getDeclaringElement().getRelativeURI() + SEPARATOR + getReference().getSimpleName();
    }

    public static String transNameToURI(String fieldName, String className){
        return className + SEPARATOR + fieldName;
    }

    public Entity<?> getDeclaringElement() {
        if (isDeclarationAvailable()) {
            return getFactory().wrap(getElement().getDeclaringType());
        } else {
            CtFieldReference<?> reference = (CtFieldReference) getReference();
            CtTypeReference<?> declaringType = ReflectionFactory.getInstance().clone(reference.getDeclaringType());
            declaringType.setActualTypeArguments(new ArrayList<>());
            return getFactory().wrap(declaringType);
        }
    }

    /**
     * 版本更新时新增方法
     */
    public void addNewField(String className){
        //field extract
        extract();
        //class has field关系建立
        Neo4jNode lastClass = Neo4jQuery.getLastVersionEntity(className, BGOntology.CLASS_ENTITY);
        if(lastClass == null){
            throw new RuntimeException("cannot find last version class: className="+className+", field= "+getRelativeURI());
        }
        String uri = lastClass.uri();
        getLogger().addTriple(getModel().createResource(uri), BGOntology.HAS_PROPERTY, this.getResource());
    }
}
