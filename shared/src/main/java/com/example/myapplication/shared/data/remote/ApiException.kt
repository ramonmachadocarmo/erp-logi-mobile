package com.example.myapplication.shared.data.remote

/** Mirrors mobile/lib/core/network/api_exception.dart's shape so error handling reads the same. */
class ApiException(
    val statusCode: Int?,
    message: String,
    val isNetwork: Boolean = false,
) : Exception(message)
