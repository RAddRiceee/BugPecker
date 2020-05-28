package org.codeontology.neo4j;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.hp.hpl.jena.rdf.model.*;
import com.opencsv.CSVWriter;
import org.codeontology.BGOntology;
import org.neo4j.driver.v1.Transaction;

import java.io.*;
import java.util.*;

import static org.neo4j.driver.v1.Values.parameters;

public class Neo4jCsvLogger extends Neo4jLogger {

    private static String csvDir = "/usr/local/Cellar/neo4j/3.5.9/libexec/import/";
    private static List<String> nodeHeaders = new ArrayList<>(Arrays.asList(
            "uri",
            "label",
            getPrefixedName(BGOntology.COMMIT_ID_PROPERTY.toString()),
            getPrefixedName(BGOntology.NOT_DECLARED_PROPERTY.toString()),
            getPrefixedName(BGOntology.IS_CONSTRUCTOR_PROPERTY.toString()),
            getPrefixedName(BGOntology.DELETE_VERSION_PROPERTY.toString()),
            getPrefixedName(BGOntology.SOURCE_CODE_PROPERTY.toString()),
            getPrefixedName(BGOntology.POSITION_PROPERTY.toString())
    ));
    private static int nodeColNum = nodeHeaders.size();

    private static List<String> relationHeaders = new ArrayList<>(Arrays.asList("node1", "relation", "node2"));
    private static int relationColNum = relationHeaders.size();

    private static List<String> labelList = new ArrayList<>(Arrays.asList(
            getPrefixedName(BGOntology.CLASS_ENTITY.toString()),
            getPrefixedName(BGOntology.API_ENTITY.toString()),
            getPrefixedName(BGOntology.METHOD_ENTITY.toString()),
            getPrefixedName(BGOntology.PARAMETER_ENTITY.toString()),
            getPrefixedName(BGOntology.VARIABLE_ENTITY.toString())
    ));

    private static List<String> relationList = new ArrayList<>(Arrays.asList(
            getPrefixedName(BGOntology.CALL_PROPERTY.toString()),
            getPrefixedName(BGOntology.INSTANCE_OF_PROPERTY.toString()),
            getPrefixedName(BGOntology.RETURN_TYPE_PROPERTY.toString()),
            getPrefixedName(BGOntology.INHERITANCE_PROPERTY.toString()),
            getPrefixedName(BGOntology.HAS_PROPERTY.toString()),
            getPrefixedName(BGOntology.UPDATE_PROPERTY.toString())
    ));

    private String nodeCsvFile;
    private String relationCsvFile;
    private boolean headerWrited = false;
    private List<String[]> sourceCodeList = new ArrayList<>();

    public Neo4jCsvLogger(String identifier){
        Neo4jLogger.getInstance();
        this.nodeCsvFile = identifier + "_node.csv";
        this.relationCsvFile = identifier + "_relation.csv";
    }

    public void writeRDFtoCsv(Model model){
        System.out.println("saving "+ model.size() +" turples to csvFile..");
        List<String[]> nodeList = new ArrayList<>();
        List<String[]> relationList = new ArrayList<>();
        //写入头部
        if(!headerWrited) {
            String[] nodeHeader = nodeHeaders.toArray(new String[nodeColNum]);
            nodeList.add(nodeHeader);
            String[] relationHeader = relationHeaders.toArray(new String[relationColNum]);
            relationList.add(relationHeader);
        }

        //遍历所有主语
        ResIterator resIterator=model.listSubjects();
        while(resIterator.hasNext()){
            Resource resource = resIterator.next();

            String[] nodeLine = new String[nodeColNum];

            nodeLine[0] = resource.toString();//主语节点

            StmtIterator stmtIterator = resource.listProperties();
            while (stmtIterator.hasNext()){//遍历主语发起的所有三元组
                Statement statement = stmtIterator.next();
                if(statement.getObject().isLiteral()){
                    //添加属性
                    String propertyName = getPrefixedName(statement.getPredicate().toString());
                    String propertyValue = statement.getObject().asLiteral().getLexicalForm();
                    if(propertyName.equals(getPrefixedName(BGOntology.SOURCE_CODE_PROPERTY.toString()))){
                        sourceCodeList.add(new String[]{resource.toString(), propertyValue});
                    }else {
                        nodeLine[nodeHeaders.indexOf(propertyName)] = propertyValue;
                    }
                }else if(statement.getPredicate().toString().equals(RDF_TYPE)){
                    //添加标签，表示所属本体类别
                    nodeLine[1] = getPrefixedName(statement.getObject().toString());
                }else{
                    //添加宾语节点和关系
                    String[] relationLine = new String[relationColNum];
                    relationLine[0] = resource.toString();
                    relationLine[1] =getPrefixedName(statement.getPredicate().toString());
                    relationLine[2] = statement.getObject().toString();
                    relationList.add(relationLine);
                }
            }
            nodeList.add(nodeLine);
        }

        writeToCSV(nodeList, nodeCsvFile);
        writeToCSV(relationList, relationCsvFile);

        Neo4jLogger.getInstance().updateVersionAndCommitIdList();
    }

