package com.githubApp.service;

import com.alibaba.fastjson.JSONObject;
import com.githubApp.model.BugLocator;

public interface HandleIssueService {

    String sendInfoToModel(String code);

//    void downLoadRepo(String fullName);

    void handleBugReport(String issueUrl, JSONObject issue);

//    void editIssue(String issueUrl, JSONObject issue, String bugLocalization);

    boolean isBugReport(JSONObject issue);

    String getLatestCommitId(String repoName);

    BugLocator getBugDetail(String userName, String repoName, String issueId);

//    IssueInfo getCommitId(JSONObject issue, Map<String, Instant> commitList);
//
//    String getUrlConnection(String url);
//
//    Map<String, Instant> getCommitList(String repoName);
}
