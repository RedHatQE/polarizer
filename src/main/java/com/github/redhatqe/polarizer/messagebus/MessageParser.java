package com.github.redhatqe.polarizer.messagebus;

import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.jms.JMSException;
import javax.jms.Message;
import java.util.concurrent.ExecutionException;

@FunctionalInterface
interface MessageParser {
    ObjectNode parse(Message msg) throws InterruptedException, ExecutionException, JMSException;
}
