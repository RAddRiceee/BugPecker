package org.codeontology.version;

import com.alibaba.fastjson.JSON;
import com.hp.hpl.jena.rdf.model.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.codeontology.BGOntology;
import org.codeontology.neo4j.*;
import org.neo4j.driver.v1.Transaction;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.codeontology.neo4j.Neo4jQuery.prefixedName;

/**
 * 获取某个版本的知识图谱
 */
public class VersionAccess {

    public static void main(String[] args){
        String projectPath = "/Users/hsz/projects/KGTest";
        String projectID = "KGTest1";
        try {
            VersionInfo versionInfo = querySpecificVersionInfo(projectPath, projectID, "kgtesta",false);
            System.out.println(JSON.toJSONString(versionInfo));
        }catch (Exception e){
            e.printStackTrace();
        }
        Neo4jConfig.getInstance().stopNeo4jDriver();
    }

    public static VersionInfo querySpecificVersionInfo(String projectPath, String projectID, String commitId, boolean detail){
        Neo4jConfig.configDatabaseChoose(projectID);
        System.out.println("querying versionInfo...");
        //根据commitId找到对应version
        int index = indexOfCommitIdList(Neo4jLogger.getInstance().getCommitIdList(),commitId);
        if(index < 0){
            //从git log寻找该commitId最近的最新commitId
            throw new RuntimeException("this commit is not included in neo4j.");
//            String latestCommitId = GitUtil.getLatestCommitId(projectPath, Neo4jLogger.getInstance().getCommitIdList(), commitId);
//            if(StringUtils.isEmpty(latestCommitId)){
//                throw new RuntimeException("this commit is not exist.");
//            }
//            index = Neo4jLogger.getInstance().getCommitIdList().indexOf(latestCommitId);
        }
        String version = Neo4jLogger.getInstance().getVersionList().get(index);

        //获取最新未删除的自声明的class
        List<Neo4jNode> classList = queryClasses(version);

        VersionInfo versionInfo = getVersionInfoOfClass(classList, version, detail);
        System.out.println("querying versionInfo finish.");
        return versionInfo;
    }

    /**
     * 获取class列表中的class的某个版本的VersionInfo
     * @param classList
     * @param version
     * @return
     */
    public static VersionInfo getVersionInfoOfClass(List<Neo4jNode> classList, String version, boolean detail){

        //获取这些类包含的最新未删除的method和variable
//        Map<String, List<Neo4jNode>> variableMap = queryMethodOrVariableOfClass(classList, BGOntology.VARIABLE_ENTITY, version);
        Map<String, List<Neo4jNode>> variableMap = new HashMap<>();
        Map<String, List<Neo4jNode>> methodMap = queryMethodOrVariableOfClass(classList, BGOntology.METHOD_ENTITY, version);

        //获取method的子图
        Set<Neo4jNode> methodSet = new HashSet<>();
        methodMap.forEach((k,v) -> methodSet.addAll(v));
        List<Neo4jNode> methodList = new ArrayList<>(methodSet);

        Map<String, List<Neo4jRelation>> methodSubGraphMap = queryMethodSubGraph(methodList, version, detail);

        return new VersionInfo(classList, variableMap, methodMap, methodSubGraphMap);
    }

    /**
     * 获取最新未删除的自声明的class
     */
    private static List<Neo4jNode> queryClasses(String version){
        //找到所有update链路径
        String statement = String.format("MATCH p=(n:%s)-[:%s*]->(n1:%s) " +
                        "where (not exists (n.%s) or n.%s <> 'true') " +
                        "and size(()-[:%s]->(n))=0 and size((n1)-[:%s]->())=0 return distinct p",
                prefixedName(BGOntology.CLASS_ENTITY), prefixedName(BGOntology.UPDATE_PROPERTY), prefixedName(BGOntology.CLASS_ENTITY),
                prefixedName(BGOntology.NOT_DECLARED_PROPERTY), prefixedName(BGOntology.NOT_DECLARED_PROPERTY),
                prefixedName(BGOntology.UPDATE_PROPERTY), prefixedName(BGOntology.UPDATE_PROPERTY));
        List<Object> objects = Neo4jQuery.cypherQuery(statement).get("p");
        List<Neo4jRelation> updatePathList = objects.stream().map(o -> (Neo4jRelation)o).collect(Collectors.toList());

        //过滤掉删除的
        updatePathList = updatePathList.stream().filter(new Predicate<Neo4jRelation>() {
            @Override
            public boolean test(Neo4jRelation updatePath) {
                Neo4jNode lastNode = updatePath.getNodes().get(updatePath.getNodes().size()-1);
                Integer deletedVer = lastNode.deletedVer();
                return deletedVer!=null && deletedVer.compareTo(Integer.valueOf(version))<=0? false : true;
            }
        }).collect(Collectors.toList());

        //对每个update链，寻找不大于version的最新实体
        List<Neo4jNode> classList = new ArrayList<>();
        for (Neo4jRelation updatePath : updatePathList){
            int size = updatePath.getNodes().size();
            for(int i = size-1;i>=0;i--){
                Integer ver =Neo4jLogger.extractVersion(updatePath.getNodes().get(i).uri());
                if(Integer.valueOf(version).compareTo(ver)>=0){
                    classList.add(updatePath.getNodes().get(i));
                    break;
                }
            }
        }

        //加上还没形成update链的未删除class
        statement = String.format("MATCH (n:%s) " +
                        "where (not exists (n.%s) or n.%s <> 'true') " +
                        "and (not exists (n.%s) or toInteger(n.%s) > %s) " +
                        "and size(()-[:%s]->(n))=0 and size((n)-[:%s]->())=0 return n",
                prefixedName(BGOntology.CLASS_ENTITY),
                prefixedName(BGOntology.NOT_DECLARED_PROPERTY), prefixedName(BGOntology.NOT_DECLARED_PROPERTY),
                prefixedName(BGOntology.DELETE_VERSION_PROPERTY), prefixedName(BGOntology.DELETE_VERSION_PROPERTY), version,
                prefixedName(BGOntology.UPDATE_PROPERTY), prefixedName(BGOntology.UPDATE_PROPERTY));
        objects = Neo4jQuery.cypherQuery(statement).get("n");
        List<Neo4jNode> singleVerClassList = objects.stream().map(o -> (Neo4jNode)o).collect(Collectors.toList());
        classList.addAll(singleVerClassList);

        return classList;
    }

