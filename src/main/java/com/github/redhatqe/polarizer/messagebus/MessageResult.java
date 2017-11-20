package com.github.redhatqe.polarizer.messagebus;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarizer.data.ProcessingInfo;


import java.util.Optional;

public class MessageResult {
    private ObjectNode node;
    private Status status;
    public String errorDetails = "";
    // FIXME:  Instead of ProcessingInfo, this should be a MessageResult<T>
    public ProcessingInfo info;

    public MessageResult() {
        this(null, null, null);
    }

    public MessageResult(ObjectNode node) {
        this(null, node, null);
    }

    public MessageResult(ObjectNode node, Status status) {
        this(null, node, status);
    }

    public MessageResult(ProcessingInfo t, ObjectNode node, Status status) {
        this.node = node;
        this.status = (node == null) ? Status.NO_MESSAGE : Status.SUCCESS;
        this.info = t;
    }

    public Optional<ObjectNode> getNode() {
        return Optional.ofNullable(this.node);
    }

    public Status getStatus() { return this.status; }

    public void setNode(ObjectNode node) {
        this.node = node;
    }

    public void setStatus(Status stat) {
        this.status = stat;
    }

    public enum Status {
        FAILED,
        SUCCESS,
        PENDING,
        NO_MESSAGE,
        EMPTY_MESSAGE,
        TIMED_OUT,
        NP_EXCEPTION,
        JMS_EXCEPTION;
    }
}
