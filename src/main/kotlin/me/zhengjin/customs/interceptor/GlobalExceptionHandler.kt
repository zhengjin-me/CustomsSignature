/*
 * MIT License
 *
 * Copyright (c) 2022 ZhengJin Fang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.zhengjin.customs.interceptor

import cn.hutool.core.io.unit.DataSizeUtil
import me.zhengjin.common.core.entity.HttpResult
import me.zhengjin.common.core.exception.ServiceException
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.NoHandlerFoundException
import java.util.stream.Collectors
import javax.validation.ConstraintViolationException
import javax.validation.ElementKind

/**
 * @version V1.0
 * @Title: ExceptionHandler
 * @Description: 全局异常处理(Controller)
 * @Author fangzhengjin
 * @Date 2017-06-14 11:45
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private companion object {
        @JvmStatic
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    /**
     * Bean Validation
     * 处理所有接口数据验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(e: MethodArgumentNotValidException): ResponseEntity<HttpResult<String>> {

        return try {
            handleServiceException(
                ServiceException(
                    type = ServiceException.Exceptions.ILLEGAL_ARGUMENT,
                    message = e.bindingResult.allErrors
                        .map { oe ->
                            oe.let {
                                if (it is FieldError) return@let "${it.field}: ${it.defaultMessage}"
                                it.defaultMessage
                            }
                        }.joinToString(";")
                )
            )
        } catch (ignore: Exception) {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON_UTF8
            ResponseEntity(
                HttpResult.fail(
                    message = e.bindingResult.allErrors
                        .stream()
                        .map { it.defaultMessage }
                        .collect(Collectors.joining(";"))
                ),
                headers, HttpStatus.OK
            )
        }
    }

    /**
     * @Valid
     * 处理所有接口数据验证异常
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(e: ConstraintViolationException): ResponseEntity<HttpResult<String>> {

        val errors = try {
            e.constraintViolations.parallelStream().map { it1 ->
                it1.propertyPath.let { it2 ->
                    val paths = ArrayList<String>()
                    it2.forEach { it3 ->
                        if (it3.kind == ElementKind.PARAMETER || it3.kind == ElementKind.PROPERTY) paths.add(it3.toString())
                    }
                    paths.joinToString(separator = ".", postfix = ": ")
                } + it1.message
            }.collect(Collectors.toList()).joinToString(separator = ";")
        } catch (innerEx: Exception) {
            e.constraintViolations.parallelStream().map { it.message }.collect(Collectors.toList()).joinToString(";")
        }

        return try {
            handleServiceException(
                ServiceException(
                    type = ServiceException.Exceptions.ILLEGAL_ARGUMENT,
                    message = errors
                )
            )
        } catch (innerEx: Exception) {
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON_UTF8
            ResponseEntity(
                HttpResult.fail(
                    message = errors
                ),
                headers, HttpStatus.OK
            )
        }
    }

    /**
     * 处理文件上传大小异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSizeExceededException(e: MaxUploadSizeExceededException): ResponseEntity<HttpResult<String>> {
        val cause = e.cause?.cause
        return handleServiceException(
            when (cause) {
                is SizeLimitExceededException -> ServiceException(
                    message = "当前上传文件[${DataSizeUtil.format(cause.actualSize)}], 超过最大允许上传大小[${DataSizeUtil.format(cause.permittedSize)}]!",
                    printStack = false
                )
                else -> ServiceException(
                    message = "当前上传文件超过最大允许上传大小[${DataSizeUtil.format(e.maxUploadSize)}]!",
                    printStack = false
                )
            }
        )
    }

    /**
     * 处理所有不可知的异常
     */
    @ExceptionHandler(Exception::class)
    fun handleAnyException(e: Exception): ResponseEntity<HttpResult<String>> {
        var printStack = false
        // 需要忽略错误输出的异常
        if (!listOf<Class<*>>(
                NoHandlerFoundException::class.java
            ).contains(e.javaClass)
        ) {
            printStack = true
        }

        // 判断响应状态
        var exceptionType = ServiceException.Exceptions.INTERNAL_SERVER_ERROR
        if (e is NoHandlerFoundException) {
            exceptionType = ServiceException.Exceptions.NOT_FOUND
        }

//        val headers = HttpHeaders()
//        headers.contentType = MediaType.APPLICATION_JSON_UTF8
//        return ResponseEntity(HttpResult.fail(code = statusCode, message = e.message), headers, HttpStatus.OK)
        return handleServiceException(
            ServiceException(
                type = exceptionType,
                message = e.message,
                cause = e,
                printStack = printStack
            )
        )
    }

    /**
     * 处理ServiceException
     */
    @ExceptionHandler(ServiceException::class)
    fun handleServiceException(e: ServiceException): ResponseEntity<HttpResult<String>> {
        logger.error(e.message, if (e.enableStack) e else null)
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON_UTF8
        return ResponseEntity(
            HttpResult.fail(
                code = e.type.code,
                message = e.message ?: e.type.message
            ),
            headers,
            HttpStatus.OK
        )
    }
}
