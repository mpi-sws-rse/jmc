package org.mpisws.strategies.trust;

public enum Relation {
    ReadsFrom("readsFrom"),
    Coherency("coherency"),
    ProgramOrder("programOrder"),
    ThreadCreation("threadCreation"),
    ;

    private final String key;

    public String key() {
        return key;
    }

    private Relation(String key) {
        this.key = key;
    }

    @Override
    public String toString() {
        return key;
    }
}
