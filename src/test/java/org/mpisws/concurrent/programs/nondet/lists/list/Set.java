package org.mpisws.concurrent.programs.nondet.lists.list;

import org.mpisws.util.concurrent.JMCInterruptException;

public interface Set {

    boolean add(Element i) throws JMCInterruptException;

    boolean remove(Element i) throws JMCInterruptException;

    boolean contains(Element i) throws JMCInterruptException;
}
