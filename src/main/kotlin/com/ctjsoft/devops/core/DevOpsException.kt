package com.ctjsoft.devops.core

enum class ErrorKind {
    AUTH,
    NETWORK,
    PARSE,
    VALIDATION,
    GIT_PRECONDITION,
    GIT_COMMAND,
    PARTIAL_SUCCESS,
}

class DevOpsException(
    message: String,
    val kind: ErrorKind,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

