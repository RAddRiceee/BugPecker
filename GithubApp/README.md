# GithubApp
GithubApp helps you locate buggy code corresponding to bug reports.
Once there is a new bug report raised by users in the issue board, BugPecker could give you the suspicious code at method level. 

If you want to use the githubApp service, you need to set up a [githubApp](https://developer.github.com/) server by yourself.
## Requirements

- Java 1.8
- Tomcat

## Configure

This server calls the Revision Analyzer to initialize your repository. You need to edit the initUrl and UpdateUrl to use the Revision Analyzer in file [HandleIssueServiceImpl](./src/main/java/com/githubApp/service/impl/HandleIssueServiceImpl.java). These parameters refer to the location your analyzer service layed out.

Before use the Matcher and Learner component, you need to edit the resultUrl in file [HandleIssueServiceImpl](./src/main/java/com/githubApp/service/impl/HandleIssueServiceImpl.java). This url should be the same with the ip of the callback url of your githubApp. 

Also you need to change the IP and Port in [LocateBugServiceImpl](./src/main/java/com/githubApp/service/impl/LocateBugServiceImpl.java). These parameters refer the location where your bug locator service layed out.
