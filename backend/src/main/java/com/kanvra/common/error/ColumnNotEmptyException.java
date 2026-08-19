package com.kanvra.common.error;

/**
 * 409 — the column still has tasks and no targetColumnId was provided
 * (docs/SPEC.md §6 delete-column behavior).
 */
public class ColumnNotEmptyException extends ApiException {

    public ColumnNotEmptyException(String message) {
        super(409, "COLUMN_NOT_EMPTY", message);
    }
}
