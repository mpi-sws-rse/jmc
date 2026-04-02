package org.mpi_sws.jmc.test.stress;

import org.mpi_sws.jmc.annotations.JmcCheck;
import org.mpi_sws.jmc.annotations.JmcCheckConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class InvokeDynamicTest {

    public static void atomicInteger_seq() {
        List<AtomicInteger> atomicIntegers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            AtomicInteger atomicInteger = new AtomicInteger() {
                private final ReentrantLock lock = new ReentrantLock();

                @Override
                public String toString() {
                    lock.lock();
                    lock.unlock();
                    return super.toString();
                }
            };
            atomicIntegers.add(atomicInteger);
            atomicIntegers.get(i).toString();
        }
    }

    public static void reentrantLock_seq() {
        ReentrantLock reentrantLock = new ReentrantLock() {
            private final AtomicInteger atomicInteger = new AtomicInteger() {
                @Override
                public String toString() {
                    return super.toString();
                }
            };
            private final ReentrantLock lock = new ReentrantLock() {
                @Override
                public String toString() {
                    lock.toString();
                    atomicInteger.toString();
                    return "JMC is the best :)";
                }
            };

            @Override
            public String toString() {
                atomicInteger.incrementAndGet();
                atomicInteger.toString();
                return super.toString();
            }
        };
        reentrantLock.lock();
        reentrantLock.toString();
        reentrantLock.unlock();
    }

    public static void lockAtomic_seq() {
        ReentrantLock reentrantLock = new ReentrantLock() {
            @Override
            public String toString() {
                return "JMC is here!";
            }
        };

        AtomicInteger atomicInteger = new AtomicInteger() {
            @Override
            public String toString() {
                reentrantLock.lock();
                reentrantLock.unlock();
                return super.toString();
            }
        };

    }

    public static void reentrantLock_par() {
        ReentrantLock reentrantLock = new ReentrantLock() {
            private final ReentrantLock lock = new ReentrantLock();

            @Override
            public String toString() {
                return super.toString();
            }
        };

        AtomicInteger atomicInteger = new AtomicInteger() {
            @Override
            public String toString() {
                return super.toString();
            }
        };

        Thread thread1 = new Thread() {
            /**
             *
             */
            @Override
            public void run() {
                System.out.println(reentrantLock);
            }
        };

        Object o = new Object();

        Thread thread2 = new Thread(() -> {
            synchronized (o) {
                reentrantLock.lock();
                reentrantLock.toString();
                reentrantLock.unlock();
                atomicInteger.incrementAndGet();
                atomicInteger.toString();
            }
        });

        Thread thread3 = new Thread(reentrantLock::toString);

        thread1.start();
        thread2.start();
        thread3.start();

        try {
            thread1.join();
            thread2.join();
            thread3.join();
        } catch (InterruptedException e) {
        }
    }

    public static void synchronizedBlock_seq() {
        Object s = new Object() {
            final Object o = new Object() {
                @Override
                public String toString() {
                    synchronized (this) {
                        return "Inner Synchronized Block Test";
                    }
                }
            };

            @Override
            public synchronized String toString() {
                return "Synchronized Block Test" + o;
            }
        };
        System.out.println(s);
    }

    // TODO : Add stress tests for synchronized blocks and methods

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testAtomicInteger_seq() {
        atomicInteger_seq();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testReentrantLock_seq() {
        reentrantLock_seq();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testLockAtomic_seq() {
        lockAtomic_seq();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testReentrantLock_par() {
        reentrantLock_par();
    }

    @JmcCheck
    @JmcCheckConfiguration(numIterations = 10)
    public void testSynchronizedBlock_seq() {
        synchronizedBlock_seq();
    }
}
