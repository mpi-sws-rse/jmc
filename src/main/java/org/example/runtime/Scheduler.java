package org.example.runtime;

import com.sun.jdi.Value;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class Scheduler{
    private static List<Thread> threadList = new ArrayList<>();

    private Scheduler(){}

    public static void init(){
        System.out.println("[Scheduler Message] : The Scheduler has been initialized");
    }

    public static void addThread(Thread thread){
        if (!threadList.contains(thread)) {
            threadList.add(thread);
            System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" added to the threadList of the Scheduler object");
            System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" has the "+thread.getState()+" state");
        } else {
            System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" is already in the list of the Scheduler object");
        }
    }

    public static void threadStart(Thread currentThread, Thread thread){
        if (threadList.contains(thread)) {
            System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" requested to run the start() inside the "+currentThread.getName()+":"+currentThread.getId());

            // @currentThread is busy waiting for the @thread to finish
            thread.start();
            System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" has been started");
            try {
                System.out.println("[Scheduler Message] : "+currentThread.getName()+":"+ currentThread.getId() +" is waiting for "+thread.getName()+":"+ thread.getId() +" to finish");
                thread.join();
                System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" has finished");
                System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" has the "+thread.getState()+" state");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        } else {
            System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" is not in the list");
        }
    }

    public static void newReadOperation(Object value, Thread thread, String owner, String name, String descriptor){
        System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" requested to read the value of "+owner+"."+name+"("+descriptor+")");
        System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" read the value of "+owner+"."+name+"("+descriptor+") = "+value);
    }

    public static void oldReadOperation(Object obj, Thread thread, String owner, String name, String descriptor){
        try {
            Class<?> clazz = Class.forName(owner.replace("/", "."));
            Object instance = clazz.cast(obj);
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(instance);
            System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" read the value of "+owner+"."+name+"("+descriptor+") = "+value);
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static void newWriteOperation(Object obj, Object newVal, Thread thread, String owner, String name, String descriptor){
        try {
            Class<?> clazz = Class.forName(owner.replace("/", "."));
            Object instance = clazz.cast(obj);
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            Object oldValue = field.get(instance);
            System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" requested to write the ["+ newVal +"] value to "+owner+"."+name+"("+descriptor+") with old value of ["+oldValue+"]");
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
