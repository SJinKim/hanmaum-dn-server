package com.hanmaum.dn.app.common.dto

data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null
) {
    companion object {
        fun <T> success(data: T? = null, message: String? = null): ApiResponse<T> {
            return ApiResponse(true, message, data)
        }

        fun <T> error(message: String): ApiResponse<T> {
            return ApiResponse(false, message, null)
        }
    }
}