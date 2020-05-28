package org.codeontology.neo4j;

import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codeontology.BGOntology;
import org.codeontology.extraction.bgontology.MethodEntity;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalPath;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.Value;

import java.util.*;
import java.util.stream.Collectors;

/**
 * neo4j查询相关的静态工具类
 */
public class Neo4jQuery {

    /**
     * 获取代码大数据元数据
     */
    public static CodeMetaData getCodeMetaData(String projectID){
        CodeMetaData codeMetaData = new CodeMetaData();
        //获取方法总数
        String statement = "Match (n:Method) return count(n) as methodNum";
        String methodNum = String.valueOf(cypherQuery(statement).get("methodNum").get(0));
        codeMetaData.setMethodNum(methodNum);
        //获取方法间调用关系数
        statement = "Match (:Method)-[r:call]->(:Method) return count(r) as callRelNum";
        String callRelNum = String.valueOf(cypherQuery(statement).get("callRelNum").get(0));
        codeMetaData.setCallRelNUm(callRelNum);
        //获取版本数量
        String versionNum = String.valueOf(Neo4jLogger.getInstance().getCommitIdList().size());
        codeMetaData.setVersionNum(versionNum);
        //获取相似数量
        AddSimRank addSimRank = new AddSimRank(projectID, null);
        String simRelNum = String.valueOf(addSimRank.getSimRelNum());
        codeMetaData.setSimRelNum(simRelNum);
        return codeMetaData;
    }

    /**
     * 获取不大于某个版本的最新实体
     */
    public static Neo4jNode getLatestVersionEntity(String version, RDFNode entityType, String relativeURI){
        //首先获取所有版本的实体
        String statement = String.format("MATCH (n:%s) where n.uri contains '%s' return n ORDER BY toInteger(split(n.uri, '#')[1])",
                prefixedName(entityType), relativeURI, prefixedName(BGOntology.UPDATE_PROPERTY));
        List<Object> nodes = cypherQuery(statement).get("n");
        if(CollectionUtils.isEmpty(nodes)){
            return null;
        }
        for(int i=nodes.size()-1;i>=0;i--){
            Neo4jNode node = (Neo4jNode)nodes.get(i);
            Integer ver = Neo4jLogger.extractVersion(node.uri());
            if(ver.compareTo(Integer.valueOf(version))<=0){
                return node;
            }
        }
        return (Neo4jNode)nodes.get(nodes.size()-1);
    }

    /**
     * 根据shot methodSignature获取实体uri
     */
    public static Neo4jNode getMethodByShotSignature(String shotSignature, String className){
        String regex = MethodEntity.buildShotMethodSigRegex(shotSignature, className);
        String paramStr = shotSignature.substring(shotSignature.indexOf('(')+1, shotSignature.indexOf(')'));
        int paramNum = paramStr.split(",").length;
        if(StringUtils.isEmpty(paramStr)){
            paramNum = 0;
        }
        String statement = String.format("match (n:%s) where n.uri =~ '%s' " +
                        "and size((n)-[:%s]->(:%s))=%d " +
                        "and not exists (n.%s) " +
                        "return n ORDER BY toInteger(split(n.uri, '#')[1]) DESC",
                prefixedName(BGOntology.METHOD_ENTITY), regex,
                prefixedName(BGOntology.HAS_PROPERTY), prefixedName(BGOntology.PARAMETER_ENTITY), paramNum,
                prefixedName(BGOntology.DELETE_VERSION_PROPERTY));

        List<Object> nodes = cypherQuery(statement).get("n");
        if(CollectionUtils.isEmpty(nodes)){
            return null;
        }
        return (Neo4jNode)nodes.get(0);

        //是最新版本且未删除的
//        Neo4jNode newNode = null;
//        Integer newVer = null;
//        for(int i = 0;i<neo4jNodes.size(); i++){
//            Neo4jNode node = neo4jNodes.get(i);
//            Integer ver = Neo4jLogger.extractVersion(node.uri());
//            if(newNode == null && node.deletedVer()==null){
//                newNode = node;
//                newVer = ver;
//            }
//            if(newVer!=null && ver.compareTo(newVer) > 0 && node.deletedVer()==null) {
//                newNode = node;
//                newVer = ver;
//            }
//        }
//        return newNode;
    }

