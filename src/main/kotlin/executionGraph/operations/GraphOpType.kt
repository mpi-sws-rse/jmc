package executionGraph.operations

enum class GraphOpType {
    FR_R_W,
    FR_RX_W,
    FR_W_W,
    FR_L_W,
    BR_W_R,
    FR_NEG_SYM,
    RESET_PROVER,
    CREATE_PROVER,
    REMOVE_PROVER,
}