    public void loadCsvToNeo4j(){
        //导入nodeCsvFile
        System.out.println("loading csvFile "+ nodeCsvFile +" to neo4j..");
        String statement = "LOAD CSV WITH HEADERS FROM 'file:///"+ nodeCsvFile +"' AS row\n" +
                "MERGE (n:Resource{uri: row.uri})\n";
        for(int i=2;i<nodeHeaders.size();i++){
            String property = getPrefixedName(nodeHeaders.get(i).toString());
            statement += String.format("FOREACH (_ IN case when row.%s <> '' then [1] else [] end| SET n.%s = row.%s)\n", property, property, property);
        }
        for(String label :labelList){
            statement += "FOREACH (_ IN case when row.label = '"+label+"' then [1] else [] end| SET n:"+label+")\n";
        }
        exeStatementWithoutTrans(statement);

        //导入relationCsvFile
        System.out.println("loading csvFile "+ relationCsvFile +" to neo4j..");
        statement = "LOAD CSV WITH HEADERS FROM 'file:///"+ relationCsvFile +"' AS row\n" +
                "MERGE (n1:Resource{uri: row.node1})" +
                "MERGE (n2:Resource{uri: row.node2})\n";
        for(int i=0;i<relationList.size();i++){
            statement+="FOREACH (_ IN case when row.relation = '"+relationList.get(i)+"' then [1] else [] end| MERGE (n1)-[:"+relationList.get(i)+"]->(n2))\n";
        }
        exeStatementWithoutTrans(statement);

        //导入sourceCodeList
        System.out.println("writing sourceCodeList(size= "+ sourceCodeList.size() +") to neo4j..");
        String sourceCodeRelation = getPrefixedName(BGOntology.SOURCE_CODE_PROPERTY.toString());
        List<Map<String,String>> sourceArray = new ArrayList<>();
        for(String[] line : sourceCodeList){
            Map<String, String> map = new HashMap<>();
            map.put("uri", line[0]);
            map.put("sourceCode", line[1]);
            sourceArray.add(map);

//            exeStatementWithoutTrans("MATCH (n:Resource{uri:{uri}}) SET n."+sourceCodeRelation+" = {sourceCode}", "uri", line[0], "sourceCode", line[1]);
        }
        exeStatementWithoutTrans("UNWIND {sourceData} AS source MATCH (n:Resource{uri:source.uri}) SET n."+sourceCodeRelation+" = source.sourceCode", parameters("sourceData", sourceArray));
    }

    public void postClean(){
        //删除文件
        try {
            File csvFile = new File(csvDir + nodeCsvFile);
            if(csvFile.exists()) {
                csvFile.delete();
            }
            csvFile = new File(csvDir + relationCsvFile);
            if(csvFile.exists()) {
                csvFile.delete();
            }
            sourceCodeList=new ArrayList<>();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * CSV文件生成方法
     * @param dataList 数据列表,第一行为头部
     * @param filename 文件名
     * @return
     */
    public static void writeToCSV(List<String[]> dataList, String filename) {
        try {
            File csvFile = new File(csvDir + filename);
            if (!csvFile.exists()) csvFile.createNewFile();

            CSVWriter writer = new CSVWriter(new OutputStreamWriter(new FileOutputStream(csvFile, true), "GBK"));
            writer.writeAll(dataList);
            writer.flush();
            writer.close();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void zeroCSV(){
        zeroFile(nodeCsvFile);
        zeroFile(relationCsvFile);
        sourceCodeList = new ArrayList<>();
    }

    private static void zeroFile(String filename){
        try {
            File csvFile = new File(csvDir + filename);
            if(!csvFile.exists()) {
                csvFile.createNewFile();
            }
            FileWriter fileWriter =new FileWriter(csvFile);
            fileWriter.write("");
            fileWriter.flush();
            fileWriter.close();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setCsvDir(String csvDir) {
        Neo4jCsvLogger.csvDir = csvDir;
    }
}
