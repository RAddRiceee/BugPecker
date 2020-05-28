import os
import git
import sys
import gc
import random
import string
import pandas as pd
from nltk.stem import PorterStemmer
from nltk.corpus import stopwords
from nltk.tokenize import word_tokenize
from sklearn.feature_extraction.text import TfidfVectorizer


def stem_tokens(tokens):
    """ Remove stopword and stem

    Arguments:
        tokens {list} -- tokens to stem
    """
    stemmer = PorterStemmer()
    removed_stopwords = [
        stemmer.stem(item) for item in tokens if item not in stopwords.words("english")
    ]

    return removed_stopwords


def normalize(text):
    """ Lowercase, remove punctuation, tokenize and stem

    Arguments:
        text {string} -- A text to normalize
    """
    remove_punc_map = dict((ord(char), None) for char in string.punctuation)
    removed_punc = text.lower().translate(remove_punc_map)
    tokenized = word_tokenize(removed_punc)
    stemmed_tokens = stem_tokens(tokenized)

    return stemmed_tokens


def cosine_sim(text1, text2):
    """ Cosine similarity with tfidf

    Arguments:
        text1 {string} -- first text
        text2 {string} -- second text
    """
    vectorizer = TfidfVectorizer(tokenizer=normalize, min_df=1, stop_words="english")
    tfidf = vectorizer.fit_transform([text1, text2])
    sim = (tfidf * tfidf.T).A[0, 1]

    return sim


def top_k_wrong_files(right_files, br_raw_text, java_files, k=20):
    """ Randomly samples 2*k from all wrong files and returns metrics
        for top k files according to rvsm similarity.

    Arguments:
        right_files {list} -- list of right files
        br_raw_text {string} -- raw text of the bug report
        java_files {dictionary} -- dictionary of source code files

    Keyword Arguments:
        k {integer} -- the number of files to return metrics (default: {50})
    """

    # Randomly sample 2*k files
    randomly_sampled = random.sample(set(java_files.index.tolist()) - set(right_files), 2 * k)

    all_files = []
    for filename in randomly_sampled:
        src = java_files[java_files.index == filename]['code'].values[0]
        rvsm = cosine_sim(br_raw_text[0], src)
        print(rvsm)
        all_files.append((filename, rvsm))
    top_k_files = sorted(all_files, key=lambda x: x[1], reverse=True)[:k]
    return top_k_files


def random_k_wrong_files(right_files, java_files, k=20):
    # Randomly sample 2*k files
    randomly_sampled = random.sample(set(java_files) - set(right_files), k)
    return randomly_sampled


def get_months_between(d1, d2):
    """ Calculates the number of months between two date strings

    Arguments:
        d1 {datetime} -- date 1
        d2 {datetime} -- date 2
    """

    diff_in_months = abs((d1.year - d2.year) * 12 + d1.month - d2.month)

    return diff_in_months


def previous_reports_for_mlevel(method, until, bug_reports):
    def find_method_occurence(methods):
        if method in methods:
            return 1
        else:
            return 0

    pre_reports = bug_reports[bug_reports['commit_time'] < until]
    pre_reports['fre'] = pre_reports['method'].apply(find_method_occurence)
    pre_reports = pre_reports[pre_reports['fre'] == 1]
    return pre_reports


def most_recent_report_for_mlevel(reports):
    """ Returns the most recently submitted previous report that shares a method name with the given bug report
    """
    if len(reports):
        reports = reports.sort_values("commit_time")
        return reports.tail(1)
    return None


def bug_fixing_recency_for_mlevel(commit_time, prev_fixed_reports):
    mrr = most_recent_report_for_mlevel(prev_fixed_reports)
    if mrr is not None:
        for index, row in mrr.iterrows():
            mrr_report_time = row['commit_time']
        return 1 / float(get_months_between(commit_time, mrr_report_time) + 1)
    return 0