    /**
     * 获取最新的实体
     */
    public static Neo4jNode getLastVersionEntity(String relativeURI, RDFNode entityType){

        if(StringUtils.isEmpty(relativeURI) || entityType == null){
            return null;
        }
        String statement = String.format("match (n:%s) where n.uri contains '%s#' " +
                        "and size((n)-[:%s]->())=0 return n",
                prefixedName(entityType), relativeURI, prefixedName(BGOntology.UPDATE_PROPERTY));
        List<Object> result = cypherQuery(statement).get("n");
        if(CollectionUtils.isEmpty(result)){
            return null;
        }
        return  (Neo4jNode)result.get(0);
    }

    public static String prefixedName(RDFNode resource){
        return Neo4jLogger.getPrefixedName(((Resource)resource).toString());
    }

    /**
     * 根据uri精确查询一个node节点
     * @param uri
     * @return
     */
    public static Neo4jNode getNeo4jNode(String uri){
        String statement = String.format("match (n{uri:'%s'}) return n", uri);
        List<Object> result = cypherQuery(statement).get("n");
        if(CollectionUtils.isEmpty(result)){
            return null;
        }
        return (Neo4jNode)result.get(0);
    }

    /**
     * cypher语言查询结果
     * @param statement
     * @return
     */
    public static Map<String, List<Object>> cypherQuery(String statement){
        Neo4jLogger neo4jLogger = Neo4jLogger.getInstance();
        if (statement.toLowerCase().contains("SET") || statement.toLowerCase().contains("DELETE") || statement.toLowerCase().contains("CREATE")) {
            throw new RuntimeException("only read, cannot write");
        }
        try {
            StatementResult result = neo4jLogger.exeStatementWithoutTrans(statement);
            return transResultToMap(result);
        }catch (IllegalStateException e){
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public static Map<String, List<Object>> cypherQuery(String statement, Value value){
        Neo4jLogger neo4jLogger = Neo4jLogger.getInstance();
        if (statement.toLowerCase().contains("SET") || statement.toLowerCase().contains("DELETE") || statement.toLowerCase().contains("CREATE")) {
            throw new RuntimeException("only read, cannot write");
        }
        try {
            StatementResult result = neo4jLogger.exeStatementWithoutTrans(statement, value);
            return transResultToMap(result);
        }catch (IllegalStateException e){
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public static Map<String, List<Object>> cypherQuery(Transaction transaction, String statement){
        Neo4jLogger neo4jLogger = Neo4jLogger.getInstance();
        if (statement.toLowerCase().contains("SET") || statement.toLowerCase().contains("DELETE") || statement.toLowerCase().contains("CREATE")) {
            throw new RuntimeException("only read, cannot write");
        }
        StatementResult result = neo4jLogger.exeStatement(transaction, statement);
        return transResultToMap(result);
    }

    private static Map<String, List<Object>> transResultToMap(StatementResult result){
        Map<String, List<Object>> printMap = new HashMap<String, List<Object>>();
        for (String key : result.keys()) {
            printMap.put(key, new ArrayList<Object>());
        }
        while (result.hasNext()) {
            Map<String, Object> resultMap = result.next().asMap();
            for (String key : resultMap.keySet()) {
                if (resultMap.get(key) instanceof InternalNode) {//处理节点
                    InternalNode node = (InternalNode) resultMap.get(key);
                    printMap.get(key).add(new Neo4jNode(node));
                } else if (resultMap.get(key) instanceof InternalPath){//处理边
                    InternalPath path = (InternalPath) resultMap.get(key);
                    printMap.get(key).add(new Neo4jRelation(path));
                }else if( resultMap.get(key) == null) {
                    printMap.get(key).add(null);
                }else {
                    printMap.get(key).add(resultMap.get(key));
                }
            }
        }
        return printMap;
    }

    /**
     * 获取一个实体发出的所有边
     */
    public static List<Neo4jRelation> getEntityEmitRelations(String uri, RDFNode entityType, List<String> relationTypes){
        String statement = String.format("MATCH r = (n:%s)-[]->() where n.uri = '%s' return r",
                prefixedName(entityType), uri);
        List<Object> objects =  cypherQuery(statement).get("r");
        List<Neo4jRelation> neo4jRelations = new ArrayList<>();
        if(CollectionUtils.isEmpty(objects)){
            return neo4jRelations;
        }
        for(Object object: objects){
            Neo4jRelation relation = (Neo4jRelation)object;
            if(!CollectionUtils.isEmpty(relationTypes) && relationTypes.contains(relation.getEdges().get(0))){
                neo4jRelations.add(relation);
            }
        }
        return neo4jRelations;
    }
}
