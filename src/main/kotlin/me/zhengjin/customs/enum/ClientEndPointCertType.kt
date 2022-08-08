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

package me.zhengjin.customs.enum

import me.zhengjin.common.core.exception.ServiceException

enum class ClientEndPointCertType(
    val signatureAlgorithm: String,
    val signatureMethodAlgorithm: String,
    val digestMethodAlgorithm: String,
) {
    RSA(
        "SHA1WITHRSA",
        "http://www.w3.org/2000/09/xmldsig#rsa-sha1",
        "http://www.w3.org/2000/09/xmldsig#sha1"
    ),
    SM2(
        "SM3WITHSM2",
        "http://www.chinaport.gov.cn/2022/04/xmldsig#sm2-sm3",
        "http://www.chinaport.gov.cn/2022/04/xmldsig#sm3"
    );

    companion object {
        fun valueOfSignatureAlgorithm(signatureAlgorithm: String) = when (signatureAlgorithm) {
            RSA.signatureAlgorithm -> RSA
            SM2.signatureAlgorithm -> SM2
            else -> throw ServiceException("未能识别电子口岸证书类型")
        }
    }
}
