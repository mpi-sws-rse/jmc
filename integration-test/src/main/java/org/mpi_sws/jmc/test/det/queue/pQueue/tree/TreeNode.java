package org.mpi_sws.jmc.test.det.queue.pQueue.tree;

import org.mpi_sws.jmc.test.det.queue.pQueue.Bin;

public class TreeNode {

    Counter counter;
    TreeNode parent, right, left;
    Bin bin;

    public boolean isLeaf() {
        return right == null;
    }
}
