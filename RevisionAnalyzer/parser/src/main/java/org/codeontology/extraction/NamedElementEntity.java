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

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Resource;
import org.codeontology.BGOntology;
import org.codeontology.CodeOntology;
import org.codeontology.neo4j.Neo4jLogger;
import spoon.SpoonException;
import spoon.reflect.declaration.CtNamedElement;
import spoon.reflect.reference.CtReference;

import java.io.File;

public abstract class NamedElementEntity<E extends CtNamedElement> extends CodeElementEntity<E> {

    private CtReference reference;

    protected NamedElementEntity(E element) {
        setElement(element);
    }

    protected NamedElementEntity(CtReference reference) {
        setReference(reference);
    }

    @SuppressWarnings("unchecked")
    private void setReference(CtReference reference) {
        if (reference == null) {
            throw new IllegalArgumentException();
        }

        this.reference = reference;
        try {
            if (reference.getDeclaration() != null && getElement() == null) {
                setElement((E) reference.getDeclaration());
            }
        }catch (SpoonException e){
        }
    }

    @Override
    protected void setElement(E element) {
        if (element == null) {
            throw new IllegalArgumentException();
        }
        super.setElement(element);
        if (reference == null && element != null) {
            try {
                this.reference = element.getReference();
            } catch (ClassCastException|NullPointerException e) {
                // leave reference null
            }
        }
    }

    public CtReference getReference() {
        return reference;
    }

    @Override
    public void setParent(Entity<?> parent) {
        super.setParent(parent);
        if(reference!=null && parent != null) {
            reference.setParent(((NamedElementEntity<?>) parent).getReference());
        }
    }

    public String getName() {
        return getReference().getSimpleName();
    }

    public void tagPosition(){
        String position = getElement().getPosition().toString();
        if(getElement().isImplicit()){
            position = getElement().getParent().getPosition().toString();//隐式方法则用所属类的路径
        }
        position = position.substring(1, position.length()-1);//去掉括号
        if(getType().equals(BGOntology.METHOD_ENTITY) && getElement().isImplicit()
                || getType().equals(BGOntology.CLASS_ENTITY)){
            position = position.split(":")[0];//只取类所在的路径，去除行号
        }
        position = position.replace(CodeOntology.getProjectPath(), CodeOntology.getProjectID());
        getLogger().addTriple(this, BGOntology.POSITION_PROPERTY, getModel().createLiteral(position));
    }

    public String splitCamelCase(String s) {
        return s.replaceAll(
            String.format("%s|%s|%s",
                "(?<=[A-Z])(?=[A-Z][a-z])",
                "(?<=[^A-Z])(?=[A-Z])",
                "(?<=[A-Za-z])(?=[^A-Za-z])"
            ), " "
        );
    }

    @Override
    public void follow() {
        if (!isDeclarationAvailable() && !CodeOntology.isJarExplorationEnabled()
                && EntityRegister.getInstance().add(this)) {
            extract();
        }
    }

    public boolean isDeclarationAvailable() {
        return getElement() != null;
    }

}