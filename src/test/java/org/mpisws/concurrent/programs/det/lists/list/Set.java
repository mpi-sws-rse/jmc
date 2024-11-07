package org.mpisws.concurrent.programs.det.lists.list;

import org.mpisws.symbolic.AbstractInteger;
import org.mpisws.util.concurrent.JMCInterruptException;

public interface Set {

    boolean add(AbstractInteger i) throws JMCInterruptException;

    boolean remove(AbstractInteger i) throws JMCInterruptException;

    boolean contains(AbstractInteger i) throws JMCInterruptException;
}
