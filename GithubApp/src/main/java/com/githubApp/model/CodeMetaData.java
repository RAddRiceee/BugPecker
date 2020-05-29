package com.githubApp.model;

import java.io.Serializable;

public class CodeMetaData implements Serializable {

    private int versionNum;

    private int callRelNUm;

    private int methodNum;

    private int simRelNum;

    public int getVersionNum() {
        return versionNum;
    }

    public void setVersionNum(int versionNum) {
        this.versionNum = versionNum;
    }

    public int getCallRelNUm() {
        return callRelNUm;
    }

    public void setCallRelNUm(int callRelNUm) {
        this.callRelNUm = callRelNUm;
    }

    public int getMethodNum() {
        return methodNum;
    }

    public void setMethodNum(int methodNum) {
        this.methodNum = methodNum;
    }

    public int getSimRelNum() {
        return simRelNum;
    }

    public void setSimRelNum(int simRelNum) {
        this.simRelNum = simRelNum;
    }
}
