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

import me.zhengjin.common.customs.message.CEBMessageType

/**
 * 进出口业务类型
 */
enum class IEType(val receiverId: String) {
    // 跨境进口
    I("DXPEDCCEB0000002"),

    // 跨境出口
    E("DXPEDCCEB0000003");

    companion object {
        fun getIEType(msgType: CEBMessageType): IEType {
            return when (msgType) {
                CEBMessageType.CEB311Message,
                CEBMessageType.CEB312Message,
                CEBMessageType.CEB411Message,
                CEBMessageType.CEB412Message,
                CEBMessageType.CEB511Message,
                CEBMessageType.CEB512Message,
                CEBMessageType.CEB513Message,
                CEBMessageType.CEB514Message,
                CEBMessageType.CEB621Message,
                CEBMessageType.CEB622Message,
                CEBMessageType.CEB623Message,
                CEBMessageType.CEB624Message,
                CEBMessageType.CEB625Message,
                CEBMessageType.CEB626Message,
                CEBMessageType.CEB711Message,
                CEBMessageType.CEB712Message,
                CEBMessageType.CEB816Message,
                CEBMessageType.CEB818Message -> I
                CEBMessageType.CEB303Message,
                CEBMessageType.CEB304Message,
                CEBMessageType.CEB403Message,
                CEBMessageType.CEB404Message,
                CEBMessageType.CEB505Message,
                CEBMessageType.CEB506Message,
                CEBMessageType.CEB507Message,
                CEBMessageType.CEB508Message,
                CEBMessageType.CEB509Message,
                CEBMessageType.CEB510Message,
                CEBMessageType.CEB603Message,
                CEBMessageType.CEB604Message,
                CEBMessageType.CEB605Message,
                CEBMessageType.CEB606Message,
                CEBMessageType.CEB607Message,
                CEBMessageType.CEB608Message,
                CEBMessageType.CEB701Message,
                CEBMessageType.CEB702Message,
                CEBMessageType.CEB792Message -> E
                else -> throw IllegalArgumentException("不支持的报文类型")
            }
        }
    }
}
