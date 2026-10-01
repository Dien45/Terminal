package com.dien.terminal.core.logging

enum class OperationType {
    INSTALL, SEARCH, UPDATE, UPGRADE, FIX, BACKUP, RESTORE, SETUP
}

enum class OperationStatus {
    RUNNING, SUCCESS, FAILED
}
