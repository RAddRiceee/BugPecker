package org.codeontology.version;

import com.alibaba.fastjson.JSON;
import com.hp.hpl.jena.rdf.model.Resource;
import org.apache.commons.collections.CollectionUtils;
import org.codeontology.BGOntology;
import org.codeontology.neo4j.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.codeontology.neo4j.Neo4jQuery.prefixedName;

public class VersionDiffAccess {


    public static void main(String[] args){
        String projectID = "Hellow";
        try {
            VersionDiffInfo versionDiffInfo = queryVersionDiffInfo(projectID, "2c9ddae");
            System.out.println(JSON.toJSONString(versionDiffInfo));
        }catch (Exception e){
            e.printStackTrace();
        }
        Neo4jConfig.getInstance().stopNeo4jDriver();
    }

    /**
     * 获取指定版本和前一个版本的不同
     * @param projectID
     * @param commitId
     * @return
     */
    public static VersionDiffInfo queryVersionDiffInfo(String projectID, String commitId){
        Neo4jConfig.configDatabaseChoose(projectID);

        VersionDiffInfo versionDiffInfo = VersionDiffInfo.getEmptyVersionDiffInfo();

        //根据commitId找到对应version
        List<String> commitIdList = Neo4jLogger.getInstance().getCommitIdList();
        int index = commitIdList.indexOf(commitId);
        if(index<0){
            throw new RuntimeException("version "+commitId+" is not in neo4j database.");
        }else if(index == 0){
            throw new RuntimeException(commitId + " is init version which is illegal here.");
        }
        versionDiffInfo.setCommitId(commitId);
        versionDiffInfo.setLastCommitId(commitIdList.get(index-1));
        String ver = Neo4jLogger.getInstance().getVersionList().get(index);

        //获取和旧版本没有update关系的class，为添加的class
        String statement = String.format("MATCH (n:%s) where n.%s = '%s' " +
                        "and (not exists (n.%s) or n.%s <> 'true') " +
                        "and size(()-[:%s]->(n))=0 return n",
                prefixedName(BGOntology.CLASS_ENTITY), prefixedName(BGOntology.COMMIT_ID_PROPERTY), commitId,
                prefixedName(BGOntology.NOT_DECLARED_PROPERTY), prefixedName(BGOntology.NOT_DECLARED_PROPERTY),
                prefixedName(BGOntology.UPDATE_PROPERTY));
        List<Neo4jNode> addClassList = Neo4jQuery.cypherQuery(statement).get("n").stream().map(o -> (Neo4jNode)o).collect(Collectors.toList());
        if(!CollectionUtils.isEmpty(addClassList)) {
            versionDiffInfo.getAddInfo().put(VersionDiffInfo.Type.CLASS, VersionAccess.getVersionInfoOfClass(addClassList, ver, false));
        }

        //获取旧版本类拥有的，没有旧版本发出的update关系的method，即添加的method
        Map<Neo4jNode, List<Neo4jNode>> addedMethodMap = queryAddedMethodOrVariable(BGOntology.METHOD_ENTITY, commitId, ver);
        if(addedMethodMap.size() > 0) {
            VersionInfo addedMethodInfo = VersionInfo.getEmptyVersionInfo();
            addedMethodInfo.setClassList(new ArrayList<>(addedMethodMap.keySet()));
            List<Neo4jNode> addedMethodList = new ArrayList<>();
            for (Map.Entry<Neo4jNode, List<Neo4jNode>> entry : addedMethodMap.entrySet()) {
                addedMethodInfo.getMethodMap().put(entry.getKey().uri(), entry.getValue());
                addedMethodList.addAll(entry.getValue());
            }
            addedMethodInfo.setMethodSubGraphMap(VersionAccess.queryMethodSubGraph(addedMethodList, ver, false));
            versionDiffInfo.getAddInfo().put(VersionDiffInfo.Type.METHOD, addedMethodInfo);
        }

        //获取添加的变量
//        Map<Neo4jNode, List<Neo4jNode>> addedVariableMap = queryAddedMethodOrVariable(BGOntology.VARIABLE_ENTITY, commitId, ver);
//        if(addedVariableMap.size() > 0) {
//            VersionInfo addedVariableInfo = VersionInfo.getEmptyVersionInfo();
//            addedVariableInfo.setClassList(new ArrayList<>(addedVariableMap.keySet()));
//            for (Map.Entry<Neo4jNode, List<Neo4jNode>> entry : addedVariableMap.entrySet()) {
//                addedVariableInfo.getVariableMap().put(entry.getKey().uri(), entry.getValue());
//            }
//            versionDiffInfo.getAddInfo().put(VersionDiffInfo.Type.VARIABLE, addedVariableInfo);
//        }

        //找到和旧版本有update关系的class，为更新的class
        statement = String.format("MATCH (n1:%s)-[:%s]->(n:%s) where n.%s = '%s' " +
                        "and (not exists (n.%s) or n.%s <> 'true') " +
                        "and split(n1.uri, '#')[1] < '%s' return n1, n",
                prefixedName(BGOntology.CLASS_ENTITY), prefixedName(BGOntology.UPDATE_PROPERTY), prefixedName(BGOntology.CLASS_ENTITY), prefixedName(BGOntology.COMMIT_ID_PROPERTY), commitId,
                prefixedName(BGOntology.NOT_DECLARED_PROPERTY), prefixedName(BGOntology.NOT_DECLARED_PROPERTY), ver);
        Map<String, List<Object>> result = Neo4jQuery.cypherQuery(statement);
        List<Neo4jNode> updateClassList = result.get("n").stream().map(o -> (Neo4jNode)o).collect(Collectors.toList());
        List<Neo4jNode> beUpdatedClassList = result.get("n1").stream().map(o -> (Neo4jNode)o).collect(Collectors.toList());
        VersionInfo updateClassInfo = null;
        if(!CollectionUtils.isEmpty(updateClassList)){
            updateClassInfo = VersionAccess.getVersionInfoOfClass(updateClassList, ver, false);
            Map<String, VersionInfo> updateClassMap = splitUpdateClassInfo(updateClassInfo, beUpdatedClassList);
            versionDiffInfo.setUpdateClassMap(updateClassMap);
        }

        //找到旧版本拥有的，有旧版本发出的update关系的method，为更新的method
        List<String> excludeMethodUriList = new ArrayList<>();
        if(updateClassInfo!=null && updateClassInfo.getMethodMap().size()>0){
            List<Neo4jNode> excludeMethodList = new ArrayList<>();
            for(Map.Entry<String, List<Neo4jNode>> entry : updateClassInfo.getMethodMap().entrySet()) {
                if(entry.getValue()!=null) {
                    excludeMethodUriList.addAll(entry.getValue().stream().map(o->o.uri()).collect(Collectors.toList()));
                }
            }
        }
        Map<String, Neo4jNode> updateMethodMap = queryUpdateMethod(commitId, ver, excludeMethodUriList);
        if(updateMethodMap.size()>0){
            for(Map.Entry<String, Neo4jNode> entry : updateMethodMap.entrySet()){
                VersionInfo updateMethodInfo = VersionInfo.getEmptyVersionInfo();
                List<Neo4jNode> methodList = new ArrayList<>(Arrays.asList(entry.getValue()));
                updateMethodInfo.getMethodMap().put("key", methodList);
                updateMethodInfo.setMethodSubGraphMap(VersionAccess.queryMethodSubGraph(methodList, ver, false));
                versionDiffInfo.getUpdateMethodMap().put(entry.getKey(), updateMethodInfo);
            }
        }

        // 获取deleteVer在范围内的，再判断是类，方法还是变量
        statement = String.format("MATCH (n) where n.%s = '%s' return n", prefixedName(BGOntology.DELETE_VERSION_PROPERTY), ver);
        List<Neo4jNode> deleteNodeList = Neo4jQuery.cypherQuery(statement).get("n").stream().map(o -> (Neo4jNode)o).collect(Collectors.toList());
        for(Neo4jNode node : deleteNodeList){
            VersionDiffInfo.Type type;
            if(node.getLabels().contains(prefixedName(BGOntology.CLASS_ENTITY))){
                type = VersionDiffInfo.Type.CLASS;
            }else if(node.getLabels().contains(prefixedName(BGOntology.METHOD_ENTITY))){
                type = VersionDiffInfo.Type.METHOD;
            }else{
                type = VersionDiffInfo.Type.VARIABLE;
                continue;//去掉变量
            }
            versionDiffInfo.getDeleteMap().get(type).add(node.uri());
        }

        return versionDiffInfo;

    }

