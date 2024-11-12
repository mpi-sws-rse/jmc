package org.mpisws.concurrent.programs.det.pqueue.tree;

import org.mpisws.concurrent.programs.det.pqueue.LockBasedBin;
import org.mpisws.concurrent.programs.det.pqueue.PQueue;
import org.mpisws.util.concurrent.JMCInterruptException;

import java.util.ArrayList;
import java.util.List;

public class LockBasedTree implements PQueue {

    int range;
    List<TreeNode> leaves;
    TreeNode root;

    public LockBasedTree(int logRange) {
        range = (1 << logRange);
        leaves = new ArrayList<>(range);
        root = buildTree(logRange, 0);
    }

    private TreeNode buildTree(int height, int index) {
        TreeNode node = new TreeNode();
        if (height == 0) {
            node.bin = new LockBasedBin();
            leaves.add(node);
        } else {
            node.left = buildTree(height - 1, index * 2);
            node.right = buildTree(height - 1, index * 2 + 1);
            node.left.parent = node;
            node.right.parent = node;
        }
        node.counter = new Counter();
        return node;
    }

    public void add(int item, int score) throws JMCInterruptException {
        TreeNode node = leaves.get(score);
        node.bin.put(item);
        while (node != root) {
            TreeNode parent = node.parent;
            if (node == parent.left) {
                parent.counter.getAndIncrement();
            }
            node = parent;
        }
    }

    public int removeMin() throws JMCInterruptException {
        TreeNode node = root;
        while (!node.isLeaf()) {
            if (node.counter.boundedGetAndIncrement() > 0) {
                node = node.left;
            } else {
                node = node.right;
            }
        }
        return node.bin.get();
    }
}
