package org.mpisws.concurrent.programs.nondet.map;

import org.mpisws.concurrent.programs.nondet.map.coarse.Map;
import org.mpisws.symbolic.ArithmeticFormula;
import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.symbolic.SymbolicOperation;
import org.mpisws.util.concurrent.JMCInterruptException;
import org.mpisws.util.concurrent.Utils;

import java.util.ArrayList;
import java.util.List;

public class Client1 {

    public static void main(String[] args) {
        try {
            Map map = new Map();
            int NUM_OPERATIONS = 3;
            List<Integer> elements = new ArrayList<>(NUM_OPERATIONS);
            List<SymbolicInteger> keys = new ArrayList<>(NUM_OPERATIONS);
            for (int i = 0; i < NUM_OPERATIONS; i++) {
                elements.add(i);
                SymbolicInteger key = new SymbolicInteger("key-" + i, false);
                ArithmeticFormula f = new ArithmeticFormula();
                SymbolicOperation op1 = f.gt(key, -1);
                Utils.assume(op1); // ASSUME key > -1
                keys.add(key);
            }

            List<InsertionThread> threads = new ArrayList<>(NUM_OPERATIONS);
            for (int i = 0; i < NUM_OPERATIONS; i++) {
                int item = elements.get(i);
                SymbolicInteger key = keys.get(i);
                InsertionThread thread = new InsertionThread(map, key, item);
                threads.add(thread);
            }

            for (int i = 0; i < NUM_OPERATIONS; i++) {
                threads.get(i).start();
            }

            for (int i = 0; i < NUM_OPERATIONS; i++) {
                try {
                    threads.get(i).join();
                } catch (InterruptedException e) {
                    //e.printStackTrace();
                }
            }
        } catch (JMCInterruptException e) {

        }
    }
}
