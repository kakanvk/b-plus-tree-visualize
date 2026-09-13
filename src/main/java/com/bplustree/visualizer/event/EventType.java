package com.bplustree.visualizer.event;

/** Describes one visual checkpoint in a B+ tree operation. */
public enum EventType {
    VISIT_NODE,
    COMPARE_KEY,
    TRAVERSE_EDGE,
    MATCH_KEY,
    INSERT_KEY,
    DELETE_KEY,
    NODE_OVERFLOW,
    NODE_UNDERFLOW,
    SPLIT_NODE,
    MERGE_NODE,
    BORROW_KEY,
    PROMOTE_KEY,
    UPDATE_SEPARATOR,
    CREATE_ROOT,
    SHRINK_ROOT,
    HIGHLIGHT_RANGE,
    COMPLETE,
    ERROR
}
