import os
import argparse
import logging
import pandas as pd
from pipeline import generate_ast
from config import Configuration
from prepare import prepare_data
from train import train_model, test_model
from evaluate import hit_at_k, mrr_metric, map_metric


def parse_args():
    parser = argparse.ArgumentParser('BugPecker: Locating Faulty Methods with Deep Learning on Revision Graphs')

    parser.add_argument('--project', default='tomcat', required=True,
                        choices=['swt', 'tomcat', 'aspectj'],
                        help='specify the java project to locate')
    parser.add_argument('--prepare', action='store_true',
                        help='prepare the data for training')
    parser.add_argument('--train', action='store_true',
                        help='train the model')
    parser.add_argument('--test', action='store_true',
                        help='test the model')
    parser.add_argument('--evaluate', action='store_true',
                        help='evaluate the model on test set')
    parser.add_argument('--gpu', type=str, default=None,
                        help='specify gpu device')

    parser.add_argument('--tr', type=float, default=0.7, help='specify the size of training  set')
    return parser.parse_args()


def prepare(args, config):
    logger = logging.getLogger('BugLoc')
    logger.info('Preparing data ...')
    generate_ast(args, config)
    prepare_data(args, config)
    logger.info('Done preparing data...')


def train(args, config):
    logger = logging.getLogger("BugLoc")
    logger.info('training ...')
    train_model(args, config)


def test(args, config):
    logger = logging.getLogger("BugLoc")
    logger.info('testing ...')
    test_model(args, config)


def evaluate(config):
    logger = logging.getLogger("BugLoc")
    test_result = pd.read_pickle(config.data_path + 'test_result.pkl')
    logger.info('evaluate the result ...')

    hit_1 = hit_at_k(test_result, 1)
    logger.info('hit@1 : {}'.format(hit_1))
    hit_5 = hit_at_k(test_result, 5)
    logger.info('hit@5 : {}'.format(hit_5))
    hit_10 = hit_at_k(test_result, 10)
    logger.info('hit@10 : {}'.format(hit_10))
    MAP = map_metric(test)
    logger.info('MAP : {}'.format(MAP))
    MRR = mrr_metric(test)
    logger.info('MRR : {}'.format(MRR))


def run():
    args = parse_args()
    logger = logging.getLogger("BugLoc")
    logger.setLevel(logging.INFO)
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')

    config = Configuration(args.project)

    if config.log_path:
        file_handler = logging.FileHandler(config.log_path)
        file_handler.setLevel(logging.INFO)
        file_handler.setFormatter(formatter)
        logger.addHandler(file_handler)
    else:
        console_handler = logging.StreamHandler()
        console_handler.setLevel(logging.INFO)
        console_handler.setFormatter(formatter)
        logger.addHandler(console_handler)
    logger.info('---------------------------------------')
    logger.info('Current project name : {}'.format(args.project))
    logger.info('Running with args : {}'.format(args))

    if args.prepare:
        prepare(args, config)
    if args.train:
        train(args, config)
    if args.evaluate:
        evaluate(config)
    if args.test:
        test(args, config)


if __name__ == '__main__':
    run()
