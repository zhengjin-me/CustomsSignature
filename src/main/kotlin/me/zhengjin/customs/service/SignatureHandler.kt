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

package me.zhengjin.customs.service

import com.rabbitmq.client.Channel
import me.zhengjin.common.customs.message.CEBMessageType
import me.zhengjin.common.utils.XmlUtils
import me.zhengjin.customs.config.properties.BaseProperties
import me.zhengjin.customs.extend.signature
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageDeliveryMode
import org.springframework.amqp.core.MessageProperties
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener
import org.springframework.stereotype.Service

@Service
class SignatureHandler(
    private val rabbitTemplate: RabbitTemplate,
    private val baseProperties: BaseProperties
) : ChannelAwareMessageListener {
    private val logger = LoggerFactory.getLogger(SignatureHandler::class.java)

    /**
     * 生成发送到MQ的消息
     */
    fun generatorMessage(signature: String) = Message(
        signature.toByteArray(),
        MessageProperties().also {
            it.setHeader("__TypeID__", "java.lang.String")
            it.deliveryMode = MessageDeliveryMode.PERSISTENT
            it.priority = 0
            it.contentEncoding = "UTF-8"
            it.contentType = "text/xml"
        }
    )

    fun signature(type: CEBMessageType, xmlStr: String) {
        // 1.转换为实体对象
        val cebMessage = XmlUtils.xmlToEntity(type.clazz, xmlStr)
        if (cebMessage != null) {
            // 2.订单报文数据签名
            val signature = cebMessage.signature(baseProperties.signature.getClientEndPointCertType(), baseProperties.signature)
            // 3.将签名后的报文数据发送到数据交换通道
            rabbitTemplate.send(
                baseProperties.messageConfig[type]!!.outQueue!!,
                generatorMessage(signature)
            )
        } else {
            logger.error("反序列化消息${type.name}失败, 跳过处理, 消息原文:[$xmlStr]")
        }
    }

    override fun onMessage(message: Message, channel: Channel?) {
        val xmlStr = String(message.body)
        try {
            val cebMessageType = baseProperties.getMessageTypeByInQueue(message.messageProperties.consumerQueue)
            signature(cebMessageType, xmlStr)
            channel?.basicAck(message.messageProperties.deliveryTag, false)
        } catch (ignore: Exception) {
            logger.error("消息处理失败, 消息原文:[$xmlStr]", ignore)
            channel?.basicNack(message.messageProperties.deliveryTag, false, true)
        }
    }
}
