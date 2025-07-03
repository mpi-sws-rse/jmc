package org.mpisws.jmc.runtime;

/**
 * Exception thrown when a task is already paused.
 *
 * <p>This exception indicates that an operation was attempted on a task that is already in a paused
 * state, which is not allowed.
 */
public class TaskAlreadyPaused extends Exception {}
