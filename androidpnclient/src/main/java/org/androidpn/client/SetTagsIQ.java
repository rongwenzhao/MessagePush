package org.androidpn.client;

import org.jivesoftware.smack.packet.IQ;

import java.util.List;

/**
 * Created by rongwenzhao on 2018/1/15.
 * 设置标签的IQ。用户设置该用户关注的一组标签。
 */

public class SetTagsIQ extends IQ {

    private String username;
    private List<String> tagList;

    public void setUsername(String username) {
        this.username = username;
    }

    public void setTagList(List<String> tagList) {
        this.tagList = tagList;
    }

    public List<String> getTagList() {
        return tagList;
    }

    @Override
    public String getChildElementXML() {
        StringBuilder buf = new StringBuilder();
        buf.append("<").append("settags").append(" xmlns=\"").append(
                "androidpn:iq:settags").append("\">");
        if (username != null) {
            buf.append("<username>").append(username).append("</username>");
        }
        //将list拼装成字符串，再封装到xml中。
        if (tagList != null && !tagList.isEmpty()) {
            buf.append("<tags>");
            int count = tagList.size();
            for (int i = 0; i < count; i++) {
                buf.append(tagList.get(i));
                if (i < count - 1) {
                    buf.append(",");
                }
            }
            buf.append("</tags>");
        }
        buf.append("</").append("settags").append("> ");
        return buf.toString();
    }
}
