package org.androidpn.client;

import org.jivesoftware.smack.packet.IQ;

/**
 * Created by rongwenzhao on 2018/1/12.
 * 客户端端接收到服务器推送消息后的回执消息（确认消息）。
 */

public class DeliverConfirmIQ extends IQ {

    String uuid;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public String getChildElementXML() {
        StringBuilder buf = new StringBuilder();
        buf.append("<").append("deliverconfirm").append(" xmlns=\"").append(
                "androidpn:iq:deliverconfirm").append("\">");
        if (uuid != null) {
            buf.append("<uuid>").append(uuid).append("</uuid>");
        }
        buf.append("</").append("deliverconfirm").append("> ");
        return buf.toString();
    }
}
