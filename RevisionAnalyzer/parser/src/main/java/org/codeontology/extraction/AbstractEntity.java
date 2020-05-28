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


import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import org.apache.commons.lang3.StringUtils;
import org.codeontology.BGOntology;
import org.codeontology.CodeOntology;
import org.codeontology.neo4j.Neo4jLogger;
import org.codeontology.neo4j.Neo4jNode;
import org.codeontology.neo4j.Neo4jQuery;

public abstract class AbstractEntity<E> implements Entity<E>, Comparable<Entity<?>> {

    private E element;
    private static Model model = RDFLogger.getInstance().getModel();
    private Entity<?> parent;
    private String uri;

    protected AbstractEntity() {

    }

    protected AbstractEntity(E element) {
        setElement(element);
    }

    @Override
    public E getElement() {
        return element;
    }

    protected void setElement(E element) {
        this.element = element;
    }

    public Resource getResource() {
        String nameVersion = getNameVersion();
        return RDFLogger.getInstance().createResource(nameVersion);
    }

    public static Resource getResource(String name){
        return getResource(name, null);
    }

    public static Resource getResource(String name, String version){
        if(StringUtils.isEmpty(version)){
            return RDFLogger.getInstance().createResource(Neo4jLogger.getNameVersion(name, CodeOntology.getVersion()));
        }
        return RDFLogger.getInstance().createResource(Neo4jLogger.getNameVersion(name, version));
    }

    public Resource getLastVersionResource(){
        Neo4jNode lastVersionEntity = Neo4jQuery.getLastVersionEntity(getRelativeURI(), getType());
        if(lastVersionEntity == null){
            return null;
        }
        String uri = lastVersionEntity.uri();
        return model.createResource(uri);
    }

    public String getNameVersion(){
        return Neo4jLogger.getNameVersion(getRelativeURI(), CodeOntology.getVersion());
    }

    protected abstract String buildRelativeURI();

    public void tagType() {
        getLogger().addTriple(this, BGOntology.RDF_TYPE_PROPERTY, getType());
    }

    protected abstract RDFNode getType();

    public void tagSourceCode() {
        getLogger().addTriple(this, BGOntology.SOURCE_CODE_PROPERTY, model.createLiteral(getSourceCode()));
    }

    public String getSourceCode() {
        return getElement().toString();
    }

    public void tagCommitID(){
        getLogger().addTriple(this, BGOntology.COMMIT_ID_PROPERTY, model.createLiteral(CodeOntology.getCommitID()));
    }

    @Override
    public Model getModel() {
        return model;
    }

    @Override
    public EntityFactory getFactory() {
        return EntityFactory.getInstance();
    }

    @Override
    public RDFLogger getLogger() {
        return RDFLogger.getInstance();
    }

    @Override
    public Entity<?> getParent() {
        return parent;
    }

    @Override
    public void setParent(Entity<?> parent) {
        this.parent = parent;
    }

    @Override
    public void follow() {
        extract();
    }

    @Override
    public final String getRelativeURI() {
        if (uri == null) {
            uri = buildRelativeURI();
        }

        return uri;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof Entity<?>)) {
            return false;
        }

        Entity<?> other = (Entity<?>) object;
        return other.getRelativeURI().equals(this.getRelativeURI());
    }

    @Override
    public int hashCode() {
        return getRelativeURI().hashCode();
    }

    public Entity<?> getParent(Class<?>... classes) {
        Entity<?> parent = getParent();
        while (parent != null) {
            for (Class<?> currentClass : classes) {
                if (currentClass.isAssignableFrom(parent.getClass())) {
                    return parent;
                }
            }
            parent = parent.getParent();
        }

        return null;
    }

    public int compareTo(Entity<?> other) {
        return this.getRelativeURI().compareTo(other.getRelativeURI());
    }
}

