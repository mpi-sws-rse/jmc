package org.mpisws.concurrent.programs.det.pqueue.tree;

import org.mpisws.concurrent.programs.det.pqueue.Bin;

public class TreeNode {

    Counter counter;
    TreeNode parent, right, left;
    Bin bin;

    public boolean isLeaf() {
        return right == null;
    }
}
