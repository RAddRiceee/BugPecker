import sys
import warnings
from javalang.ast import Node
from tree import ASTNode, BlockNode

warnings.filterwarnings("ignore")
sys.setrecursionlimit(10000)


def get_token(node):
    if isinstance(node, str):
        if node.find('*') >= 0:
            token = 'Description'
        else:
            token = node
    elif isinstance(node, set):
        token = 'Modifier'  # node.pop()
    elif isinstance(node, Node):
        token = node.__class__.__name__
    else:
        token = ''

    return token


def get_children(root):
    if isinstance(root, Node):
        children = root.children
    elif isinstance(root, set):
        children = list(root)
    else:
        children = []

    def expand(nested_list):
        for item in nested_list:
            if isinstance(item, list):
                for sub_item in expand(item):
                    yield sub_item
            elif item:
                yield item

    return list(expand(children))


def get_sequence(node, sequence):
    token, children = get_token(node), get_children(node)
    if token != 'Description':
        sequence.append(token)

    for child in children:
        get_sequence(child, sequence)

    if token in ['ForStatement', 'WhileStatement', 'DoStatement', 'SwitchStatement', 'IfStatement']:
        sequence.append('End')


def get_blocks_v1(node, block_seq):
    name, children = get_token(node), get_children(node)

    logic = ['SwitchStatement', 'IfStatement', 'ForStatement', 'WhileStatement', 'DoStatement']

    if name in ['MethodDeclaration', 'ConstructorDeclaration']:
        block_seq.append(BlockNode(node))
        try:
            body = node.body

            # body 可能为空
            if body is None:
                body = []
            for child in body:

                if get_token(child) not in logic and not hasattr(child, 'block'):
                    block_seq.append(BlockNode(child))
                else:
                    get_blocks_v1(child, block_seq)
        except:
            # TODO some error to be fixed
            pass

    elif name in logic:
        block_seq.append(BlockNode(node))
        for child in children[1:]:
            token = get_token(child)
            if not hasattr(node, 'block') and token not in logic + ['BlockStatement']:
                block_seq.append(BlockNode(child))
            else:
                get_blocks_v1(child, block_seq)
            block_seq.append(BlockNode('End'))
    elif name is 'BlockStatement' or hasattr(node, 'block'):
        block_seq.append(BlockNode(name))
        for child in children:
            if get_token(child) not in logic:
                block_seq.append(BlockNode(child))
            else:
                get_blocks_v1(child, block_seq)
    else:
        for child in children:
            get_blocks_v1(child, block_seq)

