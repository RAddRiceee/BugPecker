package org.codeontology.version;

import com.alibaba.fastjson.JSON;
import org.apache.commons.collections.CollectionUtils;
import org.codeontology.neo4j.Neo4jNode;
import org.codeontology.neo4j.Neo4jRelation;
import org.codeontology.util.FileUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用获取到的VersionDiffInfo更新前一版本的代码实体转为目标版本
 */
public class VersionDiffInfoHandler {

    public static void main(String[] args){
        String dir = "/Users/hsz/projects/BugLocJson/";
        handleVersionDiffInfo(dir+"Time_3ba9ba799b3261b7332a467a88be142c83b298fd_versionDiffInfo_1582886724382.json"
                , dir+"Time_bcb044669b4d1f8d334861ccbd169924d6ef3b54_versionInfo_1582887032535.json"
                ,dir+"Time_3ba9ba799b3261b7332a467a88be142c83b298fd_generated.json");
    }

    /**
     * lastInfoPath 前一版本的代码实体json文件位置
     * destInfoPath 为生成的目标版本实体的json文件位置
     * commitId为目标版本
     */
    public static void handleVersionDiffInfo(String diffInfoPath, String lastInfoPath, String destInfoPath){
        VersionInfo versionInfo = JSON.parseObject(FileUtil.readJsonFile(lastInfoPath), VersionInfo.class);
        VersionDiffInfo versionDiffInfo = JSON.parseObject(FileUtil.readJsonFile(diffInfoPath), VersionDiffInfo.class);

        //将更新的类的旧实体放入删除列表中
        Map<String, VersionInfo> updateClassMap = versionDiffInfo.getUpdateClassMap();
        if(updateClassMap.size()>0){
            versionDiffInfo.getDeleteMap().get(VersionDiffInfo.Type.CLASS).addAll(updateClassMap.keySet());
        }

        //处理删除的类
        List<String> deleteClassUriList = versionDiffInfo.getDeleteMap().get(VersionDiffInfo.Type.CLASS);
        if(!CollectionUtils.isEmpty(deleteClassUriList)) {
            List<Neo4jNode> deleteClassList = new ArrayList<>();
            for (Neo4jNode classNode : versionInfo.getClassList()) {
                if (deleteClassUriList.contains(classNode.uri())) {
                    deleteClassList.add(classNode);
                    versionInfo.getVariableMap().remove(classNode.uri());

                    List<Neo4jNode> methodNodeList = versionInfo.getMethodMap().get(classNode.uri());
                    versionInfo.getMethodMap().remove(classNode.uri());

                    if(!CollectionUtils.isEmpty(methodNodeList)) {
                        for(Neo4jNode methodNode : methodNodeList){
                            versionInfo.getMethodSubGraphMap().remove(methodNode.uri());
                        }
                    }
                }
            }
            versionInfo.getClassList().removeAll(deleteClassList);
        }

        List<String> deleteMethodUriList = versionDiffInfo.getDeleteMap().get(VersionDiffInfo.Type.METHOD);
        List<String> deleteVariableUriList = versionDiffInfo.getDeleteMap().get(VersionDiffInfo.Type.VARIABLE);
        Map<String, VersionInfo> updateMethodMap = versionDiffInfo.getUpdateMethodMap();

        //处理删除的变量
        if(!CollectionUtils.isEmpty(deleteVariableUriList)){
            for (List<Neo4jNode> variableList : versionInfo.getVariableMap().values()) {
                List<Neo4jNode> tmpList = new ArrayList<>();
                for(Neo4jNode variable:variableList){
                    if(deleteVariableUriList.contains(variable.uri())){
                        tmpList.add(variable);
                    }
                }
                variableList.removeAll(tmpList);
            }
        }

        if(!CollectionUtils.isEmpty(deleteMethodUriList) || updateMethodMap.size()>0){
            for(List<Neo4jNode> methodList : versionInfo.getMethodMap().values()){
                List<Neo4jNode> tmpDeleteList = new ArrayList<>();
                List<Neo4jNode> tmpAddList = new ArrayList<>();
                for(Neo4jNode method:methodList){
                    //处理删除的方法
                    if(deleteMethodUriList.contains(method.uri())){
                        tmpDeleteList.add(method);
                        versionInfo.getMethodSubGraphMap().remove(method.uri());
                    }

                    //处理更新的方法，新版本的方法替换旧版本
                    VersionInfo updateMethod = updateMethodMap.get(method.uri());
                    if(updateMethod!= null){
                        tmpDeleteList.add(method);
                        versionInfo.getMethodSubGraphMap().remove(method.uri());

                        tmpAddList.addAll(updateMethod.getMethodMap().values().iterator().next());
                        if(updateMethod.getMethodSubGraphMap().size()==1) {
                            Map.Entry<String, List<Neo4jRelation>> entry = updateMethod.getMethodSubGraphMap().entrySet().iterator().next();
                            versionInfo.getMethodSubGraphMap().put(entry.getKey(), entry.getValue());
                        }
                    }
                }
                methodList.removeAll(tmpDeleteList);
                methodList.addAll(tmpAddList);
            }
        }

        //处理更新的类,添加新版本的类
        if(updateClassMap.size()>0){
            for(Map.Entry<String, VersionInfo> entry: updateClassMap.entrySet()){
                addClass(versionInfo, entry.getValue());
            }
        }

        //处理增加的类
        VersionInfo addClassInfo = versionDiffInfo.getAddInfo().get(VersionDiffInfo.Type.CLASS);
        if(addClassInfo!=null){
            addClass(versionInfo,addClassInfo);
        }

        //处理增加的方法
        VersionInfo addMethodInfo = versionDiffInfo.getAddInfo().get(VersionDiffInfo.Type.METHOD);
        if(addMethodInfo!=null){
            for(Neo4jNode classNode : addMethodInfo.getClassList()){
                versionInfo.getMethodMap().get(classNode.uri()).addAll(addMethodInfo.getMethodMap().get(classNode.uri()));
            }
            versionInfo.getMethodSubGraphMap().putAll(addMethodInfo.getMethodSubGraphMap());
        }

        //处理增加的变量
        VersionInfo addVariableInfo = versionDiffInfo.getAddInfo().get(VersionDiffInfo.Type.VARIABLE);
        if(addVariableInfo!=null){
            for(Neo4jNode classNode : addVariableInfo.getClassList()){
                versionInfo.getVariableMap().get(classNode.uri()).addAll(addVariableInfo.getVariableMap().get(classNode.uri()));
            }
        }

        //写入文件
        FileUtil.saveJsonToFile(JSON.toJSONString(versionInfo), destInfoPath);
    }

    private static void addClass(VersionInfo versionInfo, VersionInfo addClassInfo){
        versionInfo.getClassList().addAll(addClassInfo.getClassList());
        versionInfo.getVariableMap().putAll(addClassInfo.getVariableMap());
        versionInfo.getMethodMap().putAll(addClassInfo.getMethodMap());
        versionInfo.getMethodSubGraphMap().putAll(addClassInfo.getMethodSubGraphMap());
    }
}
