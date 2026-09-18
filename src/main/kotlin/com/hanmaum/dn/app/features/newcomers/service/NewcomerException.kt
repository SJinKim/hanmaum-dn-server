package com.hanmaum.dn.app.features.newcomers.service

import org.springframework.http.HttpStatus

class NewcomerException(
    val status: HttpStatus,
    val safeDetail: String,
) : RuntimeException(safeDetail)
