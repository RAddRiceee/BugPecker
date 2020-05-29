package com.githubApp.service;

public interface LocateBugService {

    String initRepoKG(String repoInfo,String url);

//    String getBugLocalization(String issueInfo);
    String getBugLocalization(String issueTitle,String issueBody,String commitId, String repoName);

}
