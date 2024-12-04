package org.mpisws.strategies.trust;

import java.util.ArrayDeque;

public class ExplorationStack {

    private final ArrayDeque<Item> stack;

    public ExplorationStack() {
        this.stack = new ArrayDeque<>();
    }

    public void push(Item item) {
        this.stack.push(item);
    }

    public Item pop() {
        return this.stack.pop();
    }

    public boolean isEmpty() {
        return this.stack.isEmpty();
    }

    public static class Item {
        private final ItemType type;
        private final ExecutionGraphNode event1;
        private final ExecutionGraphNode event2;

        private final ExecutionGraph graph;

        public Item(ItemType type, ExecutionGraphNode one, ExecutionGraphNode two) {
            this.type = type;
            this.event1 = one;
            this.event2 = two;
            this.graph = null;
        }

        public Item(ItemType type, ExecutionGraphNode one, ExecutionGraphNode two, ExecutionGraph graph) {
            this.type = type;
            this.event1 = one;
            this.event2 = two;
            this.graph = graph;
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

        public boolean isBackwardRevisit() {
            return this.type == ItemType.BCK && this.graph != null;
        }
    }

    public enum ItemType {
        FRW,
        BCK,
    }
}
