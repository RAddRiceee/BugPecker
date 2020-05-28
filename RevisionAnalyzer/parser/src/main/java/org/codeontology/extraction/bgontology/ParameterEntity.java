package org.codeontology.extraction.bgontology;

import com.hp.hpl.jena.rdf.model.RDFNode;
import org.codeontology.BGOntology;
import org.codeontology.extraction.Entity;
import org.codeontology.extraction.NamedElementEntity;
import org.codeontology.extraction.support.JavaTypeTagger;
import org.codeontology.extraction.support.TypedElementEntity;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;

public class ParameterEntity extends NamedElementEntity<CtParameter<?>> implements TypedElementEntity<CtParameter<?>> {

    private MethodEntity parent;
    private static final String TAG = "parameter";
    private int position;

    public ParameterEntity(CtParameter<?> parameter) {
        super(parameter);
    }

    public ParameterEntity(CtTypeReference<?> reference) {
        super(reference);
    }

    @Override
    protected RDFNode getType() {
        return BGOntology.PARAMETER_ENTITY;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void setParent(MethodEntity parent) {
        this.parent = parent;
    }

    public MethodEntity getParent() {
        return this.parent;
    }

    @Override
    public void extract() {
        tagType();
        tagCommitID();
        tagJavaType();
    }

    @Override
    public String buildRelativeURI() {
        Entity<?> parentEntity =  getParent();
        if(parentEntity == null){
            parentEntity = super.getParent();
        }
        return parentEntity.getRelativeURI() + SEPARATOR + TAG + SEPARATOR + position;
    }

    @Override
    public TypeEntity<?> getJavaType() {
        TypeEntity<?> type;
        if (isDeclarationAvailable()) {
            type = getFactory().wrap(getElement().getType());
        } else {
            type = getFactory().wrap((CtTypeReference<?>) getReference());
        }
        if(type!=null) {
            type.setParent(parent);
        }
        return type;
    }

    @Override
    public void tagJavaType() {
        new JavaTypeTagger(this).tagJavaType();
    }
}
