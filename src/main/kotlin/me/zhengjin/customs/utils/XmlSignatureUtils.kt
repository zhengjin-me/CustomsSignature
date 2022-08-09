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
    // 密码机连接初始化状态
    private var initFlag = false
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 业务节点报文命名空间前缀
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
     * 初始化服务器密码机连接
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
        logger.info("-------------- 服务器密码机[$ipAddress:$port]连接初始化完成 --------------")
        initFlag = true
    }

    /**
     * 从密码机获取私钥文件, base64编码为一行
     */
    fun getRsaPrivateKeyBase64Str(keyNo: Int = 1): String {
        val rsaPrivateKey = getRsaKey(keyNo)
        return Base64.encode(rsaPrivateKey.private.encoded)
    }

    /**
     * 从密码机获取私钥文件, base64编码为一行
     */
    fun getSm2PrivateKeyBase64Str(keyNo: Int = 1): String {
        val sm2PrivateKey = getSm2Key(keyNo)
        return Base64.encode(sm2PrivateKey.private.encoded)
    }

    /**
     * 将电子口岸分发的证书转为base64编码一行
     */
    fun getClientEndPointCertBase64Str(filePath: String): String {
        File(filePath).inputStream().use {
            val certificate = loadCertificate(it)
            return Base64.encode(certificate.encoded)
        }
    }

    /**
     * 获取密码机中的key
     * @param keyNo 密码机中的密钥序号
     */
    fun getRsaKey(keyNo: Int = 1): KeyPair {
        if (!initFlag) throw RuntimeException("请先初始化密码机连接!")
        val kpg = KeyPairGenerator.getInstance("RSA", SwxaProvider.PROVIDER_NAME)
        // int keysize = 1024、 2048、 3072、 4096、 n<<16(n为密钥序号）
        // 初始化产生密码机1号密钥
        kpg.initialize(keyNo shl 16)
        return kpg.genKeyPair() ?: throw RuntimeException("获取RSA密钥对失败!")
    }

    /**
     * 获取密码机中的key
     * @param keyNo 密码机中的密钥序号
     */
    fun getSm2Key(keyNo: Int = 1): KeyPair {
        if (!initFlag) throw RuntimeException("请先初始化密码机连接!")
        val kpg = KeyPairGenerator.getInstance("SM2", SwxaProvider.PROVIDER_NAME)
        // int keysize = 1024、 2048、 3072、 4096、 n<<16(n为密钥序号）
        // 初始化产生密码机1号密钥
        kpg.initialize(keyNo shl 16)
        return kpg.genKeyPair() ?: throw RuntimeException("获取SM2密钥对失败!")
    }

    /**
     * 得到私钥
     * @param key 密钥字符串（经过base64编码）
     */
    fun getPrivateKey(key: String, type: ClientEndPointCertType): PrivateKey {
        val keySpec = PKCS8EncodedKeySpec(key.toByteArray())
        val keyFactory = KeyFactory.getInstance(type.name)
        return keyFactory.generatePrivate(keySpec)
    }

    /**
     * 初始化一个DocumentBuilder
     *
     * @return DocumentBuilder
     */
    private fun newDocumentBuilder(): DocumentBuilder = newDocumentBuilderFactory().newDocumentBuilder()

    /**
     * 初始化一个DocumentBuilderFactory
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
     * 初始化一个Transformer
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
     * 标准化Xml的数据
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
     * 将传入的一个XML String转换成一个org.w3c.dom.Document对象返回。
     *
     * @param xmlString
     * 一个符合XML规范的字符串表达。
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
     * 从指定路定，加载签署过的证书文件
     *
     * @param filePath 证书文件路径
     * @return 证书文件
     */
    @JvmStatic
    fun loadCertificate(filePath: File): X509Certificate = loadCertificate(filePath.inputStream())

    /**
     * 加载签署过的证书文件(Base64格式)
     * 包含 -----BEGIN CERTIFICATE----- 与  -----END CERTIFICATE-----
     * @param base64Cert 证书文件内容
     * @return 证书文件
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
     * 获取xml摘要数据
     * @param waitDigestValue xml字符串
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
     * 创建待签名的xml数据
     * 二选一传入
     * @param xmlData       需要计算签名的xml数据
     * @param digestValue   已经计算的摘要内容,如果传入则不在计算直接拼接
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
        // 如果传入的xmlData不为空，则计算摘要值
        // 如果已经传入了计算好的摘要, 直接填入
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
     * 创建签名节点
     * @param signedInfo            signedInfo节点
     * @param signatureValue        签名结果
     * @param x509Certificate       公钥证书
     * @param addX509Certificate    是否添加证书到签名节点
     */
    @JvmStatic
    fun creatSignatureNode(signedInfo: String, signatureValue: String? = null, x509Certificate: X509Certificate, addX509Certificate: Boolean = true): String {
        val x509CertificateStr = Base64.encode(x509Certificate.encoded)
        return creatSignatureNode(signedInfo, signatureValue, x509Certificate.serialNumber.toString(16), x509CertificateStr, addX509Certificate)
    }

    /**
     * 创建签名节点
     * @param signedInfo                    signedInfo节点
     * @param signatureValue                签名结果
     * @param cryptoMachineCertificateNo    证书序号
     * @param x509Certificate               公钥证书
     * @param addX509Certificate            是否添加证书到签名节点
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
     * 添加签名节点
     * @param sourceXml 原始业务xml
     * @param signature 要添加的签名节点xml(通过creatSignatureNode构建)
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
     * 添加基础报文传输实体节点
     * @param sourceXml     原始业务xml
     * @param baseTransfer  基础报文传输实体节点
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
     * 签名xml摘要
     * @param privateKey  私钥
     * @param signData    待签名的摘要信息(createWaitSignData获取)
     */
    fun signatureDigest(clientEndPointCertType: ClientEndPointCertType, privateKey: PrivateKey, signData: String): String {
        val signature = Signature.getInstance(clientEndPointCertType.signatureAlgorithm, SwxaProvider.PROVIDER_NAME)
        signature.initSign(privateKey)
        signature.update(signData.toByteArray())
        return Base64.encode(signature.sign())
    }

    /**
     * 创建电子口岸终端节点
     * @param entity    业务实体
     */
    @JvmStatic
    fun createDxpMsg(entity: CEBMessage, dxpId: String): String {
        // 生成业务节点XML
        val businessXml = XmlUtils.entityToXml(entity, customsNamespacePrefixMapper)
        return createDxpMsg(businessXml, entity.getMsgType(), dxpId)
    }

    /**
     * 创建电子口岸终端节点
     * @param businessXml       业务节点XML
     */
    @JvmStatic
    fun createDxpMsg(businessXml: String, msgType: CEBMessageType, dxpId: String): String {
        val msgId = UUID.randomUUID().toString().uppercase()
        // 构造传输节点对象
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
        // 生成传输节点XML
        return XmlUtils.entityToXml(clientEndPointMessage)
    }

    /**
     * 签名xml文件
     * @param xmlStr                xml字符串
     * @param x509Certificate       电子口岸签发的企业证书
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
     * @param signature 签名信息
     * @param entity    业务实体
     */
    @JvmStatic
    fun signatureEntity(clientEndPointCertType: ClientEndPointCertType, signature: CustomsSignature, entity: CEBMessage): String {
        if (entity.baseTransfer == null) {
            entity.baseTransfer = signature.toBaseTransfer()
        }
        // 电子口岸终端节点
        return createDxpMsg(
            signature(
                clientEndPointCertType,
                // 生成业务节点XML
                XmlUtils.entityToXml(entity, customsNamespacePrefixMapper),
                signature.getClientEndPointCert(),
                signature.getPrivateKey()
            ),
            entity.getMsgType(),
            signature.dxpId!!
        )
    }

    /**
     * 生成拼多多清关信息
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