    /**
     * 获取旧版本类拥有的，没有旧版本发出的update关系的method或variable，即添加的method或variable
     */
    private static Map<Neo4jNode, List<Neo4jNode>> queryAddedMethodOrVariable(Resource property, String commitId, String version){
        String statement = String.format("MATCH p=(c:%s)-[:%s]->(n:%s) " +
                        "where n.%s = '%s' " +
                        "and size(()-[:%s]->(n))=0 and split(c.uri, '#')[1] < '%s' return p",
                prefixedName(BGOntology.CLASS_ENTITY), prefixedName(BGOntology.HAS_PROPERTY), prefixedName(property),
                prefixedName(BGOntology.COMMIT_ID_PROPERTY), commitId,
                prefixedName(BGOntology.UPDATE_PROPERTY), version);
        List<Object> objects = Neo4jQuery.cypherQuery(statement).get("p");
        List<Neo4jRelation> methodOrVarPathList = objects.stream().map(o -> (Neo4jRelation)o).collect(Collectors.toList());
        Map<String, List<Neo4jNode>> calledmap = methodOrVarPathList.stream().collect(Collectors.groupingBy(r->r.getNodes().get(0).uri(), Collectors.mapping(r -> r.getNodes().get(1), Collectors.toList())));
        Map<String, List<Neo4jNode>> callmap = methodOrVarPathList.stream().collect(Collectors.groupingBy(r->r.getNodes().get(0).uri(), Collectors.mapping(r -> r.getNodes().get(0), Collectors.toList())));

        Map<Neo4jNode, List<Neo4jNode>> map = new HashMap<>();
        callmap.forEach((k,v)->{
            map.put(callmap.get(k).get(0), calledmap.get(k));
        });
        return map;
    }

