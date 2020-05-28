package org.codeontology.version;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 目标版本跟前一版本的代码实体差异
 */
public class VersionDiffInfo {

    public enum Type {
        CLASS,
        VARIABLE,
        METHOD
    }

    private String commitId;//目标版本
    private String lastCommitId;//neo4j中存储的目标版本的前一版本

    private Map<Type, VersionInfo> addInfo;//增加的类/方法/变量，VersionInfo包含了增加的类型包含的所有内容

    private Map<String, VersionInfo> updateClassMap; //更新的类，String为被旧版本类的uri，VersionInfo为更新后类的内容
    private Map<String, VersionInfo> updateMethodMap;//更新的方法，String为旧版本的方法的uri, VersionInfo中的map的key没有意义，VersionInfo只是用来包括method和methodSubGraph

    private Map<Type, List<String>> deleteMap;//删除的类/方法/变量，String值为实体的uri

    public static VersionDiffInfo getEmptyVersionDiffInfo(){
        VersionDiffInfo versionDiffInfo = new VersionDiffInfo();
        versionDiffInfo.setAddInfo(new HashMap<>());
        versionDiffInfo.setUpdateClassMap(new HashMap<>());
        versionDiffInfo.setUpdateMethodMap(new HashMap<>());
        versionDiffInfo.setDeleteMap(new HashMap<>());
        versionDiffInfo.getDeleteMap().put(Type.CLASS, new ArrayList<>());
        versionDiffInfo.getDeleteMap().put(Type.METHOD, new ArrayList<>());
        versionDiffInfo.getDeleteMap().put(Type.VARIABLE, new ArrayList<>());
        return versionDiffInfo;
    }

    public String getLastCommitId() {
        return lastCommitId;
    }

    public void setLastCommitId(String lastCommitId) {
        this.lastCommitId = lastCommitId;
    }

    public String getCommitId() {
        return commitId;
    }

    public void setCommitId(String commitId) {
        this.commitId = commitId;
    }

    public Map<Type, VersionInfo> getAddInfo() {
        return addInfo;
    }

    public void setAddInfo(Map<Type, VersionInfo> addInfo) {
        this.addInfo = addInfo;
    }

    public Map<String, VersionInfo> getUpdateClassMap() {
        return updateClassMap;
    }

    public void setUpdateClassMap(Map<String, VersionInfo> updateClassMap) {
        this.updateClassMap = updateClassMap;
    }

    public Map<String, VersionInfo> getUpdateMethodMap() {
        return updateMethodMap;
    }

    public void setUpdateMethodMap(Map<String, VersionInfo> updateMethodMap) {
        this.updateMethodMap = updateMethodMap;
    }

    public Map<Type, List<String>> getDeleteMap() {
        return deleteMap;
    }

    public void setDeleteMap(Map<Type, List<String>> deleteMap) {
        this.deleteMap = deleteMap;
    }

}
