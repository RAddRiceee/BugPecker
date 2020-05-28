# encoding=utf-8
import pandas as pd
import torch
import numpy as np
import os
import logging
from gensim.models.word2vec import Word2Vec
from sklearn.utils import shuffle
from model import Learner_Matcher
from torch.autograd import Variable


def min_max_scaler(col):
    return (col - col.min()) / (col.max() - col.min())


def get_batch(dataset, idx, bs):
    code, word, brr, bfr, cfs, expand_codes, labels = [], [], [], [], [], [], []

    tmp = dataset.iloc[idx: idx + bs]
    for _, item in tmp.iterrows():
        code.append(item['code_ids'])
        word.append(item['report_ids'])
        labels.append([item['label']])
        brr.append(item['brr'])
        bfr.append(item['bfr'])
        cfs.append(item['cfs'])
        expand_codes.append(item['expand_codes'])

    return code, word, torch.FloatTensor(brr), torch.FloatTensor(bfr), torch.FloatTensor(
        cfs), expand_codes, torch.FloatTensor(labels)


def init_model(use_gpu, config):
    batch_size = 64
    hidden_dim = 100
    encode_dim = 128
    labels = 1

    # word embeddings
    word2vec = Word2Vec.load(config.data_path + "word_w2v_128").wv
    word_max_tokens = word2vec.syn0.shape[0]
    word_embedding_dim = word2vec.syn0.shape[1]  # emd_dim
    word_embeddings = np.zeros((word_max_tokens + 1, word_embedding_dim), dtype='float32')
    word_embeddings[::word2vec.syn0.shape[0]] == word2vec.syn0

    # code embeddings
    code2vec = Word2Vec.load(config.data_path + "node_w2v_128").wv
    code_max_tokens = code2vec.syn0.shape[0]  # token number
    code_embedding_dim = code2vec.syn0.shape[1]  # emd_dim
    embeddings = np.zeros((code_max_tokens + 1, code_embedding_dim), dtype="float32")
    embeddings[:code2vec.syn0.shape[0]] = code2vec.syn0

    # 模型
    model = Learner_Matcher(embedding_dim=code_embedding_dim, hidden_dim=hidden_dim, vocab_size=code_max_tokens + 1,
                            encode_dim=encode_dim, label_size=labels, batch_size=batch_size, use_gpu=use_gpu,
                            pretrained_weight=embeddings, pretrained_word_embeddings=word_embeddings,
                            word_vocab_size=word_max_tokens + 1)

    return model


def train_model(args, config):
    logger = logging.getLogger('BugLoc')
    train_data = pd.read_pickle(config.data_path + 'train.pkl')

    # max min scale
    train_data['bfr'] = min_max_scaler(train_data['bfr'])
    train_data['brr'] = min_max_scaler(train_data['brr'])
    train_data['cfs'] = min_max_scaler(train_data['cfs'])
    train_data = shuffle(train_data)

    if args.gpu is not None:
        use_gpu = True
        os.environ["CUDA_DEVICE_ORDER"] = "PCI_BUS_ID"
        os.environ["CUDA_VISIBLE_DEVICES"] = args.gpu
    else:
        use_gpu = False

    batch_size = 64
    model = init_model(batch_size, use_gpu, config)
    if use_gpu:
        model.cuda()

    # 参数、优化器、损失函数
    parameters = model.parameters()
    optimizer = torch.optim.Adamax(parameters)
    loss_function = torch.nn.BCELoss()

    min_total_loss = 1000000
    epochs = 5
    for epoch in range(epochs):
        total_loss = 0
        logger.info('begin to train epoch {}'.format(epoch + 1))
        i = 0
        length = len(train_data)
        while i < length:
            batch = get_batch(train_data, i, batch_size, use_expand=True, use_cfs=True, use_bfr_and_brr=True)
            train1_inputs, train2_inputs, brr_inputs, bfr_inputs, cfs_inputs, expand_inputs, train_labels = batch
            i += batch_size
            if use_gpu:
                train1_inputs, train2_inputs, brr_inputs, bfr_inputs, cfs_inputs, expand_inputs, train_labels = \
                    train1_inputs, train2_inputs, brr_inputs.cuda(), bfr_inputs.cuda(), cfs_inputs.cuda(), expand_inputs, \
                    train_labels.cuda()

            model.zero_grad()
            model.batch_size = len(train_labels)
            model.hidden = model.init_hidden()
            output = model(train1_inputs, train2_inputs, brr_inputs, bfr_inputs, cfs_inputs, expand_inputs)
            loss = loss_function(output, Variable(train_labels))
            total_loss += loss
            loss.backward()
            optimizer.step()

        logger.info('Finished training epoch:{}'.format(epochs + 1))
        if total_loss <= min_total_loss:
            torch.save(model.state_dict(), config.model_path + 'model.pth')
            min_total_loss = total_loss


def test_model(args, config):
    logger = logging.getLogger('BugLoc')
    test_data = pd.read_pickle(config.data_path + 'test.pkl')

    # max min scale
    test_data['bfr'] = min_max_scaler(test_data['bfr'])
    test_data['brr'] = min_max_scaler(test_data['brr'])
    test_data['cfs'] = min_max_scaler(test_data['cfs'])

    batch_size = 64

    if args.gpu is not None:
        use_gpu = True
        os.environ["CUDA_DEVICE_ORDER"] = "PCI_BUS_ID"
        os.environ["CUDA_VISIBLE_DEVICES"] = args.gpu
    else:
        use_gpu = False

    model = init_model(batch_size, use_gpu, config)
    if use_gpu:
        model.cuda()

    model = model.load_state_dict(torch.load(f=config.model_path + 'model.pth'))

    i = 0
    buggy_rate = []
    length = len(test_data)
    while i < length:
        batch = get_batch(test_data, i, batch_size)
        code_inputs, word_inputs, brr_inputs, bfr_inputs, cfs_inputs, expand_inputs, _ = batch
        i += batch_size
        if use_gpu:
            code_inputs, word_inputs, brr_inputs, bfr_inputs, cfs_inputs, expand_inputs = \
                code_inputs, word_inputs, brr_inputs.cuda(), bfr_inputs.cuda(), cfs_inputs.cuda(), expand_inputs,

        model.batch_size = len(code_inputs)
        model.hidden = model.init_hidden()
        output = model(code_inputs, word_inputs, brr_inputs, bfr_inputs, cfs_inputs, expand_inputs)
        for rate in output:
            buggy_rate.append(rate[0].item())

    buggy_rate = pd.Series(buggy_rate, index=test_data.index)
    test_data['buggy_rate'] = buggy_rate
    test_data.to_pickle(config.data_path+'test_result.pkl')
    logger.info('finished! ')
