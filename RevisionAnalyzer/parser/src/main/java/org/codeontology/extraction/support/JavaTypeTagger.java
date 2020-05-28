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

package org.codeontology.extraction.support;

import com.hp.hpl.jena.rdf.model.Resource;
import org.codeontology.BGOntology;
import org.codeontology.CodeOntology;
import org.codeontology.extraction.RDFLogger;
import org.codeontology.extraction.bgontology.TypeEntity;
import org.codeontology.extraction.bgontology.TypeKind;

import java.util.ArrayList;
import java.util.Arrays;

public class JavaTypeTagger {

    private TypedElementEntity<?> typedElement;

    public JavaTypeTagger(TypedElementEntity<?> typedElement) {
        this.typedElement = typedElement;
    }

    public void tagJavaType() {
        TypeEntity<?> type = typedElement.getJavaType();
        if (type != null) {
            if(TypeKind.getIgnoreTypeKind().contains(type.getKind())){
                return;
            }
            Resource javaType = null;
            if(CodeOntology.isVersionUpdate()){
                javaType = type.getLastVersionResource();
            }
            if(javaType == null){
                javaType = type.getResource();
                RDFLogger.getInstance().addTriple(typedElement, BGOntology.INSTANCE_OF_PROPERTY, javaType);
                type.follow();
            }else{
                RDFLogger.getInstance().addTriple(typedElement, BGOntology.INSTANCE_OF_PROPERTY, javaType);
            }
        }
    }
}