package com.githubApp.model;

import java.util.List;

public class BugLocator {

    private String repoName;

    private String issueTitle;

    private String issueUrl;

    private List<MethodLocator> methodLocatorList;

    public String getRepoName() {
        return repoName;
    }

    public void setRepoName(String repoName) {
        this.repoName = repoName;
    }

    public String getIssueTitle() {
        return issueTitle;
    }

    public void setIssueTitle(String issueTitle) {
        this.issueTitle = issueTitle;
    }

    public String getIssueUrl() {
        return issueUrl;
    }

    public void setIssueUrl(String issueUrl) {
        this.issueUrl = issueUrl;
    }


    public List<MethodLocator> getMethodLocatorList() {
        return methodLocatorList;
    }

    public void setMethodLocatorList(List<MethodLocator> methodLocatorList) {
        this.methodLocatorList = methodLocatorList;
    }
}
