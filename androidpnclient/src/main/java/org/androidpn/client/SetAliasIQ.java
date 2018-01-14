package org.androidpn.client;

import org.jivesoftware.smack.packet.IQ;

/**
 * Created by rongwenzhao on 2018/1/14.
 * 设置别名的IQ
 */

public class SetAliasIQ extends IQ {

    String username;
    String alias;

    public void setUsername(String username) {
        this.username = username;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    @Override
    public String getChildElementXML() {
        StringBuilder buf = new StringBuilder();
        buf.append("<").append("setalias").append(" xmlns=\"").append(
                "androidpn:iq:setalias").append("\">");
        if (username != null) {
            buf.append("<username>").append(username).append("</username>");
        }
        if (alias != null) {
            buf.append("<alias>").append(alias).append("</alias>");
        }
        buf.append("</").append("setalias").append("> ");
        return buf.toString();
    }
}
