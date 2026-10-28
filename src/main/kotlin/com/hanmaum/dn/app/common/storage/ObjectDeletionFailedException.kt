package com.hanmaum.dn.app.common.storage

/** The bucket accepted a delete request but reported failures for single keys. */
class ObjectDeletionFailedException(
    val failures: List<String>,
) : RuntimeException("Object deletion failed for ${failures.size} key(s): ${failures.joinToString()}")
