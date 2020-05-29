package com.githubApp.model;

import java.io.Serializable;

public class BugInfo implements Serializable {
    private String userName;

    private String repoName;

    private int issueId;

    private String issueTitle;

    private String bugLocalization;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public int getIssueId() {
        return issueId;
    }

    public void setIssueId(int issueId) {
        this.issueId = issueId;
    }

    public String getIssueTitle() {
        return issueTitle;
    }

    public void setIssueTitle(String issueTitle) {
        this.issueTitle = issueTitle;
    }

    public String getBugLocalization() {
        return bugLocalization;
    }

    public void setBugLocalization(String bugLocalization) {
        this.bugLocalization = bugLocalization;
    }
}
