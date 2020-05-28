package org.codeontology.version;

import org.apache.commons.collections.CollectionUtils;
import org.codeontology.neo4j.Neo4jNode;
import org.codeontology.neo4j.Neo4jRelation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VersionInfo {

    List<Neo4jNode> classList;//该版本的最新未删除的自声明的class
    Map<String, List<Neo4jNode>> variableMap;//类包含的最新未删除的variable,key为类的uri
    Map<String, List<Neo4jNode>> methodMap;//类包含的最新未删除的method,key为类的uri
    /**
     * method的子图，key为method的uri，value为该method出发的所有路径
     * 路径包括3种：
     * Method -call-> Method/API
     * Method -has-> Parameter -instanceof-> Class
     * Method -returnType-> Class
     */
    Map<String, List<Neo4jRelation>> methodSubGraphMap;

    public VersionInfo(){
    }

    public VersionInfo(List<Neo4jNode> classList, Map<String, List<Neo4jNode>> variableMap, Map<String, List<Neo4jNode>> methodMap, Map<String, List<Neo4jRelation>> methodSubGraphMap) {
        if(CollectionUtils.isEmpty(classList)){
            this.classList = new ArrayList<>();
        }else {
            this.classList = classList;
        }
        if(variableMap!=null && variableMap.size()>0){
            this.variableMap = variableMap;
        }else {
            this.variableMap = new HashMap<>();
        }
        if(methodMap!=null && methodMap.size()>0){
            this.methodMap = methodMap;
        }else {
            this.methodMap = new HashMap<>();
        }
        if(methodSubGraphMap!=null && methodSubGraphMap.size()>0){
            this.methodSubGraphMap = methodSubGraphMap;
        }else {
            this.methodSubGraphMap = new HashMap<>();
        }
    }

    public static VersionInfo getEmptyVersionInfo(){
        VersionInfo versionInfo = new VersionInfo();
        versionInfo.setClassList(new ArrayList<>());
        versionInfo.setMethodMap(new HashMap<>());
        versionInfo.setVariableMap(new HashMap<>());
        versionInfo.setMethodSubGraphMap(new HashMap<>());
        return versionInfo;
    }

    public List<Neo4jNode> getClassList() {
        return classList;
    }

    public void setClassList(List<Neo4jNode> classList) {
        this.classList = classList;
    }

    public Map<String, List<Neo4jNode>> getVariableMap() {
        return variableMap;
    }

    public void setVariableMap(Map<String, List<Neo4jNode>> variableMap) {
        this.variableMap = variableMap;
    }

    public Map<String, List<Neo4jNode>> getMethodMap() {
        return methodMap;
    }

    public void setMethodMap(Map<String, List<Neo4jNode>> methodMap) {
        this.methodMap = methodMap;
    }

    public Map<String, List<Neo4jRelation>> getMethodSubGraphMap() {
        return methodSubGraphMap;
    }

    public void setMethodSubGraphMap(Map<String, List<Neo4jRelation>> methodSubGraphMap) {
        this.methodSubGraphMap = methodSubGraphMap;
    }
}
