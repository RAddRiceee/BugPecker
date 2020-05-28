package org.codeontology.neo4j;

import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalPath;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Neo4jRelation {

    private List<String> edges;//边的名称
    private List<Neo4jNode> nodes;//路径经过的点，从起点到终点

    public Neo4jRelation(InternalPath path) {
        edges = new ArrayList<String>();
        for(Relationship relationship : path.relationships()){
            edges.add(relationship.type());
        }
        nodes = new ArrayList<Neo4jNode>();
        Iterator<Node> nodeIterator = path.nodes().iterator();
        while (nodeIterator.hasNext()){
            nodes.add(new Neo4jNode((InternalNode)nodeIterator.next()));
        }
    }

    public Neo4jRelation(){}

    public List<String> getEdges() {
        return edges;
    }

    public void setEdges(List<String> types) {
        this.edges = types;
    }

    public List<Neo4jNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<Neo4jNode> nodes) {
        this.nodes = nodes;
    }
}
