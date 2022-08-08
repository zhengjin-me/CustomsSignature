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

package me.zhengjin

import me.zhengjin.common.core.exception.ServiceException
import me.zhengjin.common.utils.SpringBeanUtils
import me.zhengjin.customs.config.properties.BaseProperties
import me.zhengjin.customs.utils.XmlSignatureUtils
import org.slf4j.LoggerFactory
import org.springframework.amqp.core.AmqpAdmin
import org.springframework.amqp.core.Queue
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SignatureApplication(
    private val amqpAdmin: AmqpAdmin,
    private val baseProperties: BaseProperties,
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(SignatureApplication::class.java)

    override fun run(vararg args: String?) {
        // 检查配置信息
        baseProperties.check()
        // 初始化密码机连接
        XmlSignatureUtils.initSwsds(baseProperties.swsds.ip!!, baseProperties.swsds.port!!, baseProperties.swsds.password!!)
        // 获取Rabbit容器
        val container = SpringBeanUtils.getBean(SimpleMessageListenerContainer::class.java)
        // 设置消费者数量
        container.setConcurrentConsumers(baseProperties.messageConfig.size)
        val activeProfiles = baseProperties.messageConfig.keys.map {
            val config = baseProperties.messageConfig[it]
            ServiceException.requireNotNull(config) { "服务[$it]未找到配置信息" }
            // 自动声明消费队列
            if (baseProperties.autoDeclareInQueue) {
                // 队列不存在则创建
                amqpAdmin.getQueueInfo(config!!.inQueue!!).apply {
                    if (this == null) {
                        amqpAdmin.declareQueue(Queue(config.inQueue!!, true, false, false, mapOf("x-queue-mode" to "lazy")))
                    }
                }
            }
            // 自动声明生产队列
            if (baseProperties.autoDeclareOutQueue) {
                // 队列不存在则创建
                amqpAdmin.getQueueInfo(config!!.outQueue!!).apply {
                    if (this == null) {
                        amqpAdmin.declareQueue(Queue(config.outQueue!!, true, false, false, mapOf("x-queue-mode" to "lazy")))
                    }
                }
            }
            container.addQueueNames(config!!.inQueue!!)
            logger.info("已启用签名服务:[{}],来源队列:[{}],目标队列:[{}]", it, config.inQueue, config.outQueue)
            it
        }
        if (activeProfiles.isEmpty()) {
            throw Exception("未指定任何签名服务")
        }
    }
}

fun main(args: Array<String>) {
    runApplication<SignatureApplication>(*args)
}
