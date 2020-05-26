# BugPecker
Method Level Bug Localization over Multi-versioned Big Code
## Introduction
The task of locating the potential buggy files in a
software project is called bug localization. To help programmers
in bug localization process, many automated bug localization
approaches have been proposed. Although these approaches
achieved promising results at file level, method level bug localization
is still challenging because of semantic gap between
bug reports and code, and insufficient information of short
methods. In this paper, we present BugPecker, a method level
bug localization tool over multi-versioned big code. The key
idea is to 1) apply AST (abstract syntax tree) based distributed
code representation and multi-layer perceptron to alleviate the
semantic gap problem, and 2) discover and utilize relations
between methods in big code to expand the information of
methods, and calculate collaborative filtering feature for ranking.
We have implemented BugPecker, and evaluated it on three
public datasets. The results show that BugPecker achieves a
MAP of 0.263 and MRR of 0.291, outperforming DNNLoc-m
and BLIA 1.5.
## MVBC (Multi-Versioned Big Code)

## Github plugin

## Bug Localization

### environment

### data
First you need download the raw data from [dataset](https://jbox.sjtu.edu.cn/l/VooilN), to generate the data for training and testing( see data processing) 
```
cd bugloc/dataset/
mkdir dataset
cd dataset
unrar x dataset.rar
```

You can also download the processed data from [processed_data] to train and test a model.
```
TODO
```

you can download the test result from [test_result] to evaluate the result.
```
TODO
```
### data processing 

### train

### test

# install