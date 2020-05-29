# GithubApp
GithubApp helps you locate buggy code corresponding to bug reports.
Once there is a new bug report raised by users in the issue board, BugPecker could give you the suspicious code at method level. 

## Create a GithubApp By Yourself
If you want to use the githubApp service, you need to create a githubApp by yourself. The reference is [here](https://developer.github.com/). You can use the code we provide here as the server of your githubApp. 
### Requirements

- Java 1.8
- Tomcat

### Configure

This server call the RevisionAnalyzer componect to initialize your repository. You need to edit the initUrl and UpdateUrl to use the RevisionAnalyzer component in file [HandleIssueServiceImpl.java](./src/main/java/com/githubApp/service/impl/HandleIssueServiceImpl.java). These parameters refer to the location your analyzer service layed out.
Before use the Matcher and Learner component, you need to edit the resultUrl in file [HandleIssueServiceImpl](./src/main/java/com/githubApp/service/impl/HandleIssueServiceImpl.java). This url should be the same with the ip of the callback url of your githubApp. 

Also you need to change the IP and Port in [LocateBugServiceImpl](./src/main/java/com/githubApp/service/impl/LocateBugServiceImpl.java). These parameters refer the location where your bug locator service layed out.

## GithubApp snapshot
### Initialization
You should authorize GithubApp to access the repository at first.
The past commits and bug reports will be used to train the model of BugPecker.
Depends on the size of your repository, the initialization may take several minutes.
As figure 1 shows, statistics of your repository are available after the initialization.
![avatar](https://raw.githubusercontent.com/Tekfei/test/master/init.png)
### Bug Localization
You need to assign your issue a bug label and the corresponding commitId for a new bug report by default is the latest commitId in master branch. If you want to assign a sepcific commitId, you should write in the title in the form of "&commitId:……".
BugPecker will be triggered automatically and comment the bug report with suspicious buggy code.
![avatar](https://raw.githubusercontent.com/Tekfei/test/master/result.png)
