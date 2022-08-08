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

package me.zhengjin.customs.config.properties

import me.zhengjin.common.customs.message.CEBMessageType
import me.zhengjin.customs.signatureKey.CustomsSignature
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "customize")
class BaseProperties {
    /**
     * 三未密码机配置
     */
    var swsds: SwSds = SwSds()

    /**
     * 签名信息
     */
    var signature: CustomsSignature = CustomsSignature()

    /**
     * 加签后的数据发送到哪里
     */
    var messageConfig: Map<CEBMessageType, MessageConfig> = mapOf()

    /**
     * 自动声明消费队列
     */
    var autoDeclareInQueue: Boolean = false

    /**
     * 自动声明生产队列
     */
    var autoDeclareOutQueue: Boolean = false

    /**
     * 缓存入队列关联的消息类型
     */
    private val inQueueTypeMappingCache = mutableMapOf<String, CEBMessageType>()

    class SwSds {
        var ip: String? = null
        var port: String? = "8008"
        var password: String? = "11111111"
        fun check() {
            if (ip.isNullOrBlank()) throw Exception("密码机IP不能为空")
        }
    }

    class MessageConfig {
        /**
         * 准备签名的队列
         */
        var inQueue: String? = null

        /**
         * 签名完成的队列
         */
        var outQueue: String? = null

        fun check() {
            if (inQueue.isNullOrBlank()) throw Exception("待签名队列不能为空")
            if (outQueue.isNullOrBlank()) throw Exception("签名后队列不能为空")
        }
    }

    fun beforeInitCheck() {
        swsds.check()
    }

    fun afterInitCheck() {
        signature.check()
        messageConfig.forEach {
            it.value.check()
            inQueueTypeMappingCache[it.value.inQueue!!] = it.key
        }
    }

    /**
     * 根据入队列类型获取对应的消息类型
     */
    fun getMessageTypeByInQueue(inQueue: String): CEBMessageType {
        return inQueueTypeMappingCache[inQueue] ?: throw Exception("没有找到入队列[$inQueue]对应的类型")
    }
}
