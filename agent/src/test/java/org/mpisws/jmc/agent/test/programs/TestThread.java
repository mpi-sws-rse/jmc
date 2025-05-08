package org.mpisws.jmc.agent.test.programs;

// Expected output

// import org.mpisws.jmc.util.concurrent.JmcThread;
//
// public class TestThread extends Thread {
//    @Override
//    public void run() {
//        System.out.println("Thread is running");
//        System.out.println("Thread has finished running");
//    }
//
//    public static void main(String[] args) {
//        JmcThread thread = new JmcThread(new TestThread());
//        thread.start();
//        try {
//            thread.join1();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        System.out.println("Main method has finished running");
//    }
// }

/** TestThread is a simple Java program that demonstrates the use of threads. */
public class TestThread extends Thread {

    @Override
    public void run() {
        System.out.println("Thread is running");
        System.out.println("Thread has finished running");
    }

    public static void main(String[] args) {
        TestThread thread = new TestThread();
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Main method has finished running");
    }
}
