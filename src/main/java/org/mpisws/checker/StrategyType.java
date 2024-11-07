package org.mpisws.checker;

import java.io.Serializable;

/**
 * The StrategyType enum is used to represent the different types of strategies that can be used to
 * manage the execution.
 */
public enum StrategyType implements Serializable {
    RANDOM,
    TRUST,
    REPLAY,
    MUST,
    OPT_TRUST,
}
