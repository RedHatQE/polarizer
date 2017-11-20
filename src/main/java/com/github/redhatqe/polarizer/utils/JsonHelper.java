package com.github.redhatqe.polarizer.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonHelper {
    public static String nodeToString(ObjectNode node) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        Object obj = mapper.treeToValue(node, Object.class);
        return mapper.writeValueAsString(obj);
    }

    public static String nodeToString(JsonNode node) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        Object obj = mapper.treeToValue(node, Object.class);
        return mapper.writeValueAsString(obj);
    }
}
