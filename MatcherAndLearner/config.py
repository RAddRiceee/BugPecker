import os
import logging


class Configuration:
    def __init__(self, project):
        self.logger = logging.getLogger('BugLoc')
        self.log_path = './BugLoc.log'
        self.data_path = './dataset/'
        self.revision_analyzer_url = ''
        self.revision_analyzer_root = ''

        self.project_csv = os.path.join(self.data_path, project + '.csv')

        if not os.path.exists(self.project_csv):
            self.logger.info('error dataset path')
            exit()

        # output path
        self.output_path = os.path.dirname('./output/')
        if not os.path.exists(self.output_path):
            os.mkdir(self.output_path)

        # dir for specific project
        self.project_root = os.path.join(self.output_path, project)
        if not os.path.exists(self.project_root):
            os.mkdir(self.project_root)

        # path to save model
        self.model_path = os.path.join(self.project_root, 'model/')
        if not os.path.exists(self.model_path):
            os.mkdir(self.model_path)

        # path to save prepared data
        self.data_path = os.path.join(self.project_root, 'data/')
        if not os.path.exists(self.data_path):
            os.mkdir(self.data_path)

        # path to save generated ASTNN blocks
        self.block_path = os.path.join(self.project_root, 'block/')
        if not os.path.exists(self.block_path):
            os.mkdir(self.block_path)

        # path to save extracted method (json type)
        self.json_path = os.path.join(self.project_root, 'json/')
        if not os.path.exists(self.json_path):
            os.mkdir(self.json_path)

        # path to load sim relations from revision analyzer (txt type)
        self.sim_path = os.path.join(self.project_root, 'sim/')
        if not os.path.exists(self.sim_path):
            os.mkdir(self.sim_path)
