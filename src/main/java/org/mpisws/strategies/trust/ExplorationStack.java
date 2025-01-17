package org.mpisws.strategies.trust;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The exploration stack used in the Trust algorithm. The stack is used to keep track of the forward
 * and backward revisits.
 *
 * <p>The stack is a list of inner stacks. Each inner stack is created for a backward revisit.
 */
public class ExplorationStack {

    private final List<InnerStack> stack;

    /** Creates a new exploration stack. */
    public ExplorationStack() {
        this.stack = new ArrayList<>();
    }

    /**
     * Pushes an item onto the stack. If the stack is empty, a new InnerStack is created and added
     * to the stack. If the item is a backward revisit, a new InnerStack is created and added to the
     * stack.
     *
     * <p>If the item contains a graph then the graph of the inner stack is updated with this graph.
     * The reasoning is that since it is a DFS based exploration, The updated graph will only change
     * the relations of later events.
     *
     * @param item The item to push onto the stack
     */
    public void push(Item item) {
        if (this.stack.isEmpty()) {
            this.stack.add(new InnerStack(item.graph));
        }
        if (item.getType() == ItemType.BRR || item.getType() == ItemType.BWR) {
            this.stack.add(new InnerStack(null));
        }
        item.setInnerStackIndex(this.stack.size() - 1);
        this.stack.get(this.stack.size() - 1).push(item);

        ExecutionGraph g = item.getGraph();
        if (g != null) {
            this.stack.get(this.stack.size() - 1).setGraph(g);
        }
    }

    private void cleanStack() {
        int lastNonEmpty = this.stack.size() - 1;
        while (lastNonEmpty >= 0 && this.stack.get(lastNonEmpty).isEmpty()) {
            lastNonEmpty--;
        }
        if (lastNonEmpty < this.stack.size() - 1) {
            this.stack.subList(lastNonEmpty + 1, this.stack.size()).clear();
        }
    }

    /**
     * Pops an item from the stack. If the stack is empty, null is returned.
     *
     * @return The item popped from the stack
     */
    public Item pop() {
        // Note that we clean before popping. This was when an inner stack is popped to empty any
        // pushes will still go to that stack.
        // This is helpful when we pop a BCK item and then push a FRW item.
        // TODO: maybe there is a bug. We should think more carefully about this.
        cleanStack();
        if (this.stack.isEmpty()) {
            return null;
        }
        InnerStack innerStack = this.stack.get(this.stack.size() - 1);
        return innerStack.pop();
    }

    /**
     * Peeks at the item at the top of the stack. If the stack is empty, null is returned.
     *
     * @return The item at the top of the stack
     */
    public Item peek() {
        cleanStack();
        if (this.stack.isEmpty()) {
            return null;
        }
        InnerStack innerStack = this.stack.get(this.stack.size() - 1);
        return innerStack.peek();
    }

    /**
     * Gets the graph associated with the item.
     *
     * @param item The item
     * @return The graph associated with the item
     */
    public ExecutionGraph getGraph(Item item) {
        return this.stack.get(item.getInnerStackIndex()).getGraph();
    }

    /**
     * Checks if the stack is empty.
     *
     * @return True if the stack is empty, false otherwise
     */
    public boolean isEmpty() {
        cleanStack();
        return this.stack.isEmpty();
    }

    /** Clears the stack. */
    public void clear() {
        this.stack.clear();
    }

    /** Represents an item in the exploration stack. */
    public static class Item {
        private int innerStackIndex;
        // The type of the item
        private final ItemType type;
        // The two events that are part of the item
        // In the case of a forward revisit of
        // - (w ->(rf) r), event1 is r and event2 is w
        // - (w1 ->(co) w2), event1 is w1 and event2 is w2
        // In the case of a backward revisit, event1 is the write event and event2 is null
        private final ExecutionGraphNode event1;
        private final ExecutionGraphNode event2;

        // Graph is used only in the case of a backward revisit
        private final ExecutionGraph graph;