    /**
     * 获取这些类包含的最新未删除的method或variable
     */
    private static Map<String, List<Neo4jNode>> queryMethodOrVariableOfClass(List<Neo4jNode> classList, Resource property, String version){
        //根据has关系获取类所拥有的method或var
        List<String> classURIList = classList.stream().map(neo4jNode -> neo4jNode.uri()).collect(Collectors.toList());
        String statement = String.format("MATCH p = (n:%s)-[:%s]->(v:%s) " +
                        "where n.uri in %s and toInteger(split(v.uri, '#')[1]) <= %s " +
                        "and (not exists (v.%s) or toInteger(v.%s) > %s) return distinct p",
                prefixedName(BGOntology.CLASS_ENTITY), prefixedName(BGOntology.HAS_PROPERTY), prefixedName(property),
                JSON.toJSONString(classURIList), version,
                prefixedName(BGOntology.DELETE_VERSION_PROPERTY), prefixedName(BGOntology.DELETE_VERSION_PROPERTY), version);
        List<Object> objects = Neo4jQuery.cypherQuery(statement).get("p");
        List<Neo4jRelation> methodOrVarPathList = objects.stream().map(o -> (Neo4jRelation)o).collect(Collectors.toList());
        Map<String, List<Neo4jNode>> map = methodOrVarPathList.stream().collect(Collectors.groupingBy(r->r.getNodes().get(0).uri(), Collectors.mapping(r -> r.getNodes().get(1), Collectors.toList())));

        return replaceNodeWithNewVer(map, version);
    }

    /**
     * 用update链上不大于version的未删除最新实体替换旧实体
     */
    public static Map<String, List<Neo4jNode>> replaceNodeWithNewVer(Map<String, List<Neo4jNode>> map, String version){
        Set<String> methodOrVarURISet = new HashSet<>();
        map.values().forEach(l -> l.forEach(i -> methodOrVarURISet.add(i.uri())));

        String statement = String.format("MATCH p = (n)-[:%s*]->(n1) where n.uri in %s " +
                        "and size((n1)-[:%s]->())=0 return distinct p",
                prefixedName(BGOntology.UPDATE_PROPERTY), JSON.toJSONString(methodOrVarURISet), prefixedName(BGOntology.UPDATE_PROPERTY));
        List<Object> objects = Neo4jQuery.cypherQuery(statement).get("p");
        List<Neo4jRelation> updatePathList = objects.stream().map(o -> (Neo4jRelation)o).collect(Collectors.toList());
        for (Neo4jRelation updatePath : updatePathList){
            int size = updatePath.getNodes().size();
            for(int i = size-1;i>=0;i--){
                Neo4jNode node = updatePath.getNodes().get(i);
                Integer ver =Neo4jLogger.extractVersion(node.uri());
                Integer deletedVer = node.deletedVer();
                if(Integer.valueOf(version).compareTo(ver)>=0 && !(deletedVer!=null && deletedVer.compareTo(Integer.valueOf(version))<=0)){
                    //在map中替换
                    for(Map.Entry<String, List<Neo4jNode>> entry: map.entrySet()){
                        for(Neo4jNode n : entry.getValue()){
                            if(n.uri().equals(updatePath.getNodes().get(0).uri())){
                                int index = entry.getValue().indexOf(n);
                                entry.getValue().remove(index);
                                entry.getValue().add(index, node);
                                return map;
                            }
                        }
                    }
                    break;
                }
            }
        }
        return map;
    }

