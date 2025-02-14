package org.mpisws.concurrent.programs.dining;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mpisws.util.concurrent.JmcThread;
import org.mpisws.util.concurrent.JmcReentrantLock;

public class Philosopher extends JmcThread {

    private static final Logger LOGGER = LogManager.getLogger(Philosopher.class);

    private final int id;
    private final JmcReentrantLock leftStick;
    private final JmcReentrantLock rightStick;

    public Philosopher(int id, JmcReentrantLock leftFork, JmcReentrantLock rightFork) {
        super();
        this.id = id;
        this.leftStick = leftFork;
        this.rightStick = rightFork;
    }

    private void think() {
        LOGGER.debug("Philosopher {} is thinking.", id);
        // Thread.sleep(1000);
    }

    private void tryToEat(){
//        try {
            rightStick.lock();
            leftStick.lock();
            eat();
//        } catch (JMCInterruptException e) {
//            LOGGER.debug("Philosopher {} has been interrupted.", id);
//            throw e;
//        } finally {
            leftStick.unlock();
            rightStick.unlock();
//        }
        LOGGER.debug("Philosopher {} has put down the left stick.", id);
        LOGGER.debug("Philosopher {} has put down the right stick.", id);
    }

    private void eat() {
        LOGGER.debug("Philosopher {} is eating.", id);
        // Thread.sleep(1000);
    }

    @Override
    public void run1() {
        think();
        tryToEat();
    }
}
