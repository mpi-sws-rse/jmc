package org.mpi_sws.jmc.symbolic.bool;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;

public class SymbolicBoolean extends AbstractBoolean {
    private String name;
    private JmcBooleanFormula eval;
    private boolean value;

    private SymbolicBoolean() {
        String[] parts = this.toString().split("@");
        this.name = "SymbolicBoolean@" + parts[parts.length - 1];
        write();
    }

    private SymbolicBoolean(String name, boolean value) {
        this.name = name;
        this.setValue(value);
    }

    public SymbolicBoolean(String name) {
        Long id = JmcRuntime.currentTask();
        this.name = "SymbolicBoolean@" + name + "_" + id;
        this.write();
    }

    public void assign(JmcBooleanFormula expression) {
        write(expression);
    }

    public void assign(SymbolicBoolean symbolicBoolean) {
        write(symbolicBoolean);
    }

    @Override
    public SymbolicBoolean clone() {
        SymbolicBoolean copy = new SymbolicBoolean(name, getValue());
        if (eval != null) {
            JmcBooleanFormula expressionCopy = new JmcBooleanFormula();
            expressionCopy.setFormula(eval.getFormula());
            expressionCopy.setIntegerVariableMap(eval.getIntegerVariableMap());
            copy.setEval(expressionCopy);
        }
        return copy;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JmcBooleanFormula getEval() {
        return eval;
    }

    public void setEval(JmcBooleanFormula eval) {
        this.eval = eval;
    }

    @Override
    public AbstractBoolean read() {
        AbstractBoolean copy = this.clone();
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param(
                                "owner",
                                "org/mpisws/jmc/symbolic/bool/SymbolicBoolean")
                        .param("name", "value")
                        .param("descriptor", "SZ")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        return copy;
    }

    @Override
    public void write(SymbolicBoolean value) {
        SymbolicBoolean symbolicBoolean = (SymbolicBoolean) value.read();

        if (symbolicBoolean.getEval() != null) {
            JmcBooleanFormula expressionCopy = new JmcBooleanFormula();
            expressionCopy.setFormula(symbolicBoolean.eval.getFormula());
            expressionCopy.setIntegerVariableMap(symbolicBoolean.eval.getIntegerVariableMap());
            this.eval = expressionCopy;
        } else {
            this.name = symbolicBoolean.getName();
        }

        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", symbolicBoolean)
                        .param(
                                "owner",
                                "org/mpisws/jmc/symbolic/bool/SymbolicBoolean")
                        .param("name", "value")
                        .param("descriptor", "SZ")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    @Override
    public void write(JmcBooleanFormula value) {
        JmcBooleanFormula expressionCopy = new JmcBooleanFormula();
        expressionCopy.setFormula(value.getFormula());
        expressionCopy.setIntegerVariableMap(value.getIntegerVariableMap());
        this.eval = expressionCopy;

        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", value)
                        .param(
                                "owner",
                                "org/mpisws/jmc/symbolic/bool/SymbolicBoolean")
                        .param("name", "value")
                        .param("descriptor", "SZ")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    private void write() {
        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", value)
                        .param(
                                "owner",
                                "org/mpisws/jmc/symbolic/bool/SymbolicBoolean")
                        .param("name", "value")
                        .param("descriptor", "SZ")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    public void print() {
        if (eval != null) {
            System.out.print(" " + eval.getFormula() + " ");
        } else {
            System.out.print(" " + name + " ");
        }
    }
}

