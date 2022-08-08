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

package me.zhengjin.customs.controller

import me.zhengjin.common.core.entity.HttpResult
import me.zhengjin.common.core.exception.ServiceException
import me.zhengjin.common.customs.message.CEBMessageType
import me.zhengjin.customs.config.properties.BaseProperties
import me.zhengjin.customs.controller.vo.PinDuoDuoVO
import me.zhengjin.customs.controller.vo.SignatureVO
import me.zhengjin.customs.utils.XmlSignatureUtils
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(produces = [MediaType.APPLICATION_JSON_UTF8_VALUE])
class SignatureController(
    private val baseProperties: BaseProperties
) {
    @GetMapping("/info")
    fun info(): HttpResult<Map<String, Any?>> {
        return HttpResult.ok(
            body = mapOf(
                "customsRegistrationName" to baseProperties.signature.customsRegistrationName,
                "customsRegistrationCode" to baseProperties.signature.customsRegistrationCode,
                "supportTypes" to baseProperties.messageConfig.keys,
            )
        )
    }

    @PostMapping("/signature")
    fun signature(@RequestBody @Validated vo: SignatureVO): HttpResult<String> {
        ServiceException.requireTrue(baseProperties.signature.customsRegistrationCode == vo.customsRegistrationCode) {
            "customsRegistrationCode is not correct"
        }
        val messageType = CEBMessageType.valueOfNoCaseInsensitive(vo.messageType!!)
        ServiceException.requireNotNull(messageType) {
            "messageType is not correct"
        }
        ServiceException.requireTrue(baseProperties.messageConfig.keys.contains(messageType)) {
            "messageType is not supported"
        }
        return HttpResult.ok(
            body = XmlSignatureUtils.signatureDigest(
                baseProperties.signature.getClientEndPointCertType(),
                baseProperties.signature.getPrivateKey(),
                vo.waitSignatureData!!
            )
        )
    }

    @PostMapping("/pinduoduo")
    fun createPinDuoDuoDeclareData(@RequestBody @Validated vo: PinDuoDuoVO): HttpResult<String> {
        ServiceException.requireTrue(baseProperties.signature.customsRegistrationCode == vo.customsRegistrationCode) {
            "customsRegistrationCode is not correct"
        }
        val messageType = CEBMessageType.valueOfNoCaseInsensitive(vo.messageType!!)
        ServiceException.requireNotNull(messageType) {
            "messageType is not correct"
        }
        ServiceException.requireTrue(baseProperties.messageConfig.keys.contains(messageType)) {
            "messageType is not supported"
        }
        val dxpId = vo.dxpId ?: baseProperties.signature.dxpId ?: throw ServiceException("dxpId is null")
        ServiceException.requireNotNullOrBlank(vo.data) {
            "data is null or blank"
        }
        return HttpResult.ok(
            body = XmlSignatureUtils.createPinDuoDuoDeclareData(
                businessXml = vo.data!!,
                msgType = messageType!!,
                dxpId = dxpId,
                x509Certificate = baseProperties.signature.getClientEndPointCert(),
                baseTransfer = baseProperties.signature.toBaseTransfer(dxpId),
                baseProperties.signature.getClientEndPointCertType(),
            )
        )
    }
}
