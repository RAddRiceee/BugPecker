import torch.nn as nn
import torch.nn.functional as F
import torch
from torch.autograd import Variable


class BatchTreeEncoder(nn.Module):
    def __init__(self, vocab_size, embedding_dim, encode_dim, batch_size, use_gpu, pretrained_weight=None):
        super(BatchTreeEncoder, self).__init__()
        self.embedding = nn.Embedding(vocab_size, embedding_dim)
        self.embedding_dim = embedding_dim
        self.encode_dim = encode_dim
        self.W_c = nn.Linear(embedding_dim, encode_dim)
        self.activation = F.relu
        self.stop = -1
        self.batch_size = batch_size
        self.use_gpu = use_gpu
        self.node_list = []
        self.th = torch.cuda if use_gpu else torch
        self.batch_node = None
        self.max_index = vocab_size
        # pretrained  embedding
        if pretrained_weight is not None:
            self.embedding.weight.data.copy_(torch.from_numpy(pretrained_weight))
            # self.embedding.weight.requires_grad = False

    def create_tensor(self, tensor):
        if self.use_gpu:
            return tensor.cuda()
        return tensor

    # @torchsnooper.snoop()
    def traverse_mul(self, node, batch_index):
        size = len(node)
        if not size:
            return None
        batch_current = self.create_tensor(Variable(torch.zeros(size, self.embedding_dim)))

        index, children_index = [], []
        current_node, children = [], []
        for i in range(size):
            # if node[i][0] is not -1:
            index.append(i)
            current_node.append(node[i][0])
            temp = node[i][1:]
            c_num = len(temp)
            for j in range(c_num):
                if temp[j][0] is not -1:
                    if len(children_index) <= j:
                        children_index.append([i])
                        children.append([temp[j]])
                    else:
                        children_index[j].append(i)
                        children[j].append(temp[j])
        # else:
        #     batch_index[i] = -1

        batch_current = self.W_c(batch_current.index_copy(0, Variable(self.th.LongTensor(index)),
                                                          self.embedding(Variable(self.th.LongTensor(current_node)))))

        for c in range(len(children)):
            zeros = self.create_tensor(Variable(torch.zeros(size, self.encode_dim)))
            batch_children_index = [batch_index[i] for i in children_index[c]]
            tree = self.traverse_mul(children[c], batch_children_index)
            if tree is not None:
                batch_current += zeros.index_copy(0, Variable(self.th.LongTensor(children_index[c])), tree)
        # batch_index = [i for i in batch_index if i is not -1]
        b_in = Variable(self.th.LongTensor(batch_index))
        self.node_list.append(self.batch_node.index_copy(0, b_in, batch_current))
        return batch_current

    def forward(self, x, bs):
        self.batch_size = bs
        self.batch_node = self.create_tensor(Variable(torch.zeros(self.batch_size, self.encode_dim)))
        self.node_list = []
        self.traverse_mul(x, list(range(self.batch_size)))
        self.node_list = torch.stack(self.node_list)
        return torch.max(self.node_list, 0)[0]


