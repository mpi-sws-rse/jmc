package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

import java.util.function.BiFunction;

public class MessageServer {
    public static void send_tagged_msg(long threadId, int tag, Object message) {
        Message taggedMessage = new TaggedMessage(threadId, message, tag);
        long jmcId = RuntimeEnvironment.threadIdMap.get(threadId);
        JMCThread recvThread = (JMCThread) RuntimeEnvironment.threadObjectMap.get(jmcId);
        recvThread.pushMessage(taggedMessage);

    }

    public static void send_msg(long threadId, Object message) {
        Message simpleMessage = new SimpleMessage(threadId, message);
        long jmcId = RuntimeEnvironment.threadIdMap.get(threadId);
        JMCThread recvThread = (JMCThread) RuntimeEnvironment.threadObjectMap.get(jmcId);
        recvThread.pushMessage(simpleMessage);

    }

    public static void recv_tagged_msg_block(BiFunction<Long, Long, Boolean> function) {

    }

    public static void recv_msg_block() {

    }

    public static void recv_msg() {

    }

}
