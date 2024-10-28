package org.mpisws.symbolic;

import java.io.Serializable;

public enum InstructionType implements Serializable {
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    NOT,
    AND,
    OR,
    IMPLIES,
    IFF,
    XOR,
    EQ,
    NEQ,
    LT,
    GT,
    LEQ,
    GEQ,
    DISTINCT,
    ATOM,
}
