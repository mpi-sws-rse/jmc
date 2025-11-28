package org.mpi_sws.jmc.symbolic.integer;

import org.mpi_sws.jmc.runtime.JmcRuntime;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.symbolic.JmcSymbolic;

public class SymbolicInteger extends AbstractInteger {
    private String name;
    private ArithmeticStatement eval;
    private int value;

    private SymbolicInteger() {
        String[] parts = this.toString().split("@");
        this.name = "SymbolicInteger@" + parts[parts.length - 1];
        write();
    }

    private SymbolicInteger(String name, int value) {
        this.name = name;
        this.setValue(value);
    }

    public SymbolicInteger(String name) {
        Long id = JmcRuntime.currentTask();
        this.name = "SymbolicInteger@" + name + "_" + id;
        write();
    }

    public void assign(ArithmeticStatement expression) {
        write(expression);
    }

    public void assign(SymbolicInteger symbolicInteger) {
        write(symbolicInteger);
    }

    @Override
    public SymbolicInteger clone() {
        SymbolicInteger copy = new SymbolicInteger(name, getValue());
        if (eval != null) {
            copy.setEval(eval.clone());
        }
        return copy;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArithmeticStatement getEval() {
        if (eval != null) {
            return eval;
        } else {
            return null;
        }
    }

    public void setEval(ArithmeticStatement eval) {
        this.eval = eval;
    }

    @Override
    public AbstractInteger read() {
        AbstractInteger copy = this.clone();

        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.READ_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("owner", "org/mpisws/jmc/symbolic/integer/SymbolicInteger")
                        .param("name", "value")
                        .param("descriptor", "SI")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
        return copy;
    }

    @Override
    public void write(AbstractInteger value) {
        SymbolicInteger symbolicInteger = (SymbolicInteger) value.read();
        if (symbolicInteger.getEval() != null) {
            this.eval = symbolicInteger.getEval().clone();
        } else {
            this.name = symbolicInteger.getName();
        }

        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", symbolicInteger)
                        .param("owner", "org/mpisws/jmc/symbolic/integer/SymbolicInteger")
                        .param("name", "value")
                        .param("descriptor", "SI")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    @Override
    public void write(ArithmeticStatement value) {
        this.eval = value.clone();

        JmcRuntimeEvent event =
                new JmcRuntimeEvent.Builder()
                        .type(JmcRuntimeEvent.Type.WRITE_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("newValue", value)
                        .param("owner", "org/mpisws/jmc/symbolic/integer/SymbolicInteger")
                        .param("name", "value")
                        .param("descriptor", "SI")
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
                        .param("owner", "org/mpisws/jmc/symbolic/integer/SymbolicInteger")
                        .param("name", "value")
                        .param("descriptor", "SI")
                        .param("instance", this)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

    public void print() {
        if (eval != null) {
            if (eval.getLeft() instanceof SymbolicInteger) {
                ((SymbolicInteger) eval.getLeft()).print();
            } else {
                System.out.print(" " + eval.getLeft().getValue() + " ");
            }
            System.out.print(" " + eval.getOperator() + " ");
            if (eval.getRight() instanceof SymbolicInteger) {
                ((SymbolicInteger) eval.getRight()).print();
            } else {
                System.out.print(" " + eval.getRight().getValue() + " ");
            }
        } else {
            System.out.print(" " + name + " ");
        }
    }

    public int getIntValue() {
        if (this.getEval() != null) {
            int leftValue;
            if (this.getEval().getLeft() instanceof SymbolicInteger left) {
                leftValue = left.getIntValue();
            } else {
                leftValue = this.getEval().getLeft().getValue();
            }
            int rightValue;
            if (this.getEval().getRight() instanceof SymbolicInteger right) {
                rightValue = right.getIntValue();
            } else {
                rightValue = this.getEval().getRight().getValue();
            }
            switch (this.getEval().getOperator()) {
                case ADD:
                    return leftValue + rightValue;
                case SUB:
                    return leftValue - rightValue;
                case MUL:
                    return leftValue * rightValue;
                case DIV:
                    if (rightValue == 0) {
                        throw new ArithmeticException("[JMC Formula Message] Division by zero");
                    }
                    return leftValue / rightValue;
                case MOD:
                    if (rightValue == 0) {
                        throw new ArithmeticException("[JMC Formula Message] Modulo by zero");
                    }
                    return leftValue % rightValue;
                default:
                    throw new IllegalArgumentException(
                            "[JMC Formula Message] Unsupported operator");
            }
        } else {
            return JmcSymbolic.getSymIntVarValue(this.getName());
        }
    }
}
