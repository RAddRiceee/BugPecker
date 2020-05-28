package org.codeontology.neo4j;

import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.rdf.model.Statement;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codeontology.BGOntology;
import org.codeontology.CodeOntology;
import org.codeontology.util.CommandUtil;
import org.codeontology.util.ThreadPoolUtil;
import org.neo4j.driver.v1.*;
import org.neo4j.driver.v1.exceptions.ServiceUnavailableException;

import java.text.DecimalFormat;
import java.util.*;

import static org.neo4j.driver.v1.Values.parameters;

/**
 * 将jena model中的RDF数据存储到neo4j中
 * 使用命名空间前缀，便于简写表示IRI
 * 转换规则如下：
 * 如果三元组宾语为Literal字面量，则作为节点属性；
 * 如果三元组谓语是RDF_TYPE，即表明其对应本体的类别，则宾语作为节点标签
 * 其他情况，主语宾语都作为节点，谓语作为节点间关系
 */
public class Neo4jLogger {

    private static Neo4jLogger instance;

    private String nextVersion;
    private List<String> versionList;
    private String commitId;
    private List<String> commitIdList;
    private Neo4jConfig neo4jConfig;
    private boolean firstWrite = true;

    public static final String RDF_TYPE = BGOntology.RDF_TYPE_PROPERTY.toString();//对应本体实体
    public static final String DEFAULT_VERSON = "1";
    public static final String VERSION_LIST = "version_list";
    public static final String COMMITID_LIST = "commit_id_list";

    public static final String NAME_SPACE_PREFIX = "NamespacePrefixDefinition";//命名空间前缀
    public static HashMap<String, String> namespacePrefixs = new HashMap<String, String>() {{
//        put("http://www.semanticweb.org/BGOntology/", "BGOntology");
//        put("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "rdf");
//        put("http://www.w3.org/2000/01/rdf-schema#", "rdfs");
//        put("http://www.w3.org/2002/07/owl#", "owl");
//        put("http://www.w3.org/2004/02/skos/core#", "skos");
        put(VERSION_LIST, "");
        put(COMMITID_LIST, "");
    }};

    protected Neo4jLogger(){
        neo4jConfig = Neo4jConfig.getInstance();
        neo4jConfig.session();
        //定义命名空间前缀
        StatementResult result = exeStatementWithoutTrans("MATCH (n:"+ NAME_SPACE_PREFIX +") RETURN count(n)");
        if(result.single().get(0).asInt() <= 0){
            StringBuilder sb = new StringBuilder("CREATE (n:"+ NAME_SPACE_PREFIX +"{");
            for (Map.Entry<String, String> entry : namespacePrefixs.entrySet()) {
                sb.append("`"+entry.getKey()+"`:'"+entry.getValue()+"',");
            }
            sb.deleteCharAt(sb.length()-1);
            sb.append("})");
            exeStatementWithoutTrans(sb.toString());
            //创建唯一性约束:Resource(uri)
            exeStatementWithoutTrans("CREATE CONSTRAINT ON (n:Resource) ASSERT n.uri IS UNIQUE");
        }

        //获取versionList和commitIdList
        result = exeStatementWithoutTrans("MATCH (n:"+ NAME_SPACE_PREFIX +") RETURN n");
        Map map = result.single().get(0).asMap();
        if(StringUtils.isEmpty((String)map.get(VERSION_LIST))){
            versionList = new ArrayList<>();
            commitIdList = new ArrayList<>();
        }else {
            String[] array = ((String) map.get(VERSION_LIST)).split(",");
            versionList = new ArrayList<>(Arrays.asList(array));
            array = ((String) map.get(COMMITID_LIST)).split(",");
            commitIdList = new ArrayList<>(Arrays.asList(array));
        }
    }

    public static Neo4jLogger getInstance() {
        if (instance == null) {
            instance = new Neo4jLogger();
        }
        return instance;
    }

    public static void clean(){
        instance = null;
    }

    public void writeRDFtoNeo4j(Model model){
        System.out.println("saving "+ model.size() +" turples to neo4j..");
//        Transaction transaction = neo4jConfig.session().beginTransaction();

        //遍历主语，添加主语节点
        ResIterator resIterator=model.listSubjects();
        while(resIterator.hasNext()){
            Resource resource = resIterator.next();
            createNode(resource);//创建主语节点
            StmtIterator stmtIterator = resource.listProperties();
            while (stmtIterator.hasNext()){//遍历主语发起的所有三元组
                Statement statement = stmtIterator.next();
                if(statement.getObject().isLiteral()){
                    //添加属性
                    String propertyName = getPrefixedName(statement.getPredicate().toString());
                    String propertyValue = statement.getObject().asLiteral().getLexicalForm();
                    exeStatementWithoutTrans("MATCH (n:Resource{uri:'"+statement.getSubject().toString()+"'}) SET n."+propertyName+" = {propertyValue}", "propertyValue", propertyValue);
                }else if(statement.getPredicate().toString().equals(RDF_TYPE)){
                    //添加标签，表示所属本体类别
                    String label =getPrefixedName(statement.getObject().toString());
                    exeStatementWithoutTrans("MATCH (n:Resource{uri:'"+statement.getSubject().toString()+"'}) SET n:"+label);
                }else{
                    //添加宾语节点和关系
                    createNode(statement.getObject());
                    createRelationship(statement);
                }
            }
        }
//        transaction.success();
//        transaction.close();

        updateVersionAndCommitIdList();
    }

