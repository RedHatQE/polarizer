package com.github.redhatqe.polarizer.messagebus;

import com.fasterxml.jackson.databind.node.ObjectNode;

@FunctionalInterface
public interface MessageHandler {
    MessageResult handle(ObjectNode node);
}
