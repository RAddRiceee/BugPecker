package org.codeontology;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.net.URLDecoder;

public class BGOntology {

    public static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";
    public static final String BGOntology_ = "http://www.semanticweb.org/BGOntology/";

    private static Model model = ontology();

    private static Model ontology() {
        try {
            File ontology = null;

            URL ontologyURL = BGOntology.class.getClassLoader().getResource("BGOntology.owl");
            String ontologyPath = URLDecoder.decode(ontologyURL.getPath(), "UTF-8" );

            if(!StringUtils.isEmpty(ontologyPath)){
                ontology = new File(ontologyPath);
            }
            if(ontology==null || !ontology.exists()){
                ontology = new File(System.getProperty("user.dir") + "/ontology/CodeOntology.owl");
            }
            if(!ontology.exists()){
                ontology = new File(System.getProperty("user.dir") + "/parser/ontology/CodeOntology.owl");
            }
            if(!ontology.exists()){
                String curPath = URLDecoder.decode(BGOntology.class.getResource("/").getPath(), "UTF-8");
                String path = curPath.split("/parser")[0] + "/parser/parser/ontology/BGOntology.owl";
                ontology = new File(path);
            }
            FileInputStream reader = new FileInputStream(ontology);
            return ModelFactory.createDefaultModel().read(reader, "");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Model getModel() {
        return model;
    }

    /**
     * Classes
     */
    public static final Resource CLASS_ENTITY = model.getResource(BGOntology_ + "Class");
    public static final Resource API_ENTITY = model.getResource(BGOntology_ + "API");
    public static final Resource METHOD_ENTITY = model.getResource(BGOntology_ + "Method");
    public static final Resource PARAMETER_ENTITY = model.getResource(BGOntology_ + "Parameter");
    public static final Resource VARIABLE_ENTITY = model.getResource(BGOntology_ + "Variable");

    /**
     * Object Properties
     */
    public static final Property CALL_PROPERTY = model.getProperty(BGOntology_ + "call");
    public static final Property INSTANCE_OF_PROPERTY = model.getProperty(BGOntology_ + "instanceof");
    public static final Property RETURN_TYPE_PROPERTY = model.getProperty(BGOntology_ + "returnType");
    public static final Property INHERITANCE_PROPERTY = model.getProperty(BGOntology_ + "inheritance");
    public static final Property HAS_PROPERTY = model.getProperty(BGOntology_ + "has");
    public static final Property UPDATE_PROPERTY = model.getProperty(BGOntology_ + "update");
    public static final Property SIMILAR_PROPERTY = model.getProperty(BGOntology_ + "similar");

    /**
     * Data Properties
     */
    public static final Property RDF_TYPE_PROPERTY = model.getProperty(RDF + "type");

    public static final Property COMMIT_ID_PROPERTY = model.getProperty(BGOntology_ + "commitID");
    public static final Property NOT_DECLARED_PROPERTY = model.getProperty(BGOntology_ + "notDeclared");
    public static final Property IS_CONSTRUCTOR_PROPERTY = model.getProperty(BGOntology_ + "isConstructor");
    public static final Property DELETE_VERSION_PROPERTY = model.getProperty(BGOntology_ + "deletedVer");
    public static final Property SOURCE_CODE_PROPERTY = model.getProperty(BGOntology_ + "sourceCode");
    public static final Property POSITION_PROPERTY = model.getProperty(BGOntology_ + "position");
}
