package com.hl.hybridsearch.api

import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 전역 에러 매핑.
 *  - IllegalArgumentException (주로 SearchRequest.init 의 require 실패) → 400
 *  - 그 외 예기치 않은 예외는 500 + 일관된 에러 포맷
 *
 * 엔터프라이즈에서는 traceId, source service 등을 덧붙이는 지점.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.debug("Bad request: {}", e.message)
        return ResponseEntity.badRequest().body(errorOf("BAD_REQUEST", e.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorOf("INTERNAL_ERROR", e.message ?: e.javaClass.simpleName))
    }

    private fun errorOf(code: String, message: String?): ErrorResponse =
        ErrorResponse(code = code, message = message ?: "", timestamp = Instant.now().toString())

    data class ErrorResponse(
        val code: String,
        val message: String,
        val timestamp: String,
    )
}