        private Item(
                ItemType type,
                ExecutionGraphNode one,
                ExecutionGraphNode two,
                ExecutionGraph graph) {
            this.type = type;
            this.event1 = one;
            this.event2 = two;
            this.graph = graph;
        }

        /**
         * Creates a forward revisit item for a read revisiting an alternative write.
         *
         * @param read The read event
         * @param write The write event
         * @param graph The graph to be used in the case of a backward revisit
         * @return The created item
         */
        public static Item forwardRW(
                ExecutionGraphNode read, ExecutionGraphNode write, ExecutionGraph graph) {
            return new Item(ItemType.FRW, read, write, graph);
        }

        /**
         * Creates a forward revisit item for a write revisiting an alternative concurrent write.
         *
         * @param one The first write event
         * @param two The second write event
         * @param graph The graph to be used in the case of a backward revisit
         * @return The created item
         */
        public static Item forwardWW(
                ExecutionGraphNode one, ExecutionGraphNode two, ExecutionGraph graph) {
            return new Item(ItemType.FWW, one, two, graph);
        }

        /**
         * Creates a backward revisit item for a write revisiting a read.
         *
         * @param one The write event
         * @param graph The graph to be used in the case of a backward revisit
         * @return The created item
         */
        public static Item backwardRevisit(ExecutionGraphNode one, ExecutionGraph graph) {
            return new Item(ItemType.BWR, one, null, graph);
        }

        /**
         * Creates a backward revisit item for a lock read revisiting another lock read.
         *
         * @param one The read event
         * @param two The revisited read
         * @param graph The graph to be used in the case of a backward revisit
         * @return The created item
         */
        public static Item lockBackwardRevisit(
                ExecutionGraphNode one, ExecutionGraphNode two, ExecutionGraph graph) {
            return new Item(ItemType.BRR, one, two, graph);
        }

        /**
         * Sets the inner stack index of the item.
         *
         * @param index The inner stack index
         */
        public void setInnerStackIndex(int index) {
            this.innerStackIndex = index;
        }

        /**
         * Gets the inner stack index of the item.
         *
         * @return The inner stack index
         */
        public int getInnerStackIndex() {
            return this.innerStackIndex;
        }

        /**
         * Gets the type of the item.
         *
         * @return The type of the item
         */
        public ItemType getType() {
            return type;
        }

        /**
         * Gets the first event of the item.
         *
         * @return The first event of the item
         */
        public ExecutionGraphNode getEvent1() {
            return event1;
        }

        /**
         * Gets the second event of the item.
         *
         * @return The second event of the item
         */
        public ExecutionGraphNode getEvent2() {
            return event2;
        }

        /**
         * Gets the graph associated with the item.
         *
         * @return The graph associated with the item
         */
        public ExecutionGraph getGraph() {
            return graph;
        }

        /**
         * Checks if the item is a forward revisit.
         *
         * @return True if the item is a forward revisit, false otherwise
         */
        public boolean isBackwardRevisit() {
            return (this.type == ItemType.BRR || this.type == ItemType.BWR) && this.graph != null;
        }
    }

    /** Represents the item type in the exploration stack. */
    public enum ItemType {
        // Forward revisit of read reading an alternative write
        FRW,
        // Forward revisit of write swapping with an alternative write
        FWW,
        // Backward revisit of write reading an alternative read
        BWR,
        // Backward revisit of read revisting an alternative read's read-from
        BRR,
    }

    /** Represents an inner stack in the exploration stack. */
    private static class InnerStack {
        private ExecutionGraph graph;
        private final ArrayDeque<Item> items;

        public InnerStack(ExecutionGraph graph) {
            this.graph = graph;
            this.items = new ArrayDeque<>();
        }

        public void push(Item item) {
            this.items.push(item);
        }

        public Item pop() {
            return this.items.pop();
        }

        public Item peek() {
            return this.items.peek();
        }

        public boolean isEmpty() {
            return this.items.isEmpty();
        }

        public ExecutionGraph getGraph() {
            return this.graph;
        }

        public void setGraph(ExecutionGraph graph) {
            this.graph = graph;
        }
    }
}
