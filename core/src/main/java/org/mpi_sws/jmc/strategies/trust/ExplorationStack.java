package org.mpi_sws.jmc.strategies.trust;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    private static final Logger LOGGER = LogManager.getLogger(ExplorationStack.class);
    /** The recursion tree: one {@link InnerStack} per backward-revisit level, deepest last. */
    private final List<InnerStack> stack;

    /**
     * Creates an empty exploration stack.
     */
    public ExplorationStack() {
        this.stack = new ArrayList<>();
    }

    /**
     * Creates an exploration stack with an initial level branching from the given graph.
     *
     * @param graph the graph of the first level.
     */
    public ExplorationStack(ExecutionGraph graph) {
        this.stack = new ArrayList<>();
        this.stack.add(new InnerStack(graph));
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
        LOGGER.debug("Adding item {} to stack", item.toString());
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

    /** Drops trailing empty inner stacks so the top level always has items (if any exist). */
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
        LOGGER.debug("Removing item {} from stack", peek());
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

    /**
     * Clears the stack.
     */
    public void clear() {
        this.stack.clear();
    }

    /**
     * Gets the size of the current inner stack.
     *
     * @return The size of the stack
     */
    public int size() {
        return this.stack.get(0).size();
    }

    /**
     * Sets the given proverId to the current inner stack (the latest inner stack).
     * @param proverId
     */
    public void setProverId(int proverId) {
        this.stack.get(this.stack.size() - 1).setProverId(proverId);
    }

    /**
     * Gets the proverId of the current inner stack (the latest inner stack).
     * @return The proverId of the current inner stack
     */
    public int getProverId() {
        return this.stack.get(this.stack.size() - 1).getProverId();
    }

    /**
     * Gets the total size of all inner stacks.
     *
     * @return The total size of the stack
     */
    public int totalSize() {
        int total = 0;
        for (InnerStack innerStack : this.stack) {
            total += innerStack.size();
        }
        return total;
    }

    /**
     * Logs the current state of the stack. This is a placeholder method for debugging purposes.
     */
    public void logStackState() {
        LOGGER.debug("Current stack state:");
        for (int i = 0; i < this.stack.size(); i++) {
            InnerStack innerStack = this.stack.get(i);
            for (Item item : innerStack.items) {
                LOGGER.debug("Inner Stack {}: {}", i, item);
            }
        }
    }

    /**
     * Represents an item in the exploration stack.
     */
    public static class Item {
        /** Index of the inner-stack level this item was pushed onto. */
        private int innerStackIndex;
        /** The kind of revisit (or control) this item represents. */
        private final ItemType type;
        /**
         * The first event involved. For a forward {@code (w ->(rf) r)} revisit this is {@code r};
         * for a forward {@code (w1 ->(co) w2)} revisit this is {@code w1}; for a backward revisit
         * this is the write event.
         */
        private final ExecutionGraphNode
                event1; // TODO: Since they are graph nodes, we must use a better name
        /**
         * The second event involved. The revisiting write for {@code FRW}, {@code w2} for {@code
         * FWW}, or {@code null} (e.g. for backward revisits).
         */
        private final ExecutionGraphNode
                event2; // TODO: Since they are graph nodes, we must use a better name

        /** Events to (re-)process after this revisit is applied (e.g. a removed lock write-exclusive). */
        private final List<Event> additionalEventsToProcess;

        /** The graph this item branches from; carried on forward items and the restricted clone on backward ones. */
        private ExecutionGraph graph;

        private Item(
                ItemType type,
                ExecutionGraphNode one,
                ExecutionGraphNode two,
                ExecutionGraph graph) {
            this.type = type;
            this.event1 = one;
            this.event2 = two;
            this.graph = graph;
            this.additionalEventsToProcess = new ArrayList<>();
        }

        /**
         * Creates an item of an arbitrary type. Do not use for normal exploration; it exists only as
         * a workaround for the estimator (testor), which builds items without knowing the type.
         *
         * @param type  the item type.
         * @param one   the first event.
         * @param two   the second event.
         * @param graph the associated graph.
         * @return the created item.
         */
        public static Item makeItem(
                ItemType type,
                ExecutionGraphNode one,
                ExecutionGraphNode two,
                ExecutionGraph graph) {
            return new Item(type, one, two, graph);
        }

        /**
         * Adds an event to be (re-)processed after this revisit is applied.
         *
         * @param event the additional event.
         */
        public void addAdditionalEvent(Event event) {
            this.additionalEventsToProcess.add(event);
        }

        /**
         * Returns the events to (re-)process after this revisit is applied.
         *
         * @return the list of additional events.
         */
        public List<Event> getAdditionalEventsToProcess() {
            return this.additionalEventsToProcess;
        }

        /**
         * Creates a forward revisit item for a read revisiting an alternative write.
         *
         * @param read  The read event
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
         * @param one   The first write event
         * @param two   The second write event
         * @param graph The graph to be used in the case of a backward revisit
         * @return The created item
         */
        public static Item forwardWW(
                ExecutionGraphNode one, ExecutionGraphNode two, ExecutionGraph graph) {
            return new Item(ItemType.FWW, one, two, graph);
        }

        /**
         * Creates a forward revisit item that places a (lock) write at the coherence-maximal
         * position.
         *
         * @param one   the write event.
         * @param graph the graph to branch from.
         * @return the created item.
         */
        public static Item forwardLW(ExecutionGraphNode one, ExecutionGraph graph) {
            return new Item(ItemType.FLW, one, null, graph);
        }

        /**
         * Creates a forward revisit item that flips a symbolic branch (ConDpor).
         *
         * @param one   the symbolic event.
         * @param graph the graph to branch from.
         * @return the created item.
         */
        public static Item symbolicForwardRevisit(
                ExecutionGraphNode one, ExecutionGraph graph) {
            return new Item(ItemType.FSYMB, one, null, graph);
        }

        /**
         * Creates an item that opens a new SMT prover context (ConDpor).
         *
         * @return the created item.
         */
        public static Item createProver() {
            return new Item(ItemType.CRP, null, null, null);
        }

        /**
         * Creates an item that closes an SMT prover context (ConDpor).
         *
         * @return the created item.
         */
        public static Item removeProver() {
            return new Item(ItemType.RMP, null, null, null);
        }

        /**
         * Creates a backward revisit item for a write revisiting a read.
         *
         * @param one   The write event
         * @param graph The graph to be used in the case of a backward revisit
         * @return The created item
         */
        public static Item backwardRevisit(ExecutionGraphNode one, ExecutionGraph graph) {
            return new Item(ItemType.BWR, one, null, graph);
        }

        /**
         * Creates an item that continues the current graph without a revisit.
         *
         * @return the created item.
         */
        public static Item continueCurrent() {
            return new Item(ItemType.CONT, null, null, null);
        }

        /**
         * Creates an item that continues exploration on the given graph without a revisit.
         *
         * @param graph the graph to continue from.
         * @return the created item.
         */
        public static Item continueCurrent(ExecutionGraph graph) {
            return new Item(ItemType.CONT, null, null, graph);
        }

        /**
         * Creates a {@code BRR} backward revisit item for a lock read revisiting another lock read.
         *
         * <p><b>Not currently used.</b> Lock-acquire backward revisits are created as ordinary {@code
         * BWR} items via {@link #backwardRevisit(ExecutionGraphNode, ExecutionGraph)} (from {@code
         * Algo.handleLockAcquireWrite}); this factory has no call site.
         *
         * @param one   The read event
         * @param two   The revisited read
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
         * Sets the graph this item branches from.
         *
         * @param graph the graph to associate.
         */
        public void setGraph(ExecutionGraph graph) {
            this.graph = graph;
        }

        /**
         * Checks if the item is a backward revisit ({@code BWR} or {@code BRR}) carrying a graph.
         *
         * @return true if the item is a backward revisit, false otherwise.
         */
        public boolean isBackwardRevisit() {
            return (this.type == ItemType.BRR || this.type == ItemType.BWR) && this.graph != null;
        }

        /**
         * Checks if the item removes a prover context (ConDpor).
         *
         * @return true if the item type is {@code RMP}.
         */
        public boolean isRemoveProver() {
            return this.type == ItemType.RMP;
        }

        /**
         * Checks if the item is a coherence-maximal (lock) write placement.
         *
         * @return true if the item type is {@code FLW}.
         */
        public boolean isLastWriteRevisit() {
            return this.type == ItemType.FLW;
        }

        /**
         * Checks if the item continues the current graph without a revisit.
         *
         * @return true if the item type is {@code CONT}.
         */
        public boolean isContinueCurrent() {
            return this.type == ItemType.CONT;
        }

        /**
         * Returns a {@code TYPE(:event1:event2)} rendering, omitting null events.
         *
         * @return the string form of the item.
         */
        @Override
        public String toString() {
            // return a string representation of the item type and the events. If the event2 is
            // null,
            // then just return the event1.
            return this.type
                    + "("
                    + (this.event1 != null ? ":" + this.event1.getEvent() : "")
                    + (this.event2 != null ? ":" + this.event2.getEvent() : "")
                    + ")";
        }
    }

    /**
     * Represents the item type in the exploration stack.
     */
    public enum ItemType {
        /** Forward revisit: a read reads from an alternative write. */
        FRW,
        /** Forward revisit: a write swaps coherence position with an alternative write. */
        FWW,
        /** Forward revisit: a (lock) write is placed at the coherence-maximal position. */
        FLW,
        /** Backward revisit: a write revisits an earlier read. */
        BWR,
        /**
         * Backward revisit: a lock read revisits another lock read's reads-from. Defined but not
         * currently produced — lock backward revisits reuse {@link #BWR} (see {@link
         * Item#lockBackwardRevisit}).
         */
        BRR,
        /** Continue the current execution without any change. */
        CONT,
        /** Forward revisit of a symbolic event to explore the other branch (ConDpor). */
        FSYMB,
        /** Create an SMT prover context (ConDpor). */
        CRP,
        /** Remove an SMT prover context (ConDpor). */
        RMP
    }

    /**
     * Represents an inner stack in the exploration stack.
     */
    private static class InnerStack {
        /** The graph all items at this level branch from. */
        private ExecutionGraph graph;
        /** The pending items at this level, explored LIFO (depth-first). */
        private final ArrayDeque<Item> items;
        /**
         * The prover id is used to identify the prover that is used to reason symbolically for the existing items
         * in the InnerStack object. If no symbolic reasoning is needed, the prover id is -1.
         */
        private int proverId;

        /**
         * Creates a level with the given graph and prover id.
         *
         * @param graph the graph for this level.
         * @param proverId the SMT prover id ({@code -1} if none).
         */
        public InnerStack(ExecutionGraph graph, int proverId) {
            this.graph = graph;
            this.items = new ArrayDeque<>();
            this.proverId = proverId;
        }

        /**
         * Creates a level with the given graph and the default prover id.
         *
         * @param graph the graph for this level.
         */
        public InnerStack(ExecutionGraph graph) {
            this(graph, 1);
        }

        /**
         * Pushes an item onto this level.
         *
         * @param item the item to push.
         */
        public void push(Item item) {
            this.items.push(item);
        }

        /**
         * Pops the top item of this level.
         *
         * @return the popped item.
         */
        public Item pop() {
            return this.items.pop();
        }

        /**
         * Peeks at the top item of this level.
         *
         * @return the top item, or {@code null} if empty.
         */
        public Item peek() {
            return this.items.peek();
        }

        /**
         * Returns whether this level has no items.
         *
         * @return true if empty.
         */
        public boolean isEmpty() {
            return this.items.isEmpty();
        }

        /**
         * Returns this level's graph.
         *
         * @return the graph.
         */
        public ExecutionGraph getGraph() {
            return this.graph;
        }

        /**
         * Sets this level's graph.
         *
         * @param graph the graph.
         */
        public void setGraph(ExecutionGraph graph) {
            this.graph = graph;
        }

        /**
         * Returns the number of items at this level.
         *
         * @return the item count.
         */
        public int size() {
            return this.items.size();
        }

        /**
         * Returns this level's SMT prover id.
         *
         * @return the prover id.
         */
        public int getProverId() {
            return proverId;
        }

        /**
         * Sets this level's SMT prover id.
         *
         * @param proverId the prover id.
         */
        public void setProverId(int proverId) {
            this.proverId = proverId;
        }
    }
}
