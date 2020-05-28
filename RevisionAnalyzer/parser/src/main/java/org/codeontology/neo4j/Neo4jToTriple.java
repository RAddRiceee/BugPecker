package org.codeontology.neo4j;

import org.codeontology.BGOntology;
import org.codeontology.util.FileUtil;
import org.codeontology.version.VersionAccess;
import org.codeontology.version.VersionInfo;

import java.util.*;
import java.util.stream.Collectors;

import static org.codeontology.neo4j.Neo4jQuery.prefixedName;

/**
 * 把某个版本数据转化成triple.txt，每行是一个三元组entity1，relation，entity2
 * 三元组不考虑属性
 */
public class Neo4jToTriple {

    private static final String label_relation = "label";
    private static final int max_count = 10000;

    public static void transformNeo4jToTriple(String filePath, String projectID){
        Neo4jConfig.configDatabaseChoose(projectID);
        System.out.println("creating full triple.txt...");
        List<String> nodeUris = new ArrayList<>();
        List<String> tripleList = new ArrayList<>();

        List<String> commitIDList = Neo4jLogger.getInstance().getCommitIdList();
        int index=0;
        for(String commitID : commitIDList){
            System.out.println("index: "+index+"/"+commitIDList.size()+" commitID= "+commitID);
            List<Object> result = Neo4jQuery.cypherQuery("MATCH p=(n:Method)-[r]->(:Method) where n.commitID='"+commitID+"' RETURN p").get("p");
            List<Neo4jRelation> relations = result.stream().map(o -> (Neo4jRelation)o).collect(Collectors.toList());
            for(Neo4jRelation relation : relations){
                // 标签关系
//                for(Neo4jNode node: relation.getNodes()){
//                    if(!nodeUris.contains(node.uri())){
//                        tripleList.addAll(handleLabels(node));
//                        nodeUris.add(node.uri());
//                    }
//                }

                for(int i=0;i<relation.getEdges().size();i++){
                    String edge = relation.getEdges().get(i);
                    tripleList.add(formTriple(relation.getNodes().get(i).uri(), edge, relation.getNodes().get(i+1).uri()));
                }

                if(tripleList.size()>max_count){
                    FileUtil.appendListToFile(tripleList, filePath);
                    tripleList = new ArrayList<>();
                }
            }
            index++;
        }
        FileUtil.appendListToFile(tripleList, filePath);
    }

    public static void transformNeo4jToTriple(String projectPath, String projectID, String commitId, VersionInfo versionInfo, String filePath){
        if(versionInfo == null){
            versionInfo = VersionAccess.querySpecificVersionInfo(projectPath, projectID, commitId, true);
        }
        System.out.println("creating commit triple.txt...");
        List<String> nodeUris = new ArrayList<>();
        List<String> tripleList = new ArrayList<>();
        for(Neo4jNode node : versionInfo.getClassList()){
            tripleList.addAll(handleLabels(node));
            nodeUris.add(node.uri());
        }

        for(Map.Entry<String, List<Neo4jNode>> entry: versionInfo.getMethodMap().entrySet()){
            List<Neo4jNode> methods = entry.getValue();
            String classUri = entry.getKey();
            for(Neo4jNode method:methods){
                tripleList.addAll(handleLabels(method));
                tripleList.add(formTriple(classUri, prefixedName(BGOntology.HAS_PROPERTY), method.uri()));
                nodeUris.add(method.uri());
            }
        }

        for(Map.Entry<String, List<Neo4jRelation>> entry: versionInfo.getMethodSubGraphMap().entrySet()){
            List<Neo4jRelation> relations = entry.getValue();
            for(Neo4jRelation relation: relations){
                for(int i=1;i<relation.getNodes().size();i++){
                    Neo4jNode node = relation.getNodes().get(i);
                    if(!nodeUris.contains(node.uri())){
                        tripleList.addAll(handleLabels(node));
                        nodeUris.add(node.uri());
                    }
                }
                for(int i=0;i<relation.getEdges().size();i++){
                    String edge = relation.getEdges().get(i);
                    tripleList.add(formTriple(relation.getNodes().get(i).uri(), edge, relation.getNodes().get(i+1).uri()));
                }
            }
        }
        FileUtil.appendListToFile(tripleList, filePath);
    }

    private static List<String> handleLabels(Neo4jNode node){
        List<String> triples = new ArrayList<>();
        //处理标签
        List<String> labels = node.getLabels();
        labels.remove("Resource");
        for(String label: labels){
            triples.add(formTriple(node.uri(), label_relation, label));
        }
        return triples;
    }

    private static String formTriple(String e1, String r, String e2){
        return e1 + "\t"+r+"\t"+ e2;
    }
}
