package com.bplustree.visualizer.model;

import java.util.List;

/** Result of checking all structural B+ tree invariants. */
public record ValidationResult(boolean valid, List<String> errors) {
    public ValidationResult {
        errors = List.copyOf(errors);
    }
}