def collaborative_filtering_score_for_mlevel(cur_report, prev_reports, k=50,
                                             whether_expand_commit=False, commit2commit=None,
                                             whether_expand_method=False, method2method=None):
    """
        给定一个bug report计算其之前出现的所有相似度高的bug reports
        然后计算该bug report与相似bug report所修改过的方法的与该bug report的协同过滤分数
    :param cur_report: 当前bug report
    :param prev_reports: 当前report 按时间排序其之前的所有bug report
    :param commit2commit: commit之间的相似度
    :param method2method: method之间的相似度
    :param k: 取前k个相似度高的commit
    :param whether_expand_commit:是否扩充相似commit
    :param whether_expand_method:是否扩充相似method
    :return: 协同过滤分值高的方法的dict
    """
    sim_commits = {}
    for index, row in prev_reports.iterrows():
        report = row["report"]
        bug_id = row['commit_id']
        sim_score = cosine_sim(' '.join(cur_report), ' '.join(report))
        sim_commits[bug_id] = sim_score
    # 按bug report的 cos_sim 进行排序取前k个
    sim_commits_sorted_top_k = sorted(sim_commits.items(), key=lambda item: (item[1], item[0]), reverse=True)[:k]
    sim_commits_ids = [i[0] for i in sim_commits_sorted_top_k]
    print('before expand commit:', len(sim_commits_ids))

    all_ids = list(sim_commits)
    for key in all_ids:
        if key not in sim_commits_ids:
            sim_commits.pop(key)

    # 对所得的相似commit集合进行扩充
    if whether_expand_commit:
        expand_commits = {}
        for commit_id in sim_commits_ids:
            #  找出所有与当前sim commit 相似的 commit
            expand_commits_1 = commit2commit[commit2commit['commit1'] == commit_id]
            expand_commits_2 = commit2commit[commit2commit['commit2'] == commit_id]
            expand_commits_2 = expand_commits_2.rename(columns={'commit1': 'commit2', 'commit2': 'commit1'})
            # 这样当前源commit都在第一列
            expand_commits_df = expand_commits_1.append(expand_commits_2)
            length = len(expand_commits_df)

            for index, row in expand_commits_df.iterrows():
                sim_commit_id = row['commit2']
                score = row['sim_score']
                if sim_commit_id not in expand_commits:
                    expand_commits[sim_commit_id] = 0
                expand_commits[sim_commit_id] += (score / length)
        # 对扩充的commit也取前k个
        expand_commits_sorted_top_k = sorted(expand_commits.items(), key=lambda item: (item[1], item[0]), reverse=True)[
                                      :k]
        expand_commits_ids = [i[0] for i in expand_commits_sorted_top_k]
        for commit_id in expand_commits_ids:
            if commit_id in sim_commits:
                sim_commits[commit_id] += expand_commits[commit_id]
            else:
                sim_commits[commit_id] = expand_commits[commit_id]
    print('expand commit num:', len(expand_commits_ids))
    print('after expand commit:', len(sim_commits))

    method_cfs = {}
    # 遍历sim_bug_report计算sim_method的协同过滤分数
    for commit_id, score in sim_commits.items():
        cur_sim_commit = prev_reports[prev_reports['commit_id'] == commit_id]
        if len(cur_sim_commit) == 0:  # 当前commit可能不可见
            continue
        sim_commit_methods = cur_sim_commit['method'].values.tolist()[0]
        num_methods = len(sim_commit_methods)
        for method in sim_commit_methods:
            if method not in method_cfs:
                method_cfs[method] = 0
            method_cfs[method] += (sim_commits[commit_id] / num_methods)

    print('before expand method:', len(method_cfs))

    if whether_expand_method:
        for method_uri in list(method_cfs.keys()):
            #  找出所有与当前sim commit 相似的 commit
            sim_methods_1 = method2method[method2method['method1'] == method_uri]
            sim_methods_2 = method2method[method2method['method2'] == method_uri]
            sim_methods_2 = sim_methods_2.rename(columns={'method1': 'method2', 'method2': 'method1'})
            sim_methods = sim_methods_1.append(sim_methods_2)
            # 当前源方法都在method1 这一列中
            length = len(sim_methods)
            for index, row in sim_methods.iterrows():
                sim_method_uri = row['method2']
                score = row['sim_score']
                if sim_method_uri not in method_cfs:
                    method_cfs[sim_method_uri] = 0
                method_cfs[sim_method_uri] += (score / length)
    print('after expand method:', len(method_cfs))

    return method_cfs


def get_related_methods_to_expand_short_method(cur_method_uri, method2method, method_call_method, method_call_graph,
                                               k=10):
    """
     为当前短方法找到相关的的方法来扩充，相关关系有 方法相似度 方法调用关系 方法共现调用分数
    :param k:
    :param cur_method_uri: 当前方法uri
    :param method2method:  方法相似度
    :param method_call_method: 方法共现调用分数 A->B , A->C score(B,C)
    :param method_call_graph: 方法调用关系
    :return: 用于扩充的方法uri集合
    """

    def get_expand_methods(current_method_uri, dataframe, by_col):
        sim_methods_1 = dataframe[dataframe['method1'] == current_method_uri]
        sim_methods_2 = dataframe[dataframe['method2'] == current_method_uri]
        sim_methods_2 = sim_methods_2.rename(columns={'method1': 'method2', 'method2': 'method1'})
        sim_methods = sim_methods_1.append(sim_methods_2)
        if by_col is not None:
            sim_methods = sim_methods.sort_values(by=by_col)
        return sim_methods

    expand_method_uris = {}

    methods_1 = get_expand_methods(cur_method_uri, method2method, 'sim_score')
    methods_2 = get_expand_methods(cur_method_uri, method_call_method, 'call_score')
    methods_3 = get_expand_methods(cur_method_uri, method_call_graph, None)

    for method in methods_1[:k]:
        expand_method_uris[method] = 1  # 1没有含义
    for method in methods_2[:k]:
        expand_method_uris[method] = 1  # 1没有含义
    for method in methods_3[:k]:
        expand_method_uris[method] = 1  # 1没有含义

    return expand_method_uris.keys()


