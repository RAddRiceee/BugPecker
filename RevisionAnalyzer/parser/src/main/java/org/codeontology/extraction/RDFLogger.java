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

import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.rdf.model.Statement;
import org.codeontology.BGOntology;
import org.codeontology.CodeOntology;
import org.codeontology.neo4j.Neo4jCsvLogger;
import org.codeontology.neo4j.Neo4jLogger;

import java.io.*;

public class RDFLogger {
    private Model model;
    private String outputFile;
    private int counter;
    private Neo4jCsvLogger neo4jCsvLogger;

    private static RDFLogger instance;
    public static final int MAX_SIZE = 10000;

    private RDFLogger() {
        model = ModelFactory.createDefaultModel();
        outputFile = "triples.nt";
        counter = 0;
    }

    public static RDFLogger getInstance() {
        if (instance == null) {
            instance = new RDFLogger();
        }
        return instance;
    }

    public static void clean(){
        if(RDFLogger.getInstance().neo4jCsvLogger!= null) {
            RDFLogger.getInstance().neo4jCsvLogger.postClean();
        }
        instance = null;
    }

    public Model getModel() {
        return model;
    }

    public void setOutputFile(String path) {
        outputFile = path;
    }

    public void writeRDF() {
        if(!CodeOntology.isVersionUpdate() || model.size()>300 || neo4jCsvLogger!=null){//首次导入采用csv方式导入
            // save to neo4j database
            if(neo4jCsvLogger==null){
                setNeo4jCsvLogger();
            }
            neo4jCsvLogger.writeRDFtoCsv(model);
        }else {
            Neo4jLogger.getInstance().writeRDFtoNeo4j(model);
        }

//        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputFile, true)))) {
//            model.write(writer, "N-TRIPLE");
//        } catch (IOException e) {
//            System.out.println("Cannot write triples.");
//            System.exit(-1);
//        }
        free();
    }

    public Neo4jCsvLogger getNeo4jCsvLogger() {
        return neo4jCsvLogger;
    }

    public void setNeo4jCsvLogger(){
        String identifier = CodeOntology.getProjectID().replace(File.separator, "_") + "_" + CodeOntology.getCommitID() + "_" + System.currentTimeMillis();
        neo4jCsvLogger = new Neo4jCsvLogger(identifier);
    }

    public void addTriple(Entity<?> subject, Property property, Entity object) {
        addTriple(subject, property, object.getResource());
    }

    public void addTriple(Entity<?> subject, Property property, RDFNode object) {
        addTriple(subject.getResource(), property, object);
    }

    public synchronized void addTriple(Resource subject, Property property, RDFNode object){
        if (subject!=null && property != null && object != null) {
            Statement triple = model.createStatement(subject, property, object);
            model.add(triple);
            counter++;
            if (counter > MAX_SIZE) {
                writeRDF();
            }
        }
    }

    public Resource createResource(String nameVersion) {
        return model.createResource(nameVersion);
    }

    public String getURI(String nameVersion){
        return BGOntology.BGOntology_ + nameVersion;
    }

    private void free() {
        model = ModelFactory.createDefaultModel();
        counter = 0;
    }
}