# Revision Analyzer
## Introduction

Revision analyzer builds revision graphs from commits and past bug reports. Revision graphs contain some code related entities and relations between them, mainly method entities and similar-to and call relations between method entities. Each code related entities have a "version" attribute, which means it was extracted from the specific version of source code. 

Revision analyzer are implemented following an ontology-based method [CodeOntology](https://github.com/codeontology/parser), and stored in Neo4j. Revision analyzer use [spoon](http://spoon.gforge.inria.fr/index.html), an open-source library to parse Java source code, to extract method entities and call relations between method entities. Revision analyzer serves as a web service. It accepts Cypher queries or web service requests.

## Access API



## Install

### Requirements



### Configaration



### Run

