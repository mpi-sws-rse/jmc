package org.mpisws.util.concurrent;

import org.mpisws.runtime.JmcRuntime;

import programStructure.Message;
import programStructure.ReceiveEvent;

import java.util.function.BiFunction;

public class MessageServer {
    public static void send_tagged_msg(long receiverThreadId, long tag, Object message) {
        Message taggedMessage =
                JmcRuntime.sendTaggedMessageOperation(
                        Thread.currentThread(), receiverThreadId, tag, message);
        JMCThread recvThread = (JMCThread) JmcRuntime.findJVMThreadObject(receiverThreadId);
        recvThread.pushMessage(taggedMessage);
        JmcRuntime.waitRequest(Thread.currentThread());
    }

    public static void send_msg(long receiverThreadId, Object message) {
        Message simpleMessage =
                JmcRuntime.sendSimpleMessageOperation(
                        Thread.currentThread(), receiverThreadId, message);
        JMCThread recvThread = (JMCThread) JmcRuntime.findJVMThreadObject(receiverThreadId);
        recvThread.pushMessage(simpleMessage);
        JmcRuntime.waitRequest(Thread.currentThread());
    }

    public static Object recv_tagged_msg_block(BiFunction<Long, Long, Boolean> function) {
        ReceiveEvent receiveEvent =
                JmcRuntime.blockingReceiveRequestOperation(
                        Thread.currentThread(), function);
        JmcRuntime.blockingReceiveOperation(receiveEvent);
        Object messageValue = findMessageValue();
        JmcRuntime.waitRequest(Thread.currentThread());
        return messageValue;
    }

    public static Object recv_tagged_msg(BiFunction<Long, Long, Boolean> function) {
        JmcRuntime.receiveTaggedOperation(Thread.currentThread(), function);
        Object messageValue = findMessageValue();
        JmcRuntime.waitRequest(Thread.currentThread());
        return messageValue;
    }

    public static Object recv_msg_block() {
        ReceiveEvent receiveEvent =
                JmcRuntime.blockingReceiveRequestOperation(Thread.currentThread());
        JmcRuntime.blockingReceiveOperation(receiveEvent);
        Object messageValue = findMessageValue();
        JmcRuntime.waitRequest(Thread.currentThread());
        return messageValue;
    }

    public static Object recv_msg() {
        JmcRuntime.receiveOperation(Thread.currentThread());
        Object messageValue = findMessageValue();
        JmcRuntime.waitRequest(Thread.currentThread());
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
