package org.example.checker;

import org.example.runtime.RuntimeEnvironment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SearchStrategy {

    public void startEvent(Thread thread);

    public boolean done();

    public void test();
}
