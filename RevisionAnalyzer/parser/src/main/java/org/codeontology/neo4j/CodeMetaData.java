package org.codeontology.neo4j;

public class CodeMetaData{
    private String versionNum;//版本数
    private String methodNum;//方法总数
    private String callRelNUm;//方法间调用关系数
    private String simRelNum;//方法间相似关系数

    public String getVersionNum() {
        return versionNum;
    }

    public void setVersionNum(String versionNum) {
        this.versionNum = versionNum;
    }

    public String getMethodNum() {
        return methodNum;
    }

    public void setMethodNum(String methodNum) {
        this.methodNum = methodNum;
    }

    public String getCallRelNUm() {
        return callRelNUm;
    }

    public void setCallRelNUm(String callRelNUm) {
        this.callRelNUm = callRelNUm;
    }

    public String getSimRelNum() {
        return simRelNum;
    }

    public void setSimRelNum(String simRelNum) {
        this.simRelNum = simRelNum;
    }
}