    public void updateVersionAndCommitIdList(){
        //更新版本和commit列表
        if(!versionList.contains(getNextVersion())){
            versionList.add(getNextVersion());
            commitIdList.add(commitId);

            exeStatementWithoutTrans("MATCH (n:"+ NAME_SPACE_PREFIX +") SET n."+VERSION_LIST+" = {versionList}, n."+COMMITID_LIST+"={commitIdList}",
                    "versionList", StringUtils.join(versionList, ','),
                    "commitIdList", StringUtils.join(commitIdList, ','));
        }
    }

    private void createNode(RDFNode rdfNode){
        exeStatementWithoutTrans("MERGE (n:Resource{uri:'"+rdfNode.toString()+"'})");
    }

    private void createRelationship(Statement statement){
        String relationShip = getPrefixedName(statement.getPredicate().toString());
        exeStatementWithoutTrans("MATCH (n:Resource{uri:'"+statement.getSubject().toString()+"'}),(n1:Resource{uri:'"+statement.getObject().toString()+"'}) MERGE (n)-[:"+relationShip+"]->(n1)");
    }

    /**
     * 执行cypher操作neo4j库
     */
    public StatementResult exeStatement(String statement, String... parameter) {
        try {
            Transaction transaction = Neo4jConfig.getInstance().session().beginTransaction();
            StatementResult result = transaction.run(statement, parameters(parameter));
            transaction.success();
            transaction.close();
            return result;
        }catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException("execute cypher statement error: statement = " + statement);
        }
    }

    private int failTime=0;

    public StatementResult exeStatementWithoutTrans(String statement, String... parameter) {
        return exeStatementWithoutTrans(statement, parameters(parameter));
    }

    public StatementResult exeStatementWithoutTrans(String statement, Value value){
        try {
            StatementResult result = Neo4jConfig.getInstance().session().run(statement, value);
            failTime = 0;
            return result;
        }catch (ServiceUnavailableException|IllegalStateException e){
            if(failTime>10) {
                e.printStackTrace();
                throw e;
            }
            failTime++;
            //等待一会重试
            try {
                System.out.println("ServiceUnavailableException|IllegalStateException happen. wait "+failTime+" s.");
                Thread.currentThread().sleep(1000);
            }catch (InterruptedException ie){
                ie.printStackTrace();
                throw new RuntimeException("execute cypher statement error: "+ie.getMessage()+", statement = " + statement);
            }
            return exeStatementWithoutTrans(statement, value);
        }catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException("execute cypher statement error: statement = " + statement);
        }
    }

    /**
     * 在事务中执行语句
     */
    public StatementResult exeStatement(Transaction transaction, String statement, String... parameter) {
        try {
            StatementResult result = transaction.run(statement, parameters(parameter));
            return result;
        }catch (Exception e){
            e.printStackTrace();
            throw new RuntimeException("execute cypher statement error");
        }
    }

    public String getNextVersion(){
        if(nextVersion != null){
            return nextVersion;
        }
        //getCurVersion
        String curVer;
        if(CollectionUtils.isEmpty(versionList)){
            curVer = null;
        }else {
            curVer = versionList.get(versionList.size()-1);
        }

        String inputVer = CodeOntology.getInstance().getArguments().getVersion();
        if(StringUtils.isEmpty(curVer) && StringUtils.isEmpty(inputVer)){
            nextVersion = DEFAULT_VERSON;
        }else if(StringUtils.isEmpty(curVer) && !StringUtils.isEmpty(inputVer)){
            nextVersion = inputVer;
        }else {
            nextVersion = String.valueOf(Integer.valueOf(curVer)+1);
            if (!StringUtils.isEmpty(inputVer) && Double.valueOf(inputVer) > Integer.valueOf(curVer)) {
                nextVersion = inputVer;
            }
        }
        return nextVersion;
    }

    public static String getPropertyKey(Property property){
        return getPrefixedName(property.toString());
    }

    /**
     * 如果符合命名空间前缀，则用简写替换；否则返回原字符串
     */
    public static String getPrefixedName(String originalName){
        if(originalName.startsWith(BGOntology.BGOntology_)){
            return originalName.replace(BGOntology.BGOntology_, "");
        }
        return originalName;
    }

    /**
     * 前缀替换后的字符串恢复原字符串
     */
    public static String getOriginName(String prefixedName){
        prefixedName = BGOntology.BGOntology_ + prefixedName;
        return prefixedName;
    }

    public static String getNameVersion(String relativeURI, String version){
        return relativeURI + "#" + version;
    }

    public static Integer extractVersion(String uri){
        if(StringUtils.isEmpty(uri)){
            return null;
        }
        String[] splits = uri.split("#");
        if(splits.length == 0){
            return null;
        }
        return Integer.valueOf(splits[splits.length-1]);
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public void setNextVersion(String nextVersion) {
        this.nextVersion = nextVersion;
    }

    public String getLastCommitId(){
        if(CollectionUtils.isEmpty(commitIdList)){
            return null;
        }
        return commitIdList.get(commitIdList.size()-1);
    }

    public String getLastVersion(){
        if(CollectionUtils.isEmpty(versionList)){
            return null;
        }
        return versionList.get(versionList.size()-1);
    }

    public List<String> getVersionList() {
        return versionList;
    }

    public List<String> getCommitIdList() {
        return commitIdList;
    }
}
