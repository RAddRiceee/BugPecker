import gc
import json
import requests
import pandas as pd
from datetime import datetime


def strp2date(old_time):
    new_time = datetime.strptime(old_time, "%Y-%m-%d %H:%M:%S")
    return new_time


def process_report_text(summary, description):
    if not isinstance(summary, str):
        summary = ''
    if not isinstance(description, str):
        description = ''

    def split_and_clear(text):
        import re
        rstr = r"[\#\!\.\{\}\;\_\-\[\]\=\(\)\,\/\\\:\*\?\"\<\>\|\' ']"
        clear_text = re.sub(rstr, " ", text)
        tokens = clear_text.split()
        return tokens

    smy = split_and_clear(summary)
    des = split_and_clear(description)
    return smy + des


def extract_method_body_for_specific_commit_version(project_id, commit_id, local_path, versionInfo_url):
    data = {
        "projectID": project_id,
        "commitId": commit_id}
    headers = {
        "content-type": "application/json"}
    response = requests.post(
        url=versionInfo_url,
        headers=headers,
        data=json.dumps(data))

    content = response.content.decode('utf-8')
    content = json.loads(content)
    with open(local_path, 'w') as f:
        f.write(json.dumps(content))


def process_method_uri(method_uri_raw, method_pos):
    method_pos = method_pos.split('.java')[0].replace('/', '.')
    import re
    p1 = re.compile(r'[(](.*?)[)]', re.S)  # 最小匹配
    method_paras = re.findall(p1, method_uri_raw)[0]
    method_paras_split = method_paras.split(',')  # 方法的所有参数
    if method_paras_split[0] != '':  # 该方法有参数
        paras_short = ''
        for p in method_paras_split:
            para_type = p.split('.')[-1]
            paras_short = paras_short + para_type + ','
        paras_short = paras_short.rstrip(',')
        method_uri = method_uri_raw.replace(method_paras, paras_short)
    else:
        method_uri = method_uri_raw
    method_signature = method_uri.split('-')[1]
    method_ = method_pos + '-' + method_signature

    return method_


def process_json_file(local_json_path, project_name):
    with open(local_json_path, 'r', encoding='utf-8')as f:
        lines = f.read()
    f.close()
    file = json.loads(lines)
    del lines
    gc.collect()
    # file = json.loads(open(local_json_path).read())
    # class_list = file['classList']
    # method_sub_graph_map = file['methodSubGraphMap']
    # variable_map = file['variableMap']

    method_map = file['methodMap']
    method_dict = {}
    for index, value in method_map.items():  # methodMap 存了每一个类
        for method in value:  # value 是list 里面存了每一个方法的信息
            method_uri_raw = method['propertys']['uri'].split('#')[0]
            method_pos = method['propertys']['position']
            # swt的method_pos多了一个版本号出来 在项目名称后面
            # swt 特殊处理
            if project_name == 'SWT':
                method_pos_list = method_pos.split('/')
                method_pos = '.'.join(method_pos_list[i] for i in range(2, len(method_pos_list)))
                method_pos = method_pos_list[0] + '.' + method_pos

            method_uri = process_method_uri(method_uri_raw, method_pos)
            source_code = method['propertys']['sourceCode']
            method_dict[method_uri] = source_code

    method_df = pd.DataFrame.from_dict(method_dict, orient='index', columns=['code'])
    return method_df
