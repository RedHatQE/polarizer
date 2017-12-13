package com.github.redhatqe.polarizer.messagebus;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.redhatqe.polarizer.data.ProcessingInfo;


import java.util.Optional;

public class MessageResult<T> {
    private ObjectNode node;
    private Status status;
    public String errorDetails = "";
    // FIXME:  Instead of ProcessingInfo, this should be a MessageResult<T>
    public T info;

    public MessageResult() {
        this(null, null, null);
    }

    public MessageResult(ObjectNode node) {
        this(null, node, null);
    }

    public MessageResult(ObjectNode node, Status status) {
        this(null, node, status);
    }

    public MessageResult(T t, ObjectNode node, Status status) {
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
        FAILED,                 // The request from the Receiving (eg Polarion) side failed
        SUCCESS,                // The request from the Receiving side passed
        PENDING,                // The sender has successfully sent (but dont know if receiver got it
        QUEUED,                 // The receiver has received the request, but has not serviced request yet
        NO_MESSAGE,             // No message at all was received, but before Time out
        EMPTY_MESSAGE,          // Message was received, but is empty (no contents)
        TIMED_OUT,              // Waiting for message response timed out
        SEND_FAIL,              // The http post failed for some reason
        NP_EXCEPTION,           // Null pointer exception occurred (usually on sending side)
        WRONG_MESSAGE_FORMAT,   // Message was received, but was not of the expected format
        JMS_EXCEPTION;          // JMS exception occurred, either on sending or receiving side
    }
}
