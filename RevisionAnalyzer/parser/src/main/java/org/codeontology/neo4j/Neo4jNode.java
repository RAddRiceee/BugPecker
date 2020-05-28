package org.codeontology.neo4j;

import org.apache.commons.lang3.StringUtils;
import org.codeontology.BGOntology;
import org.neo4j.driver.internal.InternalNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.codeontology.neo4j.Neo4jQuery.prefixedName;

public class Neo4jNode {

    private List<String> labels;//标签
    private Map<String, String> propertys;//节点属性

    public Neo4jNode(InternalNode node) {
        this.labels = (List<String>) node.labels();
        this.propertys = new HashMap<String, String>();
        for(Map.Entry<String, Object> entry :node.asMap().entrySet()){
            this.propertys.put(entry.getKey(), entry.getValue().toString());
        }
    }

    public Neo4jNode(){}

    @Override
    public boolean equals(Object node){
        if(node == null){
            return false;
        }
        return this.uri().equals(((Neo4jNode)node).uri());
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public Map<String, String> getPropertys() {
        return propertys;
    }

    public void setPropertys(Map<String, String> propertys) {
        this.propertys = propertys;
    }

    public String uri(){
        return propertys.get("uri");
    }

    public Integer deletedVer(){
        String ver = propertys.get(prefixedName(BGOntology.DELETE_VERSION_PROPERTY));
        if(StringUtils.isEmpty(ver)){
            return null;
        }
        return Integer.valueOf(ver);
    }

    public void removePropertyExceptURI(){
        String uri = uri();
        propertys.clear();
        propertys.put("uri", uri);
    }
}
