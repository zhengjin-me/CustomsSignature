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

package me.zhengjin.customs.signatureKey

import me.zhengjin.common.core.exception.ServiceException
import me.zhengjin.common.customs.message.extend.BaseTransfer
import me.zhengjin.customs.enum.ClientEndPointCertType
import me.zhengjin.customs.utils.XmlSignatureUtils
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * 海关签名信息
 */
class CustomsSignature {
    var dxpId: String? = null

    /**
     * 企业海关注册名称
     */
    var customsRegistrationName: String? = null

    /**
     * 企业海关注册代码
     */
    var customsRegistrationCode: String? = null

    /**
     * 私钥在密码机中的序号
     */
    var keyIndex: Int? = null

    /**
     * 私钥信息
     */
    var privateKey: String? = null

    /**
     * 证书信息
     */
    var clientEndPointCert: String? = null

    /**
     * 证书类型
     * RSA/SM2
     */
    private var clientEndPointCertType: ClientEndPointCertType? = null

    fun check() {
        if (dxpId.isNullOrBlank()) throw Exception("DxpId不能为空")
        if (customsRegistrationName.isNullOrBlank()) throw Exception("企业海关注册名称不能为空")
        if (customsRegistrationCode.isNullOrBlank()) throw Exception("企业海关注册代码不能为空")
        if (privateKey.isNullOrBlank() && keyIndex == null) throw Exception("私钥与私钥序号不能同时为空")
        if (clientEndPointCert.isNullOrBlank()) throw Exception("证书信息不能为空")
        // 尝试加载秘钥和证书
        val clientEndPointCertTemp = getClientEndPointCert()
        getPrivateKey()
//        clientEndPointCertTemp.
    }

    // 用缓存就失败, 没找到原因
    fun getPrivateKey(): PrivateKey {
        return if (privateKey.isNullOrBlank()) {
            XmlSignatureUtils.getPrivateKey(privateKey!!, clientEndPointCertType!!)
        } else when (clientEndPointCertType) {
            ClientEndPointCertType.RSA -> XmlSignatureUtils.getRsaKey(keyIndex!!).private
            ClientEndPointCertType.SM2 -> XmlSignatureUtils.getSm2Key(keyIndex!!).private
            else -> throw ServiceException("无效的证书类型")
        }
    }

    // 用缓存就失败, 没找到原因
    fun getClientEndPointCert(): X509Certificate {
        val x509Certificate = XmlSignatureUtils.loadCertificate(clientEndPointCert!!)
        clientEndPointCertType = ClientEndPointCertType.valueOfSignatureAlgorithm(x509Certificate.sigAlgName)
        return x509Certificate
    }

    fun getClientEndPointCertType() = clientEndPointCertType!!

    fun toBaseTransfer(dxpId: String? = null) = BaseTransfer(
        copCode = customsRegistrationCode!!,
        copName = customsRegistrationName!!,
        dxpId = dxpId ?: this.dxpId!!
    )
}
