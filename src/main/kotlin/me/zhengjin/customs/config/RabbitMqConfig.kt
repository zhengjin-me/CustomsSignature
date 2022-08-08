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

package me.zhengjin.customs.config

import me.zhengjin.customs.service.SignatureHandler
import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.rabbit.annotation.EnableRabbit
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer
import org.springframework.amqp.support.converter.MessageConverter
import org.springframework.amqp.support.converter.SimpleMessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@EnableRabbit
@Configuration
class RabbitMqConfig {
    @Bean
    fun messageConverter(): MessageConverter {
        return SimpleMessageConverter()
    }

//    /**
//     * 自定义队列容器工厂
//     * @param connectionFactory
//     * @return
//     */
//    @Bean
//    fun rabbitListenerContainerFactory(connectionFactory: ConnectionFactory): SimpleRabbitListenerContainerFactory {
//        val factory = SimpleRabbitListenerContainerFactory()
//        factory.setConnectionFactory(connectionFactory)
//        factory.setMessageConverter(messageConverter())
//        // 消息发送失败策略 拒绝
//        factory.setDefaultRequeueRejected(false)
//        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL)
//        return factory
//    }

    /**
     * 自定义队列容器
     * @param connectionFactory
     * @return
     */
    @Bean
    fun simpleMessageListenerContainer(
        connectionFactory: ConnectionFactory,
        signatureHandler: SignatureHandler,
    ): SimpleMessageListenerContainer {
        val container = SimpleMessageListenerContainer()
        container.connectionFactory = connectionFactory
        container.isExposeListenerChannel = true
        container.acknowledgeMode = AcknowledgeMode.MANUAL
        container.setPrefetchCount(250)
        // 这里暂时不设置, 等启动完成后设置
//        container.setConcurrentConsumers(baseProperties.messageConfig.size)
//        container.setQueueNames(*baseProperties.messageConfig.values.map { it.inQueue }.toTypedArray())
        // 消息发送失败策略 拒绝
        container.setDefaultRequeueRejected(false)
        container.setAutoDeclare(false)
        container.setMessageListener(signatureHandler)
        container.setReceiveTimeout(60000)
        return container
    }
}