class Learner_Matcher(nn.Module):
    def __init__(self, embedding_dim, hidden_dim, vocab_size, encode_dim, label_size, batch_size, use_gpu,
                 pretrained_weight, pretrained_word_embeddings, word_vocab_size):
        super(Learner_Matcher, self).__init__()
        self.stop = [vocab_size - 1]
        self.hidden_dim = hidden_dim
        self.num_layers = 2
        self.gpu = use_gpu
        self.batch_size = batch_size
        self.vocab_size = vocab_size
        self.embedding_dim = embedding_dim
        self.encode_dim = encode_dim
        self.label_size = label_size
        self.pretrained_weight = pretrained_weight
        self.encoder = BatchTreeEncoder(self.vocab_size, self.embedding_dim, self.encode_dim, self.batch_size, self.gpu, self.pretrained_weight)
        self.expand_encoder = BatchTreeEncoder(self.vocab_size, self.embedding_dim, self.encode_dim, self.batch_size, self.gpu, self.pretrained_weight)

        self.root2label = nn.Linear(self.encode_dim, self.label_size)
        # gru
        self.bigru = nn.GRU(self.encode_dim, self.hidden_dim, num_layers=self.num_layers, bidirectional=True,
                            batch_first=True)
        # linear
        self.hidden2label = nn.Linear(self.hidden_dim * 2, self.label_size)
        # hidden
        self.hidden = self.init_hidden()

        self.dropout = nn.Dropout(0.2)
        self.mlp = nn.Sequential(nn.Linear(4, self.hidden_dim),
                                 nn.ReLU(),
                                 nn.Linear(self.hidden_dim, 1))

        # word parameter
        self.pretrained_word_embeddings = pretrained_word_embeddings
        self.word_vocab_size = word_vocab_size
        self.word_embeddings = nn.Embedding(self.word_vocab_size, self.embedding_dim)
        if self.pretrained_word_embeddings is not None:
            self.word_embeddings.weight.data.copy_(torch.from_numpy(self.pretrained_word_embeddings))

    def init_hidden(self):
        if self.gpu is True:
            if isinstance(self.bigru, nn.LSTM):
                h0 = Variable(torch.zeros(self.num_layers * 2, self.batch_size, self.hidden_dim).cuda())
                c0 = Variable(torch.zeros(self.num_layers * 2, self.batch_size, self.hidden_dim).cuda())
                return h0, c0
            return Variable(torch.zeros(self.num_layers * 2, self.batch_size, self.hidden_dim)).cuda()
        else:
            return Variable(torch.zeros(self.num_layers * 2, self.batch_size, self.hidden_dim))

    def hidden_for_method_expand(self, batch_size):
        if self.gpu is True:
            if isinstance(self.bigru, nn.LSTM):
                h0 = Variable(torch.zeros(self.num_layers * 2, batch_size, self.hidden_dim).cuda())
                c0 = Variable(torch.zeros(self.num_layers * 2, batch_size, self.hidden_dim).cuda())
                return h0, c0
            return Variable(torch.zeros(self.num_layers * 2, batch_size, self.hidden_dim)).cuda()
        else:
            return Variable(torch.zeros(self.num_layers * 2, batch_size, self.hidden_dim))

    def get_zeros(self, num):
        zeros = Variable(torch.zeros(num, self.encode_dim))
        if self.gpu:
            return zeros.cuda()
        return zeros

    def report_encode(self, y):

        lens = [len(item) for item in y]
        max_len = max(lens)
        encodes = []
        for i in range(self.batch_size):
            for j in range(lens[i]):
                encodes.append(y[i][j])
            diff = max_len - lens[i]
            for x in range(diff):
                encodes.append([0])  # PADDED

        if self.gpu:
            encodes = Variable(torch.LongTensor(encodes)).cuda()
        else:
            encodes = Variable(torch.LongTensor(encodes))

        encodes = self.word_embeddings(encodes)
        encodes = encodes.view(self.batch_size, max_len, -1)  # view = reshape 1728,1,128->32 54 128
        gru_out, hidden = self.bigru(encodes, self.hidden)
        gru_out = torch.transpose(gru_out, 1, 2)  # transpose交换一个tensor的两个维度
        gru_out = F.max_pool1d(gru_out, gru_out.size(2)).squeeze(2)
        return gru_out

    def encode(self, x, cur_size):
        # x 是一个batch 的 code
        lens = [len(item) for item in x]
        max_len = max(lens)  # 一个batch最大的code长度
        encodes = []
        for i in range(cur_size):
            for j in range(lens[i]):
                encodes.append(x[i][j])

        encodes = self.encoder(encodes, sum(lens))  # tree coding

        # padding each method of x to uniform length
        seq, start, end = [], 0, 0
        for i in range(cur_size):
            end += lens[i]
            if max_len - lens[i]:
                seq.append(self.get_zeros(max_len - lens[i]))
            seq.append(encodes[start:end])
            start = end
        encodes = torch.cat(seq)  # seq:[tensor(1,2),tensor(1,2)]-> encodes:tensor([1,2,1,2]) # shape:[4,128]
        encodes = encodes.view(cur_size, max_len, -1)
        # shape:[2,2,128] = [batch_size, size of each append code, embedding dim]
        # gru_out, hidden = self.bigru(encodes, self.hidden)
        gru_out, hidden = self.bigru(encodes, self.hidden_for_method_expand(cur_size))
        # shape:[2,2,200]=[batch_size, size of each append code, 2*hidden size]
        gru_out = torch.transpose(gru_out, 1, 2)
        # 交换维度, shape:[2,200,2]=[batch_size, 2*hidden size, len of each append code]
        # pooling
        # print(F.max_pool1d(gru_out,gru_out.size(2)).size())
        gru_out = F.max_pool1d(gru_out, gru_out.size(2)).squeeze(2)
        # gru_out.size(2)= len of each append code
        # max_pool1d() [2,200,2]->[2,200,1]
        # squeeze():[2,200,1]->[2,200]
        return gru_out

    def forward(self, code,  word,  brr, bfr, cfs, expand_set):

        word_vec = self.report_encode(word)
        code_vec = self.encode(code, self.batch_size)

        code_vec_expanded = torch.Tensor(1, self.hidden_dim*2)
        if self.gpu:
            code_vec_expanded = code_vec_expanded.cuda()
        for i in range(self.batch_size):
            expand_sub_set = expand_set[i]
            sub_len = len(expand_sub_set)
            if sub_len > 0:  # 每一个method 对应的expand_set
                expand_vec_set = self.encode(expand_sub_set, sub_len)
                # soft attention
                expand_vec_set = expand_vec_set.unsqueeze(1)
                method_vec = code_vec[i].unsqueeze(-1)
                batch_method_vec = method_vec.repeat(sub_len, 1, 1)
                d = torch.bmm(expand_vec_set, batch_method_vec)
                attn = F.softmax(d, dim=0)

                expand_method = torch.bmm(attn, expand_vec_set).squeeze(1)
                expand_method_vec = expand_method[0]
                for s in range(1, len(expand_method)):
                    expand_method_vec += expand_method[s]

                # gru 决定需要使用扩充method的哪些内容
                method_vec = method_vec.squeeze(1).unsqueeze(0).repeat(2, 1)
                expand_method_vec = expand_method_vec.unsqueeze(0).repeat(2, 1)
                if self.gpu:
                    expand_method_vec = expand_method_vec.cuda()
                gru_cell = nn.GRUCell(input_size=self.hidden_dim*2, hidden_size=self.hidden_dim*2)  # bigru 所以需要*2
                if self.gpu:
                    gru_cell = gru_cell.cuda()
                expanded_method, hn = gru_cell(expand_method_vec, method_vec)
            else:
                expanded_method = code_vec[i]
            if i == 0:
                code_vec_expanded = expanded_method.unsqueeze(0)
            else:
                code_vec_expanded = torch.cat((code_vec_expanded, expanded_method.unsqueeze(0)), 0)
        # code_vec_expanded = self.

        abs_dist = torch.abs(torch.add(code_vec_expanded, word_vec))
        # abs_dist = torch.abs(torch.add(code_vec, word_vec))
        y = torch.sigmoid(self.hidden2label(abs_dist))

        # MLP
        feature_input = []
        for i in range(self.batch_size):
            rate = y[i].item()
            brr_item = brr[i].item()
            bfr_item = bfr[i].item()
            cfs_item = cfs[i].item()
            feature_input.append([rate, brr_item, bfr_item, cfs_item])

        feature_input = Variable(torch.LongTensor(feature_input)).float()

        if self.gpu:
            feature_input = feature_input.cuda()

        result = self.mlp(feature_input)

        # 超出0.0-1.0 BCELoss函数有问题
        result[result < 0.0] = 0.0
        result[result > 1.0] = 1.0

        return result
