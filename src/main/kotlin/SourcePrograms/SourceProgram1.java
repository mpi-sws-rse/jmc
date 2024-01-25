package SourcePrograms;

public class SourceProgram1 {
    public int x;

    public static void main(String[] args) {
        SourceProgram1 sp = new SourceProgram1();
        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                sp.x = ++sp.x;
                System.out.println(sp.x);
            }
        });

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                sp.x = ++sp.x;
                System.out.println(sp.x);
            }
        });
        t1.start();
        t2.start();
    }

}
