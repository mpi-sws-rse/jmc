package org.mpisws.strategies.trust;

import org.mpisws.runtime.RuntimeEvent;

import java.util.Map;

/**
 * Represents a node in the execution graph.
 */
public class ExecutionGraphNode {
    // The event that this node represents.
    private final RuntimeEvent event;
    // The attributes of this node.
    private Map<String, Object> attributes;

    /**
     * Constructs a new {@link ExecutionGraphNode} with the given event.
     *
     * @param event The {@link RuntimeEvent} that this node represents.
     */
    public ExecutionGraphNode(RuntimeEvent event) {
        this.event = event;
    }

    /**
     * Updates the attributes of this node.
     *
     * @param attributes The new attributes of this node.
     */
    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Adds an attribute to this node.
     *
     * @param key The key of the attribute.
     * @param value The value of the attribute.
     */
    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Returns the attribute with the given key.
     *
     * @param key The key of the attribute.
     * @param <T> The type of the attribute.
     * @return The attribute with the given key.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * Returns the {@link RuntimeEvent} that this node represents.
     *
     * @return The event that this node represents.
     */
    public RuntimeEvent getEvent() {
        return event;
    }
}
