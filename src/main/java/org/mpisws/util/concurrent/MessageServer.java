package org.mpisws.util.concurrent;

import org.mpisws.runtime.RuntimeEnvironment;

import programStructure.Message;
import programStructure.ReceiveEvent;

import java.util.function.BiFunction;

public class MessageServer {
    public static void send_tagged_msg(long receiverThreadId, long tag, Object message) {
        Message taggedMessage = RuntimeEnvironment.sendTaggedMessageOperation(Thread.currentThread(), receiverThreadId, tag, message);
        JMCThread recvThread = (JMCThread) RuntimeEnvironment.findJVMThreadObject(receiverThreadId);
        recvThread.pushMessage(taggedMessage);
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }

    public static void send_msg(long receiverThreadId, Object message) {
        Message simpleMessage = RuntimeEnvironment.sendSimpleMessageOperation(Thread.currentThread(), receiverThreadId, message);
        JMCThread recvThread = (JMCThread) RuntimeEnvironment.findJVMThreadObject(receiverThreadId);
        recvThread.pushMessage(simpleMessage);
        RuntimeEnvironment.waitRequest(Thread.currentThread());
    }

    public static Object recv_tagged_msg_block(BiFunction<Long, Long, Boolean> function) {
        ReceiveEvent receiveEvent = RuntimeEnvironment.blockingReceiveRequestOperation(Thread.currentThread(), function);
        RuntimeEnvironment.blockingReceiveOperation(receiveEvent);
        Object messageValue = findMessageValue();
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        return messageValue;
    }

    public static Object recv_tagged_msg(BiFunction<Long, Long, Boolean> function) {
        RuntimeEnvironment.receiveTaggedOperation(Thread.currentThread(), function);
        Object messageValue = findMessageValue();
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        return messageValue;
    }

    public static Object recv_msg_block() {
        ReceiveEvent receiveEvent = RuntimeEnvironment.blockingReceiveRequestOperation(Thread.currentThread());
        RuntimeEnvironment.blockingReceiveOperation(receiveEvent);
        Object messageValue = findMessageValue();
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        return messageValue;
    }

    public static Object recv_msg() {
        RuntimeEnvironment.receiveOperation(Thread.currentThread());
        Object messageValue = findMessageValue();
        RuntimeEnvironment.waitRequest(Thread.currentThread());
        return messageValue;
    }

    private static Object findMessageValue() {
        Message message = ((JMCThread) Thread.currentThread()).findMessage();
        if (message == null) {
            return null;
        } else {
            return message.getValue();
        }
    }
}