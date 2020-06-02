# Revision Analyzer
## Introduction

Revision analyzer builds revision graphs from code, commits and past bug reports. Revision graphs contain some code related entities and relations between them, mainly method entities and similar-to and call relations between method entities. Each code related entities have a "version" attribute, which means it was extracted from the specific version of source code. 

Revision analyzer are implemented following an ontology-based method [CodeOntology](https://github.com/codeontology/parser), and builded revision graphs are stored in Neo4j. Revision analyzer use [spoon](http://spoon.gforge.inria.fr/index.html), an open-source library to parse Java source code, to extract method entities and call relations between method entities. Revision analyzer serves as a web service. It accepts Cypher queries or web service requests.

We conduct experiments on the dataset created from three open-source projects (AspectJ, SWT and Tomcat).  Revision Analyzer has built revision graphs for these three projects respectively. Three Neo4j databases that respectively stores builded revision graphs is available [here](https://jbox.sjtu.edu.cn/l/aoMeGs).

## Build

### Requirements

- Linux or Mac OS
- Java 1.8
- Neo4j
- Tomcat

### Configaration

1. Modify some configuration parameters in the configuration file [config.properties](./KGWeb/src/main/resources/config.properties) to fit your local environment.
2. Package the project into a .war file. (building Artifacts in Intellij IDEA is better.)

### Run

1. Run the .war file in Intellij IDEA, or copy the packaged .war file to tomcat's subdirectory 'webapp' and start tomcat.
2. build a revision graph of a specific project with a specific version through the http API '/extractNewProject'. Then add new versions of the project to the revision graph through the http API '/versionUpdate'. You can refer to [Rest.java](./KGWeb/src/main/java/org/codeontology/Rest.java) class for more details.
