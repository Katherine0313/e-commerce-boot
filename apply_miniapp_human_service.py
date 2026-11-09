#!/usr/bin/env python3
# -*- coding: utf-8 -*-
r"""
把小程序（e-commerce-uniapp）的「联系人工客服」从微信原生 contact 按钮
改为调用后端 /api/mall/ai/handoff 人工客服系统。

用法：
    cd d:\\study\\my-workspace\\wechat-workspace\\e-commerce-uniapp
    python ..\\e-commerce-boot\\apply_miniapp_human_service.py
"""

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent.parent / "e-commerce-uniapp"

REPLACEMENTS = [
    # 1. utils/server-api.js：兼容 Python 后端的 {code:0, data}，支持 silent 轮询
    (
        ROOT / "utils" / "server-api.js",
        """\t// 后端约定：{ success, data } 成功，{ success, message } 失败
\t\tif(res.data.success){
\t\t\treturn res.data.data
\t\t}else{
\t\t\tthrow res.data
\t\t}
\t}).catch((err) => {
\t\t// 统一错误提示，再抛给调用方
\t\tuni.showToast({
\t\t\ttitle: (err && (err.message || err.msg || err.errMsg)) || '请求失败，请重试',
\t\t\ticon: 'none'
\t\t})
\t\tthrow err
\t})""",
        """\t// 后端约定：Java 用 { success, data }，Python AI 用 { code:0, data }
\t\tconst ok = res.data.success === true || res.data.code === 0
\t\tif(ok){
\t\t\treturn res.data.data
\t\t}else{
\t\t\tthrow res.data
\t\t}
\t}).catch((err) => {
\t\t// 统一错误提示，再抛给调用方；options.silent 时静默（如轮询）
\t\tif(!options.silent){
\t\t\tuni.showToast({
\t\t\t\ttitle: (err && (err.message || err.msg || err.errMsg)) || '请求失败，请重试',
\t\t\t\ticon: 'none'
\t\t\t})
\t\t}
\t\tthrow err
\t})"""
    ),

    # 2. api/agent.js：新增 handoff / getConversation / getMessages
    (
        ROOT / "api" / "agent.js",
        """import { BASE_URL } from '../utils/server-api.js'
import { getToken } from '../utils/token.js'""",
        """import { BASE_URL, request } from '../utils/server-api.js'
import { getToken } from '../utils/token.js'"""
    ),
    (
        ROOT / "api" / "agent.js",
        """// UTF-8 解码（小程序不一定有 TextDecoder；按完整事件解码，不会截断中文）
function utf8Decode(bytes) {
\tlet out = ''
\tfor (let i = 0; i < bytes.length;) {
\t\tconst b = bytes[i++]
\t\tif (b < 0x80) {
\t\t\tout += String.fromCharCode(b)
\t\t} else if (b < 0xE0) {
\t\t\tout += String.fromCharCode(((b & 0x1F) << 6) | (bytes[i++] & 0x3F))
\t\t} else if (b < 0xF0) {
\t\t\tout += String.fromCharCode(((b & 0x0F) << 12) |
\t\t\t\t((bytes[i++] & 0x3F) << 6) |
\t\t\t\t(bytes[i++] & 0x3F))
\t\t} else {
\t\t\tconst cp = ((b & 0x07) << 18) |
\t\t\t\t((bytes[i++] & 0x3F) << 12) |
\t\t\t\t((bytes[i++] & 0x3F) << 6) |
\t\t\t\t(bytes[i++] & 0x3F)
\t\t\tconst v = cp - 0x10000
\t\t\tout += String.fromCharCode(0xD800 + (v >> 10), 0xDC00 + (v & 0x3FF))
\t\t}
\t}
\treturn out
}""",
        """// UTF-8 解码（小程序不一定有 TextDecoder；按完整事件解码，不会截断中文）
function utf8Decode(bytes) {
\tlet out = ''
\tfor (let i = 0; i < bytes.length;) {
\t\tconst b = bytes[i++]
\t\tif (b < 0x80) {
\t\t\tout += String.fromCharCode(b)
\t\t} else if (b < 0xE0) {
\t\t\tout += String.fromCharCode(((b & 0x1F) << 6) | (bytes[i++] & 0x3F))
\t\t} else if (b < 0xF0) {
\t\t\tout += String.fromCharCode(((b & 0x0F) << 12) |
\t\t\t\t((bytes[i++] & 0x3F) << 6) |
\t\t\t\t(bytes[i++] & 0x3F))
\t\t} else {
\t\t\tconst cp = ((b & 0x07) << 18) |
\t\t\t\t((bytes[i++] & 0x3F) << 12) |
\t\t\t\t((bytes[i++] & 0x3F) << 6) |
\t\t\t\t(bytes[i++] & 0x3F)
\t\t\tconst v = cp - 0x10000
\t\t\tout += String.fromCharCode(0xD800 + (v >> 10), 0xDC00 + (v & 0x3FF))
\t\t}
\t}
\treturn out
}

const JSON_HEADER = { 'content-type': 'application/json' }

/**
 * 转人工：创建/复用商城 AI 会话并标记 human_mode。
 * 之后在 human_mode 下重复调用本接口，会把 message 存为当前会话的用户消息。
 */
export function handoff(conversationId, message) {
\treturn request('/api/mall/ai/handoff', {
\t\tmethod: 'POST',
\t\theader: JSON_HEADER,
\t\tdata: { conversationId, message }
\t})
}

/** 查询单个会话状态（含 humanMode） */
export function getConversation(id) {
\treturn request(`/api/mall/ai/conversations/${id}`, { silent: true })
}

/** 查询会话消息列表 */
export function getMessages(id) {
\treturn request(`/api/mall/ai/conversations/${id}/messages`, { silent: true })
}"""
    ),

    # 3. store/ai-chat.js：支持人工模式
    (
        ROOT / "store" / "ai-chat.js",
        """import {
\taskAgent
} from '../api/agent.js'""",
        """import {
\taskAgent,
\thandoff,
\tgetConversation,
\tgetMessages
} from '../api/agent.js'"""
    ),
    (
        ROOT / "store" / "ai-chat.js",
        """const newSessionId = () =>
\t'float-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10)""",
        """const newSessionId = () =>
\t'float-' + Date.now() + '-' + Math.random().toString(36).slice(2, 10)

const HUMAN_POLL_INTERVAL = 3000"""
    ),
    (
        ROOT / "store" / "ai-chat.js",
        """\tstate: () => ({
\t\tvisible: false, // 聊天面板是否打开
\t\tsessionId: '',
\t\tsending: false,
\t\tmessages: greeting(),
\t\t// 悬浮球位置（px）：null 表示还没拖过，由组件按右侧中间初始化
\t\tballLeft: null,
\t\tballTop: null
\t}),""",
        """\tstate: () => ({
\t\tvisible: false, // 聊天面板是否打开
\t\tsessionId: '',
\t\tsending: false,
\t\tmessages: greeting(),
\t\t// 悬浮球位置（px）：null 表示还没拖过，由组件按右侧中间初始化
\t\tballLeft: null,
\t\tballTop: null,
\t\t// 人工客服相关
\t\tconversationId: null,
\t\thumanMode: false,
\t\thumanTimer: null
\t}),"""
    ),
    (
        ROOT / "store" / "ai-chat.js",
        """\t\treset() {
\t\t\tthis.sessionId = newSessionId()
\t\t\tthis.messages = greeting()
\t\t\tthis.sending = false
\t\t},""",
        """\t\treset() {
\t\t\tthis.stopHumanPolling()
\t\t\tthis.sessionId = newSessionId()
\t\t\tthis.conversationId = null
\t\t\tthis.humanMode = false
\t\t\tthis.messages = greeting()
\t\t\tthis.sending = false
\t\t},

\t\t// ===== 人工客服 =====
\t\tstartHumanPolling() {
\t\t\tthis.stopHumanPolling()
\t\t\tthis.humanTimer = setInterval(() => this.refreshHuman(), HUMAN_POLL_INTERVAL)
\t\t},
\t\tstopHumanPolling() {
\t\t\tif (this.humanTimer) {
\t\t\t\tclearInterval(this.humanTimer)
\t\t\t\tthis.humanTimer = null
\t\t\t}
\t\t},
\t\tasync refreshHuman() {
\t\t\tif (!this.conversationId) return
\t\t\tconst id = this.conversationId
\t\t\ttry {
\t\t\t\tconst [msgRes, convRes] = await Promise.all([
\t\t\t\t\tgetMessages(id),
\t\t\t\t\tgetConversation(id)
\t\t\t\t])
\t\t\t\tif (id !== this.conversationId) return

\t\t\t\tconst list = (msgRes || []).filter(m => m.type === 'user' || m.type === 'assistant')
\t\t\t\tthis.messages = list.map(m => ({
\t\t\t\t\tid: m.id,
\t\t\t\t\trole: m.type === 'user' ? 'user' : 'bot',
\t\t\t\t\ttext: m.content || '',
\t\t\t\t\thuman: m.type === 'assistant'
\t\t\t\t}))

\t\t\t\t// 客服结束会话后切回 AI
\t\t\t\tif (convRes && !convRes.humanMode && this.humanMode) {
\t\t\t\t\tthis.humanMode = false
\t\t\t\t\tthis.stopHumanPolling()
\t\t\t\t\tthis.messages.push({
\t\t\t\t\t\tid: 'local-close',
\t\t\t\t\t\trole: 'bot',
\t\t\t\t\t\ttext: '人工客服已结束本次会话，如有其他问题可以继续咨询 AI 客服。',
\t\t\t\t\t\thuman: true
\t\t\t\t\t})
\t\t\t\t}
\t\t\t} catch (e) {
\t\t\t\t// 轮询失败静默，下一轮重试
\t\t\t}
\t\t},
\t\tasync handoffToHuman(content) {
\t\t\tif (this.sending) return false
\t\t\tthis.sending = true
\t\t\ttry {
\t\t\t\tconst res = await handoff(this.conversationId || undefined, content || '人工客服')
\t\t\t\tif (res.conversationId) {
\t\t\t\t\tthis.conversationId = res.conversationId
\t\t\t\t\tthis.humanMode = true
\t\t\t\t}
\t\t\t\tawait this.refreshHuman()
\t\t\t\tthis.startHumanPolling()
\t\t\t\treturn true
\t\t\t} catch (e) {
\t\t\t\tthis.messages.push({
\t\t\t\t\tid: 'local-handoff-err',
\t\t\t\t\trole: 'bot',
\t\t\t\t\ttext: '转人工失败，请检查网络后重试',
\t\t\t\t\thuman: true
\t\t\t\t})
\t\t\t\treturn false
\t\t\t} finally {
\t\t\t\tthis.sending = false
\t\t\t}
\t\t},
\t\tasync sendHumanMessage(content) {
\t\t\tif (this.sending) return false
\t\t\tthis.sending = true
\t\t\ttry {
\t\t\t\tawait handoff(this.conversationId, content)
\t\t\t\tawait this.refreshHuman()
\t\t\t\treturn true
\t\t\t} catch (e) {
\t\t\t\treturn false
\t\t\t} finally {
\t\t\t\tthis.sending = false
\t\t\t}
\t\t},"""
    ),
    (
        ROOT / "store" / "ai-chat.js",
        """\t\tasync send(text) {
\t\t\tconst content = String(text || '').trim()
\t\t\tif (!content || this.sending) {
\t\t\t\treturn false
\t\t\t}
\t\t\tthis.ensureSession()
\t\t\tthis.sending = true
\t\t\tthis.messages.push({
\t\t\t\tid: ++msgId,
\t\t\t\trole: 'user',
\t\t\t\ttext: content
\t\t\t})

\t\t\t// 人工客服要展示微信原生客服按钮，本地拦截，不走网络
\t\t\tconst human = matchHumanIntent(content)
\t\t\tif (human) {
\t\t\t\tthis.messages.push({
\t\t\t\t\tid: ++msgId,
\t\t\t\t\trole: 'bot',
\t\t\t\t\t...human
\t\t\t\t})
\t\t\t\tthis.sending = false
\t\t\t\treturn true
\t\t\t}

\t\t\ttry {
\t\t\t\tconst result = await askAgent({
\t\t\t\t\tmessage: content,
\t\t\t\t\tsessionId: this.sessionId
\t\t\t\t})
\t\t\t\tthis.messages.push({
\t\t\t\t\tid: ++msgId,
\t\t\t\t\trole: 'bot',
\t\t\t\t\ttext: result.reply || DEFAULT_REPLY.text,
\t\t\t\t\tcontact: !!result.contact,
\t\t\t\t\tfeedback: !!result.feedback,
\t\t\t\t\tai: true // 展示「自动答复」标识
\t\t\t\t})
\t\t\t} catch (e) {
\t\t\t\t// 后端不可用：本地规则兜底，保证用户总能收到一句回复
\t\t\t\tconst reply = matchAutoReply(content) || {
\t\t\t\t\t...DEFAULT_REPLY
\t\t\t\t}
\t\t\t\tthis.messages.push({
\t\t\t\t\tid: ++msgId,
\t\t\t\t\trole: 'bot',
\t\t\t\t\ttext: reply.text,
\t\t\t\t\tcontact: !!reply.contact,
\t\t\t\t\tfeedback: !!reply.feedback,
\t\t\t\t\tai: true
\t\t\t\t})
\t\t\t} finally {
\t\t\t\tthis.sending = false
\t\t\t}
\t\t\treturn true
\t\t}""",
        """\t\tasync send(text) {
\t\t\tconst content = String(text || '').trim()
\t\t\tif (!content || this.sending) {
\t\t\t\treturn false
\t\t\t}
\t\t\tthis.ensureSession()
\t\t\tthis.sending = true
\t\t\tthis.messages.push({
\t\t\t\tid: 'local-' + (++msgId),
\t\t\t\trole: 'user',
\t\t\t\ttext: content
\t\t\t})

\t\t\t// 已经在人工模式：直接走人工通道发消息
\t\t\tif (this.humanMode) {
\t\t\t\treturn await this.sendHumanMessage(content)
\t\t\t}

\t\t\t// 命中「人工客服」意图：转接后端人工客服
\t\t\tconst human = matchHumanIntent(content)
\t\t\tif (human) {
\t\t\t\treturn await this.handoffToHuman(content)
\t\t\t}

\t\t\ttry {
\t\t\t\tconst result = await askAgent({
\t\t\t\t\tmessage: content,
\t\t\t\t\tsessionId: this.sessionId
\t\t\t\t})
\t\t\t\tthis.messages.push({
\t\t\t\t\tid: 'local-' + (++msgId),
\t\t\t\t\trole: 'bot',
\t\t\t\t\ttext: result.reply || DEFAULT_REPLY.text,
\t\t\t\t\tcontact: !!result.contact,
\t\t\t\t\tfeedback: !!result.feedback,
\t\t\t\t\tai: true // 展示「自动答复」标识
\t\t\t\t})
\t\t\t} catch (e) {
\t\t\t\t// 后端不可用：本地规则兜底，保证用户总能收到一句回复
\t\t\t\tconst reply = matchAutoReply(content) || {
\t\t\t\t\t...DEFAULT_REPLY
\t\t\t\t}
\t\t\t\tthis.messages.push({
\t\t\t\t\tid: 'local-' + (++msgId),
\t\t\t\t\trole: 'bot',
\t\t\t\t\ttext: reply.text,
\t\t\t\t\tcontact: !!reply.contact,
\t\t\t\t\tfeedback: !!reply.feedback,
\t\t\t\t\tai: true
\t\t\t\t})
\t\t\t} finally {
\t\t\t\tthis.sending = false
\t\t\t}
\t\t\treturn true
\t\t}"""
    ),

    # 4. pages/my/customer-service.vue
    (
        ROOT / "pages" / "my" / "customer-service.vue",
        """\t\t<!-- 顶部说明 + 人工客服入口：open-type="contact" 拉起微信原生客服会话 -->
\t\t<view class="service-bar">
\t\t\t<view class="service-info">
\t\t\t\t<text class="service-title">客服助手</text>
\t\t\t\t<text class="service-tip">固定知识库自动答复，人工客服请点右侧按钮</text>
\t\t\t</view>
\t\t\t<button class="contact-btn" open-type="contact" hover-class="none">联系人工客服</button>
\t\t</view>""",
        """\t\t<!-- 顶部说明 + 人工客服入口 -->
\t\t<view class="service-bar">
\t\t\t<view class="service-info">
\t\t\t\t<text class="service-title">{{ store.humanMode ? '人工客服' : '客服助手' }}</text>
\t\t\t\t<text class="service-tip">{{ store.humanMode ? '人工客服接待中，请直接留言' : '固定知识库自动答复，人工客服请点右侧按钮' }}</text>
\t\t\t</view>
\t\t\t<button v-if="!store.humanMode" class="contact-btn" @click="handoffToHuman" hover-class="none">联系人工客服</button>
\t\t</view>"""
    ),
    (
        ROOT / "pages" / "my" / "customer-service.vue",
        """\t\t\t\t\t\t<!-- 自动答复明确标注，避免用户误以为是真人 -->
\t\t\t\t\t\t<text v-if="msg.ai && !msg.typing" class="ai-tag">自动答复</text>
\t\t\t\t\t\t<view v-if="msg.contact || msg.feedback" class="bubble-actions">""",
        """\t\t\t\t\t\t<!-- 自动答复明确标注，避免用户误以为是真人 -->
\t\t\t\t\t\t<text v-if="msg.ai && !msg.typing" class="ai-tag">自动答复</text>
\t\t\t\t\t\t<text v-else-if="msg.human && !msg.typing" class="human-tag">人工客服</text>
\t\t\t\t\t\t<view v-if="msg.contact || msg.feedback" class="bubble-actions">"""
    ),
    (
        ROOT / "pages" / "my" / "customer-service.vue",
        """\t\t\t\t\t\t\t<button
\t\t\t\t\t\t\t\tv-if="msg.contact"
\t\t\t\t\t\t\t\tclass="bubble-btn"
\t\t\t\t\t\t\t\topen-type="contact"
\t\t\t\t\t\t\t\thover-class="none"
\t\t\t\t\t\t\t>联系人工客服</button>""",
        """\t\t\t\t\t\t\t<button
\t\t\t\t\t\t\t\tv-if="msg.contact"
\t\t\t\t\t\t\t\tclass="bubble-btn"
\t\t\t\t\t\t\t\t@click="handoffToHuman"
\t\t\t\t\t\t\t\thover-class="none"
\t\t\t\t\t\t\t>联系人工客服</button>"""
    ),
    (
        ROOT / "pages" / "my" / "customer-service.vue",
        """\t\t\tscrollToBottom() {
\t\t\t\tthis.$nextTick(() => {
\t\t\t\t\tthis.scrollTop += 10000
\t\t\t\t})
\t\t\t},
\t\t\tgoFeedback() {
\t\t\t\tuni.navigateTo({
\t\t\t\t\turl: '/pages/my/feedback'
\t\t\t\t})
\t\t\t}""",
        """\t\t\tscrollToBottom() {
\t\t\t\tthis.$nextTick(() => {
\t\t\t\t\tthis.scrollTop += 10000
\t\t\t\t})
\t\t\t},
\t\t\thandoffToHuman() {
\t\t\t\tif (this.store.sending) return
\t\t\t\tthis.store.handoffToHuman('人工客服')
\t\t\t},
\t\t\tgoFeedback() {
\t\t\t\tuni.navigateTo({
\t\t\t\t\turl: '/pages/my/feedback'
\t\t\t\t})
\t\t\t}"""
    ),
    (
        ROOT / "pages" / "my" / "customer-service.vue",
        """\t.ai-tag {
\t\tdisplay: inline-block;
\t\tmargin-top: 8rpx;
\t\tpadding: 2rpx 10rpx;
\t\tborder-radius: 6rpx;
\t\tbackground: #FFF3E8;
\t\tfont-size: 20rpx;
\t\tcolor: #B25B00;
\t}""",
        """\t.ai-tag {
\t\tdisplay: inline-block;
\t\tmargin-top: 8rpx;
\t\tpadding: 2rpx 10rpx;
\t\tborder-radius: 6rpx;
\t\tbackground: #FFF3E8;
\t\tfont-size: 20rpx;
\t\tcolor: #B25B00;
\t}

\t.human-tag {
\t\tdisplay: inline-block;
\t\tmargin-top: 8rpx;
\t\tpadding: 2rpx 10rpx;
\t\tborder-radius: 6rpx;
\t\tbackground: #E6F7FF;
\t\tfont-size: 20rpx;
\t\tcolor: #1677FF;
\t}"""
    ),

    # 5. components/floating-chat/floating-chat.vue
    (
        ROOT / "components" / "floating-chat" / "floating-chat.vue",
        """\t\t\t<view class="chat-head">
\t\t\t\t<image class="head-avatar" src="/static/zongjie.jpg" mode="aspectFill"></image>
\t\t\t\t<view class="head-info">
\t\t\t\t\t<text class="head-title">客服助手</text>
\t\t\t\t\t<text class="head-sub">常见咨询 · 固定知识库答复</text>
\t\t\t\t</view>
\t\t\t\t<view class="head-actions">
\t\t\t\t\t<text class="head-action" @click="resetChat">新对话</text>
\t\t\t\t\t<text class="head-close" @click="store.close()">×</text>
\t\t\t\t</view>
\t\t\t</view>""",
        """\t\t\t<view class="chat-head">
\t\t\t\t<image class="head-avatar" src="/static/zongjie.jpg" mode="aspectFill"></image>
\t\t\t\t<view class="head-info">
\t\t\t\t\t<text class="head-title">{{ store.humanMode ? '人工客服' : '客服助手' }}</text>
\t\t\t\t\t<text class="head-sub">{{ store.humanMode ? '人工客服接待中，请直接留言' : '常见咨询 · 固定知识库答复' }}</text>
\t\t\t\t</view>
\t\t\t\t<view class="head-actions">
\t\t\t\t\t<text class="head-action" @click="resetChat">新对话</text>
\t\t\t\t\t<text class="head-close" @click="store.close()">×</text>
\t\t\t\t</view>
\t\t\t</view>"""
    ),
    (
        ROOT / "components" / "floating-chat" / "floating-chat.vue",
        """\t\t\t\t\t\t<!-- 自动答复明确标注，避免用户误以为是真人 -->
\t\t\t\t\t\t<text v-if="msg.ai && !msg.typing" class="ai-tag">自动答复</text>
\t\t\t\t\t\t<view v-if="msg.contact || msg.feedback" class="bubble-actions">""",
        """\t\t\t\t\t\t<!-- 自动答复明确标注，避免用户误以为是真人 -->
\t\t\t\t\t\t<text v-if="msg.ai && !msg.typing" class="ai-tag">自动答复</text>
\t\t\t\t\t\t<text v-else-if="msg.human && !msg.typing" class="human-tag">人工客服</text>
\t\t\t\t\t\t<view v-if="msg.contact || msg.feedback" class="bubble-actions">"""
    ),
    (
        ROOT / "components" / "floating-chat" / "floating-chat.vue",
        """\t\t\t\t\t\t\t<button
\t\t\t\t\t\t\t\tv-if="msg.contact"
\t\t\t\t\t\t\t\tclass="bubble-btn"
\t\t\t\t\t\t\t\topen-type="contact"
\t\t\t\t\t\t\t\thover-class="none"
\t\t\t\t\t\t\t>联系人工客服</button>""",
        """\t\t\t\t\t\t\t<button
\t\t\t\t\t\t\t\tv-if="msg.contact"
\t\t\t\t\t\t\t\tclass="bubble-btn"
\t\t\t\t\t\t\t\t@click="handoffToHuman"
\t\t\t\t\t\t\t\thover-class="none"
\t\t\t\t\t\t\t>联系人工客服</button>"""
    ),
    (
        ROOT / "components" / "floating-chat" / "floating-chat.vue",
        """\t\t\tgoFeedback() {
\t\t\t\tthis.store.close()
\t\t\t\tuni.navigateTo({
\t\t\t\t\turl: '/pages/my/feedback'
\t\t\t\t})
\t\t\t}""",
        """\t\t\thandoffToHuman() {
\t\t\t\tif (this.store.sending) return
\t\t\t\tthis.store.handoffToHuman('人工客服')
\t\t\t},
\t\t\tgoFeedback() {
\t\t\t\tthis.store.close()
\t\t\t\tuni.navigateTo({
\t\t\t\t\turl: '/pages/my/feedback'
\t\t\t\t})
\t\t\t}"""
    ),
    (
        ROOT / "components" / "floating-chat" / "floating-chat.vue",
        """\t.ai-tag {
\t\tdisplay: inline-block;
\t\tmargin-top: 8rpx;
\t\tpadding: 2rpx 10rpx;
\t\tborder-radius: 6rpx;
\t\tbackground: #FFF3E8;
\t\tfont-size: 20rpx;
\t\tcolor: #B25B00;
\t}""",
        """\t.ai-tag {
\t\tdisplay: inline-block;
\t\tmargin-top: 8rpx;
\t\tpadding: 2rpx 10rpx;
\t\tborder-radius: 6rpx;
\t\tbackground: #FFF3E8;
\t\tfont-size: 20rpx;
\t\tcolor: #B25B00;
\t}

\t.human-tag {
\t\tdisplay: inline-block;
\t\tmargin-top: 8rpx;
\t\tpadding: 2rpx 10rpx;
\t\tborder-radius: 6rpx;
\t\tbackground: #E6F7FF;
\t\tfont-size: 20rpx;
\t\tcolor: #1677FF;
\t}"""
    ),

    # 6. utils/auto-reply.js：更新注释
    (
        ROOT / "utils" / "auto-reply.js",
        """* 例外：「人工客服」必须本地拦截（要拉起微信原生客服会话，不能走网络）。""",
        """* 例外：「人工客服」必须本地拦截，直接转后端人工客服会话。"""
    ),
]


def apply():
    if not ROOT.exists():
        print(f"目标目录不存在：{ROOT}")
        print("请传入小程序项目根目录，例如：")
        print("  python apply_miniapp_human_service.py D:\\study\\my-workspace\\wechat-workspace\\e-commerce-uniapp")
        sys.exit(1)

    for path, old, new in REPLACEMENTS:
        text = path.read_text(encoding='utf-8')
        if old not in text:
            print(f"[跳过] 未找到匹配片段：{path.relative_to(ROOT)}")
            continue
        text = text.replace(old, new, 1)
        path.write_text(text, encoding='utf-8')
        print(f"[已更新] {path.relative_to(ROOT)}")

    print("\n全部替换完成。请在 HBuilderX 中重新编译小程序并测试。")


if __name__ == '__main__':
    apply()