    /**
     * 获取method的子图，key为method的uri，value为该method出发的所有路径
     */
    public static Map<String, List<Neo4jRelation>> queryMethodSubGraph(List<Neo4jNode> methodList, String version, boolean detail){
        Map<String, List<Neo4jRelation>> methodSubGraphMap = new HashMap<>();
        if(CollectionUtils.isEmpty(methodList)){
            return methodSubGraphMap;
        }
        List<String> methodURIList = methodList.stream().map(m->m.uri()).collect(Collectors.toList());

        String statement1 = String.format("MATCH p1 = (n:%s)-[:%s]->(n1) where n.uri in %s and (not exists (n1.%s) or toInteger(n1.%s) > %s) return distinct p1",
                prefixedName(BGOntology.METHOD_ENTITY), prefixedName(BGOntology.CALL_PROPERTY), JSON.toJSONString(methodURIList),
                prefixedName(BGOntology.DELETE_VERSION_PROPERTY), prefixedName(BGOntology.DELETE_VERSION_PROPERTY), version);
        Map<String, List<Object>> result1 = Neo4jQuery.cypherQuery(statement1);
        List<Neo4jRelation> callPathList = result1.get("p1").stream().map(o->(Neo4jRelation)o).collect(Collectors.toList());

        //调用的method替换为最新实体
        Map<String, List<Neo4jRelation>> callPathMap = callPathList.stream().collect(Collectors.groupingBy(r->r.getNodes().get(0).uri(), Collectors.toList()));
        Map<String, List<Neo4jNode>> calledMethodMap = new HashMap<>();
        for(Map.Entry<String, List<Neo4jRelation>> entry : callPathMap.entrySet()){
            calledMethodMap.put(entry.getKey(), entry.getValue().stream().map(r->r.getNodes().get(1)).collect(Collectors.toList()));
        }
        calledMethodMap = replaceNodeWithNewVer(calledMethodMap, version);
        for(Map.Entry<String, List<Neo4jRelation>> entry : callPathMap.entrySet()){
            for(int i=0;i<entry.getValue().size();i++){
                entry.getValue().get(i).getNodes().remove(1);
                entry.getValue().get(i).getNodes().add(1, calledMethodMap.get(entry.getKey()).get(i));
            }
        }

        if(detail){
            String statement2 = String.format("MATCH p2 = (n:%s)-[:%s]-(:%s)-[:%s]-() where n.uri in %s return distinct p2",
                    prefixedName(BGOntology.METHOD_ENTITY), prefixedName(BGOntology.HAS_PROPERTY), prefixedName(BGOntology.PARAMETER_ENTITY),
                    prefixedName(BGOntology.INSTANCE_OF_PROPERTY), JSON.toJSONString(methodURIList));
            Map<String, List<Object>> result2 = Neo4jQuery.cypherQuery(statement2);
            List<Neo4jRelation> parameterPathList = result2.get("p2").stream().map(o->(Neo4jRelation)o).collect(Collectors.toList());
            Map<String, List<Neo4jRelation>> parameterPathMap = parameterPathList.stream().collect(Collectors.groupingBy(r->r.getNodes().get(0).uri(), Collectors.toList()));

            String statement3 = String.format("MATCH p3 = (n:%s)-[:%s]-() where n.uri in %s return distinct p3",
                    prefixedName(BGOntology.METHOD_ENTITY), prefixedName(BGOntology.RETURN_TYPE_PROPERTY), JSON.toJSONString(methodURIList));
            Map<String, List<Object>> result3 = Neo4jQuery.cypherQuery(statement3);
            List<Neo4jRelation> returnTypePathList = result3.get("p3").stream().map(o->(Neo4jRelation)o).collect(Collectors.toList());
            Map<String, List<Neo4jRelation>> returnTypePathMap = returnTypePathList.stream().collect(Collectors.groupingBy(r->r.getNodes().get(0).uri(), Collectors.toList()));

            //合并各种路径
            for(String uri : methodURIList){
                List<Neo4jRelation> list = new ArrayList<>();
                if(!CollectionUtils.isEmpty(parameterPathMap.get(uri))){
                    list.addAll(parameterPathMap.get(uri));
                }
                if(!CollectionUtils.isEmpty(returnTypePathMap.get(uri))){
                    list.addAll(returnTypePathMap.get(uri));
                }
                if(!CollectionUtils.isEmpty(callPathMap.get(uri))){
                    list.addAll(callPathMap.get(uri));
                }
                methodSubGraphMap.put(uri, list);
            }
        }else {
            methodSubGraphMap = callPathMap;
        }

        //该method删除除了uri外的属性
        for(Map.Entry<String, List<Neo4jRelation>> entry : methodSubGraphMap.entrySet()){
            entry.getValue().stream().forEach(r->r.getNodes().get(0).removePropertyExceptURI());
        }

        return methodSubGraphMap;
    }

    public static int indexOfCommitIdList(List<String> commitIDList, String commitID){
        if(commitID.length()>7){
            return commitIDList.indexOf(commitID);
        }
        for(int i=0;i<commitIDList.size();i++){
            if(commitIDList.get(i).substring(0,7).equals(commitID.substring(0,7))){
                return i;
            }
        }
        return -1;
    }
}
