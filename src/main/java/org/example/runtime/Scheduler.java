package org.example.runtime;

import dpor.Trust;
import programStructure.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scheduler{
    private static List<Thread> threadList = new ArrayList<>();

    public static Map<Integer, Threads> threadMap = new HashMap<>();

    private static int serialNumber = 0;
    private Scheduler(){}

    public static void init(){
        System.out.println("[Scheduler Message] : The Scheduler has been initialized");
    }

    public static void runMC(){
        Trust trust = new Trust();
        trust.setThreads(threadMap);
        trust.verify();
        for (Integer key : Scheduler.threadMap.keySet()) {
            System.out.println("Thread name: " + key + " Thread id: " + Scheduler.threadMap.get(key).component2());
        }
        System.out.println("All the possible execution graphs have been visited.");
    }

    public static void addThread(Thread thread){
        if (!threadList.contains(thread)) {
            Threads trd = new Threads((int) thread.getId(),new ArrayList<>());
            threadMap.put((int) thread.getId(),trd);
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
            serialNumber = 0;
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
            Location location = new Location(owner,name);
            ReadEvent readEvent = new ReadEvent((int) thread.getId(), EventType.READ,++serialNumber,value,null,location);
            threadMap.get((int) thread.getId()).component2().add(readEvent);
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
            Location location = new Location(owner,name);
            WriteEvent writeEvent = new WriteEvent((int) thread.getId(), EventType.WRITE,++serialNumber,newVal,location);
            threadMap.get((int) thread.getId()).component2().add(writeEvent);
            System.out.println("[Scheduler Message] : "+thread.getName()+":"+ thread.getId() +" requested to write the ["+ newVal +"] value to "+owner+"."+name+"("+descriptor+") with old value of ["+oldValue+"]");
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
