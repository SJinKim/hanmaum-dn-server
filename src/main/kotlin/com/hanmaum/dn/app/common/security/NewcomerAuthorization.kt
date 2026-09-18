package com.hanmaum.dn.app.common.security

import org.springframework.security.access.prepost.PreAuthorize

/**
 * Grants read access to newcomer PII.
 *
 * Keep this check on every newcomer read endpoint instead of spelling role expressions
 * in individual controllers. Editors inherit read access through their own realm role.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'NEWCOMER_VIEWER', 'NEWCOMER_EDITOR')")
annotation class NewcomerReadAccess

/** Grants write access to newcomer PII. Viewers deliberately do not pass this check. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("hasAnyRole('ADMIN', 'NEWCOMER_EDITOR')")
annotation class NewcomerWriteAccess
