def hit_at_k(test_result, k):
    hit_nums = 0
    no_truth_num = 0
    bug_ids = test_result.drop_duplicates(subset=['bug_id'], keep='first')['bug_id'].to_list()
    print('total bug report number:{}'.format(len(bug_ids)))
    flag = 1
    for bid in bug_ids:
        temp = test_result[test_result['bug_id'] == bid]
        if flag == 1:
            print('number of methods:', len(temp))
            flag = 0
        temp_sorted = temp.sort_values('buggy_rate', ascending=False)
        temp_sorted = temp_sorted.reset_index()
        truth_ranked = temp_sorted[temp_sorted['label'] == 1].index.tolist()
        if len(truth_ranked) == 0:
            no_truth_num += 1
            continue
        for i in truth_ranked:
            if (i + 1) <= k:  # index从0开始的
                hit_nums += 1
                break
    hit_rate = hit_nums / (len(bug_ids) - no_truth_num)

    return hit_rate


def map_metric(test_result):
    map_sum = 0
    no_truth_num = 0
    bug_ids = test_result.drop_duplicates(subset=['bug_id'], keep='first')['bug_id'].to_list()
    for bug_id in bug_ids:
        p = 0
        temp = test_result[test_result['bug_id'] == bug_id]
        temp_sorted = temp.sort_values('buggy_rate', ascending=False)
        temp_sorted = temp_sorted.reset_index()
        truth_ranked = temp_sorted[temp_sorted['label'] == 1].index.tolist()
        if len(truth_ranked) == 0:
            no_truth_num += 1
            continue
        length = len(truth_ranked)
        for j in range(0, length):
            p = p + ((j+1) / (truth_ranked[j]+1))
        ap = p / length
        map_sum += ap
    map_result = map_sum / (len(bug_ids) - no_truth_num)
    return map_result


def mrr_metric(test_result):
    mrr_sum = 0
    no_truth_num = 0
    bug_ids = test_result.drop_duplicates(subset=['bug_id'], keep='first')['bug_id'].to_list()
    for bug_id in bug_ids:
        temp = test_result[test_result['bug_id'] == bug_id]
        temp_sorted = temp.sort_values('buggy_rate', ascending=False)
        temp_sorted = temp_sorted.reset_index()
        truth_ranked = temp_sorted[temp_sorted['label'] == 1].index.tolist()
        if len(truth_ranked) == 0:
            no_truth_num += 1
            continue
        best_rank = truth_ranked[0]
        reciprocal_rank = 1 / (best_rank + 1)
        mrr_sum += reciprocal_rank
    mrr_result = mrr_sum / (len(bug_ids) - no_truth_num)
    return mrr_result
