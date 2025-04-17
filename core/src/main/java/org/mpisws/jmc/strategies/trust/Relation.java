package org.mpisws.jmc.strategies.trust;

public enum Relation {
    ReadsFrom("readsFrom"),
    Coherency("coherency"),
    ProgramOrder("programOrder"),
    ThreadCreation("threadCreation"),
    ThreadStart("threadStart"),
    ThreadJoin("threadJoin"),
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
