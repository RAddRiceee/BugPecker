# BugPecker
BugPecker is a githubApp to help you locate faulty code corresponding to bug reports.
Once there is a new bug report raised by users in the issue board, BugPecker could give you the suspicious code at method level. 

## Usage
You need to set up a githubApp server by yourself to user BugPecker. The process of installation is [here](https://github.com/RAddRiceee/BugPecker/tree/master/GithubApp).
### Initialization
After the installation,you should authorize BugPecker to access the repository. The past commits and bug reports will be used to train the model of BugPecker. Depends on the size of your repository, the initialization may take several minutes.

As figure 1 shows, statistics of your repository are available after the initialization.
![avatar](https://raw.githubusercontent.com/Tekfei/test/master/init.png)
### Bug Localization
You need to assign your issue a bug label and the corresponding commitId for a new bug report by default is the latest commitId in master branch. If you want to assign a sepcific commitId, you should write in the title in the form of "&commitId:……".

BugPecker will be triggered automatically and comment the bug report with suspicious  code.
![avatar](https://raw.githubusercontent.com/Tekfei/test/master/result.png)

## Architecture
BugPecker consists of the following three components:
### Revision analyzer

The revision analyzer builds revision graphs from code, commits and past bug reports. Revision graphs contain some code related entities and relations between them, mainly method entities and similar-to and call relations between method entities. Each code related entities have a "version" attribute, which means it was extracted from the specific version of source code. More implementation details are available [here](https://github.com/RAddRiceee/BugPecker/tree/master/RevisionAnalyzer).

### Semantic matcher and Learner

The Semantic matcher and the Learner are two components of BugPecker. Thr former calculates the semantic similarity between a method and a bug report and passes the semantic matching score to the Learner. The Learner
combines the semantic mathing score and the other three kinds of scores(bug fixing recency score, bug fixing frequency score, collaborative filtering score) to select the possible faulty methods from repository and rank them
by supocious score. More implementation details are available [here](https://github.com/RAddRiceee/BugPecker/tree/master/MatcherAndLearner).

### Github plugin

We have implemented the BugPecker tool as a Github plugin in Java. After submitting a bug report in the Github issue system, programmers could get ranked suspicious faulty methods later from BugPecker. You are recommended to create your own githubApp with the code we provide. More implementation details are available [here](https://github.com/RAddRiceee/BugPecker/tree/master/GithubApp).

## Dataset
We have evaluated BugPecker on three open source projects (AspectJ, SWT and Tomcat). The dataset and some intermediate data relevant to the experiment are available [here](https://jbox.sjtu.edu.cn/l/aoMeGs).
