# GithubApp
GithubApp helps you locate buggy code corresponding to bug reports.
Once there is a new bug report raised by users in the issue board, BugPecker could give you the suspicious code at method level. 
More details from [BugPecker repository](https://github.com/RAddRiceee/BugPecker).

The usage is as follows:
## Initialization
You should authorize GithubApp to access the repository at first.
The past commits and bug reports will be used to train the model of BugPecker.
Depends on the size of your repository, the initialization may take several minutes.
As figure 1 shows, statistics of your repository are available after the initialization.
![avatar](https://raw.githubusercontent.com/Tekfei/test/master/2.png)
## Bug Localization
You can assign the corresponding commitId for a new bug report(by default it is the latest commitId in master branch).
BugPecker will be triggered automatically and comment the bug report with suspicious buggy code.
![avatar](https://raw.githubusercontent.com/Tekfei/test/master/result.png)

