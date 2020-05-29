import os
import logging
from tqdm import tqdm
import javalang
from utils.astnn_utils import *
from utils.data_utils import *
from gensim.models.word2vec import Word2Vec


def parse_source_code(source_codes):

    def parse_code(func):
        try:
            tokens = javalang.tokenizer.tokenize(func)
            parser = javalang.parser.Parser(tokens)
            tree = parser.parse_member_declaration()
        except javalang.parser.JavaSyntaxError:
            tree = ""  # 没法编译的method只能暂时删掉 好像很多是用了lambda表达式的
        return tree

    source_codes['tree'] = source_codes['code'].apply(parse_code)
    source_codes = source_codes[source_codes['tree'] != ""]
    return source_codes

    return source


def code_dictionary_and_embedding(trees, code_embedding_root):
    embedding_save_path = code_embedding_root + 'node_w2v_128'
    if os.path.exists(embedding_save_path):
        w2v = Word2Vec.load(embedding_save_path)
        return w2v
    temp_trees = pd.DataFrame(trees, copy=True)

    def trans_to_sequences(ast):
        sequence = []
        get_sequence(ast, sequence)
        return sequence

    corpus = temp_trees['tree'].apply(trans_to_sequences)

    w2v = Word2Vec(corpus, size=128, workers=16, sg=1, max_final_vocab=5000)
    w2v.save(embedding_save_path)
    return w2v


def generate_block_seqs(w2v, trees):
    word2vec = w2v.wv
    vocab = word2vec.vocab
    max_token = word2vec.syn0.shape[0]  # 字典的单词总量

    # 将tree 从token 转换为index 递归
    def tree_to_index(node):
        token = node.token
        # 如果token 在vocab中就转换为其对应的index 否则用占位符max_token代替
        result = [vocab[token].index if token in vocab else max_token]
        children = node.children
        if children is not None:
            for child in children:
                result.append(tree_to_index(child))
        return result

    def trans2seq(r):
        blocks = []
        get_blocks_v1(r, blocks)
        tree = []
        for b in blocks:
            btree = tree_to_index(b)
            tree.append(btree)
        if len(tree) == 0:
            tree = ''
        return tree

    block = pd.DataFrame(trees, copy=True)
    block['block'] = block['tree'].apply(trans2seq)
    block = block[block['block'] != '']
    return block


def generate_ast(args, config):
    logger = logging.getLogger('BugLoc')

    block_root = config.block_path
    json_root = config.json_path
    csv_path = config.project_csv

    data = pd.read_csv(csv_path).head(10)

    logger.info('generating ast for methods')
    for index, row in data.iterrows():
        commit_id = row['commit_id']
        bug_id = row['bug_id']
        block_save_path = block_root + commit_id + '.pkl'
        json_save_path = json_root + commit_id + '.json'

        if os.path.exists(block_save_path):
            continue

        if not os.path.exists(json_save_path):
            if args.project == 'aspectj':
                project_id = 'org.aspectj'
            elif args.project == 'swt':
                project_id = 'eclipse.platform.swt/' + str(bug_id)
            elif args.project == 'tomcat':
                project_id = 'tomcat'

            extract_method_body_for_specific_commit_version(project_id=project_id,
                                                            commit_id=commit_id,
                                                            local_path=json_save_path,
                                                            versionInfo_url=config.version_info_url)

        method_df = process_json_file(json_save_path, args.project)

        trees = parse_source_code(method_df)
        w2v = code_dictionary_and_embedding(trees=trees, code_embedding_root= config.data_path)
        blocks = generate_block_seqs(w2v=w2v, trees=trees)
        blocks = blocks['block']
        blocks.to_pickle(block_save_path)