def load_sim_files(sim_file_path):
    commit2commit = pd.read_csv(sim_file_path + 'commit2commit.txt', header=None, sep='\\s+',
                                names=['id1', 'id2', 'Unnamed:2', 'Unnamed:3', 'sim_score', 'Unnamed:5'])

    commit_id_map = pd.read_csv(sim_file_path + 'commitIdMap.txt', header=None, sep='\\s+',
                                names=['id', 'commit_id'])

    method2method = pd.read_csv(sim_file_path + 'method2method.txt', header=None, sep='\\s+',
                                names=['id1', 'id2', 'Unnamed:2', 'Unnamed:3', 'sim_score', 'Unnamed:5'])

    # method_id_map = pd.read_csv(sim_file_path + 'methodIdMap.txt', header=None, sep='\\s+',
    #                           names=['id', 'method_uri'])

    # swt
    method_id_map = pd.read_csv(sim_file_path + 'methodIdMap.txt', header=None,
                                names=['row'])

    def split_row(row, flag):
        import re
        tokens = re.split("\\s+", row, 1)
        if flag == 'id':
            return int(tokens[0])
        else:
            return tokens[1]

    method_id_map['id'] = method_id_map.apply(lambda row: split_row(row['row'], 'id'), axis=1)
    method_id_map['method_uri'] = method_id_map.apply(lambda row: split_row(row['row'], 'uri'), axis=1)

    method_call_method = pd.read_csv(sim_file_path + 'methodCallmethod.txt', header=None, sep='\\s+',
                                     names=['id1', 'id2', 'Unnamed:2', 'Unnamed:3', 'call_score', 'Unnamed:5'])

    method_call_graph = pd.read_csv(sim_file_path + 'methodCallGraph.txt', header=None, sep='\\s+',
                                    names=['id1', 'id2', ])

    # swt的特殊操作
    def fix_method_uri(method_uri):
        method_uri = 'eclipse.platform.swt.' + method_uri.replace('/', '.')
        return method_uri

    method_id_map['method_uri'] = method_id_map['method_uri'].apply(fix_method_uri)
    method_id_map = method_id_map[method_id_map['method_uri'] != '']

    # 将method2method 和 method_call_method 转换成uri标识
    method1_list, method2_list = [], []
    for index, row in method2method.iterrows():
        id1 = row['id1']
        id2 = row['id2']
        method1 = method_id_map[method_id_map['id'] == id1]['method_uri'].values[0]
        method2 = method_id_map[method_id_map['id'] == id2]['method_uri'].values[0]
        method1_list.append(method1)
        method2_list.append(method2)
    method2method['method1'] = method1_list
    method2method['method2'] = method2_list
    del method2method['Unnamed:2'], method2method['Unnamed:3'], method2method['Unnamed:5'], method1_list, method2_list

    method1_list, method2_list = [], []
    for index, row in method_call_method.iterrows():
        id1 = row['id1']
        id2 = row['id2']
        method1 = method_id_map[method_id_map['id'] == id1]['method_uri'].values[0]
        method2 = method_id_map[method_id_map['id'] == id2]['method_uri'].values[0]
        method1_list.append(method1)
        method2_list.append(method2)
    method_call_method['method1'] = method1_list
    method_call_method['method2'] = method2_list
    del method_call_method['Unnamed:2'], method_call_method['Unnamed:3'], method_call_method[
        'Unnamed:5'], method1_list, method2_list

    # 将commit2commit增加hash编码
    commit1_list, commit2_list = [], []
    for index, row in commit2commit.iterrows():
        id1 = row['id1']
        id2 = row['id2']
        commit1 = commit_id_map[commit_id_map['id'] == id1]['commit_id'].values[0]
        commit2 = commit_id_map[commit_id_map['id'] == id2]['commit_id'].values[0]
        commit1_list.append(commit1)
        commit2_list.append(commit2)
    commit2commit['commit1'] = commit1_list
    commit2commit['commit2'] = commit2_list
    del commit2commit['Unnamed:2'], commit2commit['Unnamed:3'], commit2commit['Unnamed:5'], commit1_list, commit2_list

    # 将commit2commit增加hash编码
    method1_list, method2_list = [], []
    for index, row in method_call_graph.iterrows():
        id1 = row['id1']
        id2 = row['id2']
        method1 = method_id_map[method_id_map['id'] == id1]['method_uri'].values[0]
        method2 = method_id_map[method_id_map['id'] == id2]['method_uri'].values[0]
        method1_list.append(method1)
        method2_list.append(method2)
    method_call_graph['method1'] = method1_list
    method_call_graph['method2'] = method2_list
    del method1_list, method2_list

    gc.collect()
    return commit2commit, method2method, method_call_method, method_call_graph
