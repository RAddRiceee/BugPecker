package org.codeontology.neo4j;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.codeontology.BGOntology;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;

import static org.codeontology.neo4j.Neo4jQuery.prefixedName;
import static org.codeontology.neo4j.Neo4jLogger.extractVersion;
import static org.neo4j.driver.v1.Values.parameters;

public class AddSimRank {

    private String projectID;

    private String simFile;
    private String methodUriIdMapFile;
    private Map<String, String> methodUriIdMap;

    private final String folder = "simRank/";
    private int batch = 100;
    public static PeriodFormatter formatter = new PeriodFormatterBuilder().appendHours().appendSuffix(" h ").appendMinutes().appendSuffix(" min ").appendSeconds().appendSuffix(" s ").appendMillis().appendSuffix(" ms").toFormatter();

    public String getSimFile() {
        return simFile;
    }

    public AddSimRank(String projectID, Integer batchSize){
        this.projectID = projectID;
        if(batchSize!=null){
            this.batch = batchSize;
        }
        String folderPath = AddSimRank.class.getClassLoader().getResource(folder).getPath();
        try {
            folderPath = URLDecoder.decode(folderPath, "UTF-8" );
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        simFile = folderPath + projectID + "/method2method.txt";
        methodUriIdMapFile = folderPath + projectID + "/methodUriIdMap.txt";
    }

    public int getSimRelNum(){
        List<String> lines = new ArrayList<>();
        try {
            lines = readLines(simFile);
        }catch (Exception e){
            return 0;
        }
        return lines.size();
    }

    public void addSimRankToNeo4j(){
        if(methodUriIdMap == null) {
            getMethodUriIdMapFromFile();
        }
        Neo4jConfig.configDatabaseChoose(projectID);

        List<String> lines = readLines(simFile);
        int batchTimes = (int)Math.ceil(lines.size()/(batch*1.0));

        for(int i=0; i<batchTimes; i++){
            System.out.print(String.format("process %s*%s/%s(%s*%s) lines to add similar relation...",i+1, batch, lines.size(), batchTimes, batch));
            long start = System.currentTimeMillis();
            List<String> subLines = lines.subList(i*batch, Math.min((i+1)*batch, lines.size()));

            List<Map<String,String>> simMapList = new ArrayList<>();//neo4j添加sim关系的参数

            List<String> ids = getIdFromLines(subLines);
            List<String> methodUris = ids.stream().map(id->methodUriIdMap.get(id)).collect(Collectors.toList());

            Map<String, List<String>> methodUpdatePaths = getMethodUpdatePaths(methodUris);

            for(String line : subLines){
                String[] splits = line.split(" ");
                String[] simMethods = new String[]{splits[0], splits[1]};

                List<String> updatePath1 = methodUpdatePaths.get(methodUriIdMap.get(simMethods[0]));
                List<String> updatePath2 = methodUpdatePaths.get(methodUriIdMap.get(simMethods[1]));

                List<List<String>> simRelations = new ArrayList<>();
                getSimFromUpdatePathAToB(updatePath1, updatePath2, simRelations);
                getSimFromUpdatePathAToB(updatePath2, updatePath1, simRelations);

                for(List<String> sim : simRelations){
                    Map<String, String> map = new HashMap<>();
                    map.put("uri1", sim.get(0));
                    map.put("uri2", sim.get(1));
                    simMapList.add(map);
                }
            }

            //向neo4j加入sim关系
            String statement = String.format("UNWIND {simMapList} AS sim MATCH (n:Resource{uri:sim.uri1}),(n1:Resource{uri:sim.uri2}) MERGE (n)-[:%s]->(n1)",
                    prefixedName(BGOntology.SIMILAR_PROPERTY));
            Neo4jLogger.getInstance().exeStatementWithoutTrans(statement, parameters("simMapList", simMapList));
            long end = System.currentTimeMillis();
            Period period = new Period(start, end);
            System.out.println("finished in " + formatter.print(period));
        }
    }

    private static List<String> getIdFromLines(List<String> lines){
        List<String> ids = new ArrayList<>();
        for(String line : lines){
            String[] splits = line.split(" ");
            if(!ids.contains(splits[0])){
                ids.add(splits[0]);
            }
            if(!ids.contains(splits[1])){
                ids.add(splits[1]);
            }
        }
        return ids;
    }

    public static void getSimFromUpdatePathAToB(List<String> updatePathA, List<String> updatePathB, List<List<String>> simRelations){
        List<Integer> verList2 = updatePathB.stream().map(u->extractVersion(u)).collect(Collectors.toList());

        for(int i=0;i<updatePathA.size();i++){
            String node1 = updatePathA.get(i);
            Integer ver1 = extractVersion(node1);
            int oldVerIndex = findNearestOldVerIndex(ver1, verList2);
            if(oldVerIndex<0){
                continue;
            }
            List<String> sim = new ArrayList<>(Arrays.asList(node1, updatePathB.get(oldVerIndex)));
            if(!containSim(sim, simRelations)){
                simRelations.add(sim);
            }
        }
    }

    private static Integer findNearestOldVerIndex(Integer ver, List<Integer> verList){
        if(verList.get(0)>ver){
            return -1;
        }
        Integer oldVer = null;
        for (Integer tmp : verList) {
            if(tmp>ver){
                break;
            }
            oldVer = tmp;
        }
        return verList.indexOf(oldVer);
    }

    private static boolean containSim(List<String> sim, List<List<String>> simList){
        for(List<String> tmp:simList){
            if(tmp.contains(sim.get(0)) && tmp.contains(sim.get(1))){
                return true;
            }
        }
        return false;
    }

    private static Map<String, List<String>> getMethodUpdatePaths(List<String> methodUris){
        Map<String, List<Neo4jRelation>> updatePathMap = new HashMap<>();
        String statement = String.format("UNWIND {methodUris} as mUri MATCH p=(n:%s)-[:%s*]->(n1:%s) " +
                        "where (n.uri STARTS WITH mUri or n1.uri STARTS WITH mUri) " +
                        "and size(()-[:%s]->(n))=0 and size((n1)-[:%s]->())=0 return distinct p, mUri",
                prefixedName(BGOntology.METHOD_ENTITY), prefixedName(BGOntology.UPDATE_PROPERTY), prefixedName(BGOntology.METHOD_ENTITY),
                prefixedName(BGOntology.UPDATE_PROPERTY), prefixedName(BGOntology.UPDATE_PROPERTY));
        Map<String, List<Object>> result = Neo4jQuery.cypherQuery(statement, parameters("methodUris", methodUris));
        List<String> mUriList = result.get("mUri").stream().map(o -> (String)o).collect(Collectors.toList());
        List<Neo4jRelation> updatePathList = result.get("p").stream().map(o -> {
            if(o==null){
                return null;
            }
            return (Neo4jRelation)o;
        }).collect(Collectors.toList());

        for(int i=0;i<updatePathList.size();i++){
            String mUri = mUriList.get(i);
            if(updatePathMap.get(mUri) == null){
                updatePathMap.put(mUri, new ArrayList<>());
            }
            if(updatePathList.get(i)!=null) {
                updatePathMap.get(mUri).add(updatePathList.get(i));
            }
        }

        Map<String, List<String>> updatePathNodeMap = new HashMap<>();
        for(String methodUri : methodUris){
            List<Neo4jRelation> updatePaths = updatePathMap.get(methodUri);

            List<String> updatePathNodes = new ArrayList<>();
            if(CollectionUtils.isEmpty(updatePaths)){
                //未构成update链，只有单个节点
                statement = String.format("MATCH (n:%s) where n.uri STARTS WITH '%s' return distinct n",
                        prefixedName(BGOntology.METHOD_ENTITY), methodUri+'#');
                List<Object> objects = Neo4jQuery.cypherQuery(statement).get("n");
                updatePathNodes.addAll(objects.stream().map(o -> ((Neo4jNode)o).uri()).collect(Collectors.toList()));
            }else{
                //有update链
                for(Neo4jRelation path: updatePaths){
                    updatePathNodes.addAll(path.getNodes().stream().map(o->o.uri()).collect(Collectors.toList()));
                }
            }
            //根据版本号排序
            Collections.sort(updatePathNodes, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    int v1 = extractVersion(o1);
                    int v2 = extractVersion(o2);
                    return v1-v2;
                }
            });
            updatePathNodeMap.put(methodUri, updatePathNodes);
        }
        return updatePathNodeMap;
    }

    private void getMethodUriIdMapFromFile(){
        methodUriIdMap = new HashMap<>();
        List<String> lines = readLines(methodUriIdMapFile);
        for(String line : lines){
            String[] splits = line.split(" ");
            if(methodUriIdMap.get(splits[0])!=null){
                continue;
            }
            methodUriIdMap.put(splits[0], splits[1]);
        }
    }

    private static List<String> readLines(String path){
        List<String> lines = new ArrayList<>();
        try {
            lines = IOUtils.readLines(new FileInputStream(new File(path)), Charsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return lines;
    }

}
