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

package me.zhengjin.customs.utils

import cn.hutool.core.codec.Base64
import cn.hutool.core.io.IoUtil
import com.sansec.device.bean.config.userConfig
import com.sansec.jce.provider.SwxaProvider
import com.sun.xml.bind.marshaller.NamespacePrefixMapper
import me.zhengjin.common.customs.client.dxp.AddInfo
import me.zhengjin.common.customs.client.dxp.ClientEndPointMessage
import me.zhengjin.common.customs.client.dxp.TransInfo
import me.zhengjin.common.customs.message.CEBMessage
import me.zhengjin.common.customs.message.CEBMessageType
import me.zhengjin.common.customs.message.extend.BaseTransfer
import me.zhengjin.common.utils.XmlUtils
import me.zhengjin.customs.enum.ClientEndPointCertType
import me.zhengjin.customs.enum.IEType
import me.zhengjin.customs.signatureKey.CustomsSignature
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Security
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.crypto.OctetStreamData
import javax.xml.crypto.dsig.CanonicalizationMethod
import javax.xml.crypto.dsig.TransformService
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object XmlSignatureUtils {
    // ??????????????????????????????
    private var initFlag = false
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * ????????????????????????????????????
     */
    val customsNamespacePrefixMapper = object : NamespacePrefixMapper() {
        override fun getPreferredPrefix(
            namespaceUri: String?,
            suggestion: String?,
            requirePrefix: Boolean
        ): String? {
            return when (namespaceUri) {
                "http://www.w3.org/2001/XMLSchema-instance" -> "xsi"
                "http://www.w3.org/2000/09/xmldsig#" -> "ds"
                "http://www.chinaport.gov.cn/ceb" -> "ceb"
//                "http://www.chinaport.gov.cn/dxp" -> "dxp"
                else -> suggestion
            }
        }
    }

    /**
     * ?????????????????????????????????
     */
    @JvmStatic
    fun initSwsds(ipAddress: String = "221.215.48.11", port: String = "8008", passwrod: String = "11111111") {
        val userConfig = userConfig()
        userConfig.logLevel = "2"
        userConfig.ipAddress = ipAddress
        userConfig.poolSize = "10"
        userConfig.port = port
        userConfig.passwrod = passwrod
//        Security.removeProvider("SunRsaSign")
//        Security.insertProviderAt(SwxaProvider(userConfig), 2)
        Security.addProvider(SwxaProvider(userConfig))
        Security.addProvider(BouncyCastleProvider())
        logger.info("-------------- ??????????????????[$ipAddress:$port]????????????????????? --------------")
        initFlag = true
    }

    /**
     * ??????????????????????????????, base64???????????????
     */
    fun getRsaPrivateKeyBase64Str(keyNo: Int = 1): String {
        val rsaPrivateKey = getRsaKey(keyNo)
        return Base64.encode(rsaPrivateKey.private.encoded)
    }

    /**
     * ??????????????????????????????, base64???????????????
     */
    fun getSm2PrivateKeyBase64Str(keyNo: Int = 1): String {
        val sm2PrivateKey = getSm2Key(keyNo)
        return Base64.encode(sm2PrivateKey.private.encoded)
    }

    /**
     * ????????????????????????????????????base64????????????
     */
    fun getClientEndPointCertBase64Str(filePath: String): String {
        File(filePath).inputStream().use {
            val certificate = loadCertificate(it)
            return Base64.encode(certificate.encoded)
        }
    }

    /**
     * ?????????????????????key
     * @param keyNo ???????????????????????????
     */
    fun getRsaKey(keyNo: Int = 1): KeyPair {
        if (!initFlag) throw RuntimeException("??????????????????????????????!")
        val kpg = KeyPairGenerator.getInstance("RSA", SwxaProvider.PROVIDER_NAME)
        // int keysize = 1024??? 2048??? 3072??? 4096??? n<<16(n??????????????????
        // ????????????????????????1?????????
        kpg.initialize(keyNo shl 16)
        return kpg.genKeyPair() ?: throw RuntimeException("??????RSA???????????????!")
    }

    /**
     * ?????????????????????key
     * @param keyNo ???????????????????????????
     */
    fun getSm2Key(keyNo: Int = 1): KeyPair {
        if (!initFlag) throw RuntimeException("??????????????????????????????!")
        val kpg = KeyPairGenerator.getInstance("SM2", SwxaProvider.PROVIDER_NAME)
        // int keysize = 1024??? 2048??? 3072??? 4096??? n<<16(n??????????????????
        // ????????????????????????1?????????
        kpg.initialize(keyNo shl 16)
        return kpg.genKeyPair() ?: throw RuntimeException("??????SM2???????????????!")
    }

    /**
     * ????????????
     * @param key ????????????????????????base64?????????
     */
    fun getPrivateKey(key: String, type: ClientEndPointCertType): PrivateKey {
        val keySpec = PKCS8EncodedKeySpec(key.toByteArray())
        val keyFactory = KeyFactory.getInstance(type.name)
        return keyFactory.generatePrivate(keySpec)
    }

    /**
     * ???????????????DocumentBuilder
     *
     * @return DocumentBuilder
     */
    private fun newDocumentBuilder(): DocumentBuilder = newDocumentBuilderFactory().newDocumentBuilder()

    /**
     * ???????????????DocumentBuilderFactory
     *
     * @return DocumentBuilderFactory
     */
    private fun newDocumentBuilderFactory(): DocumentBuilderFactory {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        return factory
    }

    /**
     * ???????????????Transformer
     *
     * @return Transformer
     */
    private fun newTransformer(): Transformer {
        val transFactory = TransformerFactory.newInstance()
        transFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        transFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
        return transFactory.newTransformer()
    }

    /**
     * ?????????Xml?????????
     *
     * @param str
     */
    @JvmStatic
    fun c14nTransform(str: String): String {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        val ts = TransformService.getInstance(CanonicalizationMethod.INCLUSIVE, "DOM")
        val newData = ts.transform(OctetStreamData(ByteArrayInputStream(bytes)), null) as OctetStreamData
        val baos = ByteArrayOutputStream()
        newData.octetStream.use {
            IoUtil.copy(it, baos)
        }
        return baos.toString(StandardCharsets.UTF_8.name())
    }

    /**
     * ??????????????????XML String???????????????org.w3c.dom.Document???????????????
     *
     * @param xmlString
     * ????????????XML???????????????????????????
     * @return a Document
     */
    private fun parseXMLDocument(xmlString: String): Document = newDocumentBuilder().parse(InputSource(StringReader(xmlString)))

    fun loadCertificate(inputStream: InputStream): X509Certificate {
        return inputStream.use {
            val certificateFactory = CertificateFactory.getInstance("X.509", "BC")
            certificateFactory.generateCertificate(it) as X509Certificate
        }
    }

    /**
     * ????????????????????????????????????????????????
     *
     * @param filePath ??????????????????
     * @return ????????????
     */
    @JvmStatic
    fun loadCertificate(filePath: File): X509Certificate = loadCertificate(filePath.inputStream())

    /**
     * ??????????????????????????????(Base64??????)
     * ?????? -----BEGIN CERTIFICATE----- ???  -----END CERTIFICATE-----
     * @param base64Cert ??????????????????
     * @return ????????????
     */
    @JvmStatic
    fun loadCertificate(base64Cert: String): X509Certificate {
        return if (base64Cert.contains("-----BEGIN CERTIFICATE-----") && base64Cert.contains("-----END CERTIFICATE-----")) {
            loadCertificate(base64Cert.byteInputStream())
        } else {
            loadCertificate(Base64.decode(base64Cert).inputStream())
        }
    }

    /**
     * ??????xml????????????
     * @param waitDigestValue xml?????????
     */
    @JvmStatic
    fun calcDigestValue(clientEndPointCertType: ClientEndPointCertType, waitDigestValue: String): String {
        val messageDigest = when (clientEndPointCertType) {
            ClientEndPointCertType.RSA -> MessageDigest.getInstance("SHA1", SwxaProvider.PROVIDER_NAME)
            ClientEndPointCertType.SM2 -> MessageDigest.getInstance("SM3WithoutKey", SwxaProvider.PROVIDER_NAME)
        }
        messageDigest.update(waitDigestValue.toByteArray())
        return Base64.encode(messageDigest.digest())
    }

    /**
     * ??????????????????xml??????
     * ???????????????
     * @param xmlData       ?????????????????????xml??????
     * @param digestValue   ???????????????????????????,???????????????????????????????????????
     */
    @JvmStatic
    fun createSignedInfo(clientEndPointCertType: ClientEndPointCertType, xmlData: String? = null, digestValue: String? = null): String {
        val builder = newDocumentBuilder()
        val document = builder.newDocument()

        val signedInfoElm = document.createElement("ds:SignedInfo")
        signedInfoElm.setAttribute("xmlns:ceb", "http://www.chinaport.gov.cn/ceb")
        signedInfoElm.setAttribute("xmlns:ds", "http://www.w3.org/2000/09/xmldsig#")
        signedInfoElm.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")

        val canonicalizationMethodElm = document.createElement("ds:CanonicalizationMethod")
        canonicalizationMethodElm.setAttribute("Algorithm", "http://www.w3.org/TR/2001/REC-xml-c14n-20010315#WithComments")
        canonicalizationMethodElm.appendChild(document.createTextNode(""))

        val signatureMethodElm = document.createElement("ds:SignatureMethod")
        signatureMethodElm.setAttribute("Algorithm", clientEndPointCertType.signatureMethodAlgorithm)
        signatureMethodElm.appendChild(document.createTextNode(""))

        val referenceElm = document.createElement("ds:Reference")
        referenceElm.setAttribute("URI", "")

        val transformsElm = document.createElement("ds:Transforms")

        val transformElm = document.createElement("ds:Transform")
        transformElm.setAttribute("Algorithm", "http://www.w3.org/2000/09/xmldsig#enveloped-signature")
        transformElm.appendChild(document.createTextNode(""))

        val digestMethodElm = document.createElement("ds:DigestMethod")
        digestMethodElm.setAttribute("Algorithm", clientEndPointCertType.digestMethodAlgorithm)
        digestMethodElm.appendChild(document.createTextNode(""))

        val digestValueElm = document.createElement("ds:DigestValue")
        // ???????????????xmlData??????????????????????????????
        // ???????????????????????????????????????, ????????????
        digestValueElm.appendChild(document.createTextNode(digestValue ?: xmlData?.let { calcDigestValue(clientEndPointCertType, it) } ?: ""))

        transformsElm.appendChild(transformElm)

        referenceElm.appendChild(transformsElm)
        referenceElm.appendChild(digestMethodElm)
        referenceElm.appendChild(digestValueElm)

        signedInfoElm.appendChild(canonicalizationMethodElm)
        signedInfoElm.appendChild(signatureMethodElm)
        signedInfoElm.appendChild(referenceElm)

        document.appendChild(signedInfoElm)

        val transformer = newTransformer()
        val xmlStr = StringWriter().use { sw ->
            val strResult = StreamResult(sw)
            transformer.transform(DOMSource(document), strResult)
            strResult.writer.toString()
        }
        val index = xmlStr.indexOf("<", 1)
        return c14nTransform(xmlStr.substring(index))
    }

    /**
     * ??????????????????
     * @param signedInfo            signedInfo??????
     * @param signatureValue        ????????????
     * @param x509Certificate       ????????????
     * @param addX509Certificate    ?????????????????????????????????
     */
    @JvmStatic
    fun creatSignatureNode(signedInfo: String, signatureValue: String? = null, x509Certificate: X509Certificate, addX509Certificate: Boolean = true): String {
        val x509CertificateStr = Base64.encode(x509Certificate.encoded)
        return creatSignatureNode(signedInfo, signatureValue, x509Certificate.serialNumber.toString(16), x509CertificateStr, addX509Certificate)
    }

    /**
     * ??????????????????
     * @param signedInfo                    signedInfo??????
     * @param signatureValue                ????????????
     * @param cryptoMachineCertificateNo    ????????????
     * @param x509Certificate               ????????????
     * @param addX509Certificate            ?????????????????????????????????
     */
    @JvmStatic
    fun creatSignatureNode(signedInfo: String, signatureValue: String? = null, cryptoMachineCertificateNo: String, x509Certificate: String, addX509Certificate: Boolean = true): String {

        val builder = newDocumentBuilder()
        val document = builder.newDocument()
        val signatureElm = document.createElement("ds:Signature")
        signatureElm.setAttribute("xmlns:ds", "http://www.w3.org/2000/09/xmldsig#")

        val signatureValueElm = document.createElement("ds:SignatureValue")
        signatureValueElm.appendChild(document.createTextNode(signatureValue?.replace("\r\n", "") ?: ""))

        val keyInfoElm = document.createElement("ds:KeyInfo")

        val keyNameElm = document.createElement("ds:KeyName")
        keyNameElm.appendChild(document.createTextNode(cryptoMachineCertificateNo))
        keyInfoElm.appendChild(keyNameElm)

        if (addX509Certificate) {
            val x509DataElm = document.createElement("ds:X509Data")
            val x509CertificateElm = document.createElement("ds:X509Certificate")
            x509CertificateElm.appendChild(document.createTextNode(x509Certificate))
            x509DataElm.appendChild(x509CertificateElm)
            keyInfoElm.appendChild(x509DataElm)
        }

        val builderSignInfo = newDocumentBuilder()
        val docSignInfo = builderSignInfo.parse(InputSource(StringReader(signedInfo)))
        val docSignEle = docSignInfo.documentElement

        val signedInfoEle = document.importNode(docSignEle, true) as Element
        signatureElm.appendChild(signedInfoEle)
        signatureElm.appendChild(signatureValueElm)
        signatureElm.appendChild(keyInfoElm)

        document.appendChild(signatureElm)

        val transformer = newTransformer()
        val xmlStr = StringWriter().use { sw ->
            val strResult = StreamResult(sw)
            transformer.transform(DOMSource(document), strResult)
            strResult.writer.toString()
        }

        val index = xmlStr.indexOf("<", 1)
        return c14nTransform(xmlStr.substring(index))
    }

    /**
     * ??????????????????
     * @param sourceXml ????????????xml
     * @param signature ????????????????????????xml(??????creatSignatureNode??????)
     */
    @JvmStatic
    fun appendSignature(sourceXml: String, signature: String): String {
        val doc = parseXMLDocument(sourceXml)
        val docEle = doc.documentElement

        val docNode = parseXMLDocument(signature)
        val docNodeEle = docNode.documentElement

        val signatureEle = doc.importNode(docNodeEle, true) as Element
        docEle.appendChild(signatureEle)

        val transformer = newTransformer()
        val xmlData = StringWriter().use { sw ->
            val strResult = StreamResult(sw)
            transformer.transform(DOMSource(docEle), strResult)
            strResult.writer.toString()
        }
        val index = xmlData.indexOf("<", 1)
        return c14nTransform(xmlData.substring(index))
    }

    /**
     * ????????????????????????????????????
     * @param sourceXml     ????????????xml
     * @param baseTransfer  ??????????????????????????????
     */
    @JvmStatic
    fun appendBaseTransfer(sourceXml: String, baseTransfer: BaseTransfer): String {
        val builder = newDocumentBuilder()
        val document = builder.newDocument()
        val baseTransferElm = document.createElement("ceb:BaseTransfer")

        val copCodeElm = document.createElement("ceb:copCode")
        copCodeElm.appendChild(document.createTextNode(baseTransfer.copCode))
        baseTransferElm.appendChild(copCodeElm)

        val copNameElm = document.createElement("ceb:copName")
        copNameElm.appendChild(document.createTextNode(baseTransfer.copName))
        baseTransferElm.appendChild(copNameElm)

        val dxpModeElm = document.createElement("ceb:dxpMode")
        dxpModeElm.appendChild(document.createTextNode(baseTransfer.dxpMode))
        baseTransferElm.appendChild(dxpModeElm)

        val dxpIdElm = document.createElement("ceb:dxpId")
        dxpIdElm.appendChild(document.createTextNode(baseTransfer.dxpId))
        baseTransferElm.appendChild(dxpIdElm)

        val doc = parseXMLDocument(sourceXml)
        val docEle = doc.documentElement

        val baseTransferEle = doc.importNode(baseTransferElm, true) as Element
        docEle.appendChild(baseTransferEle)

        val transformer = newTransformer()
        val xmlData = StringWriter().use { sw ->
            val strResult = StreamResult(sw)
            transformer.transform(DOMSource(docEle), strResult)
            strResult.writer.toString()
        }
        val index = xmlData.indexOf("<", 1)
        return c14nTransform(xmlData.substring(index))
    }

    /**
     * ??????xml??????
     * @param privateKey  ??????
     * @param signData    ????????????????????????(createWaitSignData??????)
     */
    fun signatureDigest(clientEndPointCertType: ClientEndPointCertType, privateKey: PrivateKey, signData: String): String {
        val signature = Signature.getInstance(clientEndPointCertType.signatureAlgorithm, SwxaProvider.PROVIDER_NAME)
        signature.initSign(privateKey)
        signature.update(signData.toByteArray())
        return Base64.encode(signature.sign())
    }

    /**
     * ??????????????????????????????
     * @param entity    ????????????
     */
    @JvmStatic
    fun createDxpMsg(entity: CEBMessage, dxpId: String): String {
        // ??????????????????XML
        val businessXml = XmlUtils.entityToXml(entity, customsNamespacePrefixMapper)
        return createDxpMsg(businessXml, entity.getMsgType(), dxpId)
    }

    /**
     * ??????????????????????????????
     * @param businessXml       ????????????XML
     */
    @JvmStatic
    fun createDxpMsg(businessXml: String, msgType: CEBMessageType, dxpId: String): String {
        val msgId = UUID.randomUUID().toString().uppercase()
        // ????????????????????????
        val clientEndPointMessage = ClientEndPointMessage(
            TransInfo(
                msgId = msgId,
                senderId = dxpId,
                receiverIds = arrayListOf(
                    IEType.getIEType(msgType).receiverId
                ),
                createTime = Date(),
                msgType = msgType
            ),
            businessXml,
            AddInfo("${msgType}_$msgId.xml")
        )
        // ??????????????????XML
        return XmlUtils.entityToXml(clientEndPointMessage)
    }

    /**
     * ??????xml??????
     * @param xmlStr                xml?????????
     * @param x509Certificate       ?????????????????????????????????
     */
    @JvmStatic
    fun signature(
        clientEndPointCertType: ClientEndPointCertType,
        xmlStr: String,
        x509Certificate: X509Certificate,
        privateKey: PrivateKey,
    ): String {
        val digestValue = calcDigestValue(clientEndPointCertType, xmlStr)
        return appendSignature(
            xmlStr,
            creatSignatureNode(
                createSignedInfo(clientEndPointCertType = clientEndPointCertType, digestValue = digestValue),
                signatureDigest(clientEndPointCertType, privateKey, digestValue),
                x509Certificate
            )
        )
    }

    /**
     * @param signature ????????????
     * @param entity    ????????????
     */
    @JvmStatic
    fun signatureEntity(clientEndPointCertType: ClientEndPointCertType, signature: CustomsSignature, entity: CEBMessage): String {
        if (entity.baseTransfer == null) {
            entity.baseTransfer = signature.toBaseTransfer()
        }
        // ????????????????????????
        return createDxpMsg(
            signature(
                clientEndPointCertType,
                // ??????????????????XML
                XmlUtils.entityToXml(entity, customsNamespacePrefixMapper),
                signature.getClientEndPointCert(),
                signature.getPrivateKey()
            ),
            entity.getMsgType(),
            signature.dxpId!!
        )
    }

    /**
     * ???????????????????????????
     */
    @JvmStatic
    fun createPinDuoDuoDeclareData(
        businessXml: String,
        msgType: CEBMessageType,
        dxpId: String,
        x509Certificate: X509Certificate,
        baseTransfer: BaseTransfer,
        clientEndPointCertType: ClientEndPointCertType
    ): String {
        return createDxpMsg(
            appendSignature(
                if (businessXml.contains("<ceb:BaseTransfer>", true)) businessXml else appendBaseTransfer(businessXml, baseTransfer),
                creatSignatureNode(createSignedInfo(clientEndPointCertType), null, x509Certificate)
            ),
            msgType, dxpId
        )
    }
}