    /**
     * 找到旧版本拥有的，有旧版本发出的update关系的method，为更新的method, key为旧方法uri
     */
    private static Map<String, Neo4jNode> queryUpdateMethod(String commitId, String version, List<String> excludeMethodUriList){
        String statement = String.format("MATCH (n1:%s)-[:%s]->(n:%s) where n.%s = '%s' " +
                        "and split(n1.uri, '#')[1] < '%s' and not n.uri in %s return n1, n",
                prefixedName(BGOntology.METHOD_ENTITY), prefixedName(BGOntology.UPDATE_PROPERTY), prefixedName(BGOntology.METHOD_ENTITY),
                prefixedName(BGOntology.COMMIT_ID_PROPERTY), commitId, version, JSON.toJSONString(excludeMethodUriList));
        Map<String, List<Object>> result = Neo4jQuery.cypherQuery(statement);
        List<Neo4jNode> updateMethodList = result.get("n").stream().map(o -> (Neo4jNode)o).collect(Collectors.toList());
        List<Neo4jNode> beUpdatedMethodList = result.get("n1").stream().map(o -> (Neo4jNode)o).collect(Collectors.toList());
        Map<String, Neo4jNode> map = new HashMap<>();
        for(int i=0;i<updateMethodList.size();i++){
            map.put(beUpdatedMethodList.get(i).uri(), updateMethodList.get(i));
        }
        return map;
    }

    public static Map<String, VersionInfo> splitUpdateClassInfo(VersionInfo updateClassInfo, List<Neo4jNode> beUpdatedClassList){
        Map<String, VersionInfo> updateClassMap = new HashMap<>();
        for(int i=0;i<updateClassInfo.getClassList().size();i++){
            Neo4jNode updateClass = updateClassInfo.getClassList().get(i);
            String uri =updateClass.uri();
            VersionInfo singleClassInfo = VersionInfo.getEmptyVersionInfo();
            singleClassInfo.getClassList().add(updateClass);
            if(!CollectionUtils.isEmpty(updateClassInfo.getVariableMap().get(uri))){
                singleClassInfo.getVariableMap().put(uri, updateClassInfo.getVariableMap().get(uri));
            }
            if(!CollectionUtils.isEmpty(updateClassInfo.getMethodMap().get(uri))) {
                singleClassInfo.getMethodMap().put(uri, updateClassInfo.getMethodMap().get(uri));
                for (Neo4jNode methodNode : updateClassInfo.getMethodMap().get(uri)) {
                    singleClassInfo.getMethodSubGraphMap().put(methodNode.uri(), updateClassInfo.getMethodSubGraphMap().get(methodNode.uri()));
                }
            }
            updateClassMap.put(beUpdatedClassList.get(i).uri(), singleClassInfo);
        }
        return updateClassMap;
    }

}
