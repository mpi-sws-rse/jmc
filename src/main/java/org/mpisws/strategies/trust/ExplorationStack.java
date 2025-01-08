package org.mpisws.strategies.trust;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

// TODO: Should move this to a stack of stacks. Each outer stack refers to a specific graph.
public class ExplorationStack {

    private final List<InnerStack> stack;

    public ExplorationStack() {
        this.stack = new ArrayList<>();
    }

    /*
     * Pushes an item onto the stack. If the stack is empty, a new InnerStack is created and added to the stack.
     * If the item is a backward revisit, a new InnerStack is created and added to the stack.
     *
     * <p> If the item contains a graph then the graph of the inner stack is updated with this graph.
     * The reasoning is that since it is a DFS based exploration, The updated graph will only change
     * the relations of later events.
     *
     * @param item The item to push onto the stack
     */
    public void push(Item item) {
        if (this.stack.isEmpty()) {
            this.stack.add(new InnerStack(item.graph));
        }
        if (item.getType() == ItemType.BCK) {
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

    /*
     * Pops an item from the stack. If the stack is empty, null is returned.
     *
     * @return The item popped from the stack
     */
    public Item pop() {
        cleanStack();
        if (this.stack.isEmpty()) {
            return null;
        }
        InnerStack innerStack = this.stack.get(this.stack.size() - 1);
        return innerStack.pop();
    }

    public Item peek() {
        cleanStack();
        if (this.stack.isEmpty()) {
            return null;
        }
        InnerStack innerStack = this.stack.get(this.stack.size() - 1);
        return innerStack.peek();
    }

    public ExecutionGraph getGraph(Item item) {
        return this.stack.get(item.getInnerStackIndex()).getGraph();
    }

    public boolean isEmpty() {
        return this.stack.isEmpty();
    }

    public static class Item {
        private int innerStackIndex;

        private final ItemType type;
        // The two events that are part of the item
        // In the case of a forward revisit of
        // - (w ->(rf) r), event1 is w and event2 is r
        // - (w1 ->(co) w2), event1 is w1 and event2 is w2
        // In the case of a backward revisit, event1 is the write event and event2 is null
        private final ExecutionGraphNode event1;
        private final ExecutionGraphNode event2;

        // Graph is used only in the case of a backward revisit
        private final ExecutionGraph graph;

        public Item(ItemType type, ExecutionGraphNode one, ExecutionGraphNode two) {
            this.type = type;
            this.event1 = one;
            this.event2 = two;
            this.graph = null;
        }

        public Item(ItemType type, ExecutionGraphNode one, ExecutionGraph graph) {
            this.type = type;
            this.event1 = one;
            this.event2 = null;
            this.graph = graph;
        }

        public void setInnerStackIndex(int index) {
            this.innerStackIndex = index;
        }

        public int getInnerStackIndex() {
            return this.innerStackIndex;
        }

        public ItemType getType() {
            return type;
        }

        public ExecutionGraphNode getEvent1() {
            return event1;
        }

        public ExecutionGraphNode getEvent2() {
            return event2;
        }

        public ExecutionGraph getGraph() {
            return graph;
        }

        public boolean isBackwardRevisit() {
            return this.type == ItemType.BCK && this.graph != null;
        }
    }

    public enum ItemType {
        FRW,
        FWW,
        BCK,
    }

    private static class InnerStack {
        private ExecutionGraph graph;
        private ArrayDeque<Item> items;

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
