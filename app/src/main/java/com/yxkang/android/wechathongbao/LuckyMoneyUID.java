package com.yxkang.android.wechathongbao;

import android.text.TextUtils;

/**
 * Created by fine on 2017/1/21.
 * <br>
 * 红包的唯一标识，避免重复读取红包
 * <p>
 * <strong>注意：</strong>其实该类的设计并不能唯一表示一个红包，目前还无法做到真正的唯一
 * </p>
 */

class LuckyMoneyUID {

    private String sender;
    private String lastSender;
    private String contentDesc;
    private String lastContentDesc;
    private String timeOrName;
    private String lastTimeOrName;

    void setSender(String sender) {
        this.lastSender = this.sender;
        this.sender = sender;
    }

    boolean isSenderChanged() {
        if (!TextUtils.isEmpty(lastSender)) {
            return !lastSender.equals(sender);
        } else {
            return !TextUtils.isEmpty(sender);
        }
    }

    void setContentDesc(String contentDesc) {
        this.lastContentDesc = this.contentDesc;
        this.contentDesc = contentDesc;
    }

    boolean isContentDescChanged() {
        if (!TextUtils.isEmpty(lastContentDesc)) {
            return !lastContentDesc.equals(contentDesc);
        } else {
            return !TextUtils.isEmpty(contentDesc);
        }
    }

    void setTimeOrName(String timeOrName) {
        this.lastTimeOrName = this.timeOrName;
        this.timeOrName = timeOrName;
    }

    void setTimeOrNameNone() {
        timeOrName = "";
        lastTimeOrName = "";
    }

    boolean isTimeOrNameChanged() {
        if (!TextUtils.isEmpty(lastTimeOrName)) {
            return !lastTimeOrName.equals(timeOrName);
        } else {
            return !TextUtils.isEmpty(timeOrName);
        }
    }

    void clear() {
        sender = "";
        lastSender = "";
        contentDesc = "";
        lastContentDesc = "";
        timeOrName = "";
        lastTimeOrName = "";
    }

    @Override
    public String toString() {
        return "LuckyMoneyUID{" +
                "sender='" + sender + '\'' +
                ", lastSender='" + lastSender + '\'' +
                ", contentDesc='" + contentDesc + '\'' +
                ", lastContentDesc='" + lastContentDesc + '\'' +
                ", timeOrName='" + timeOrName + '\'' +
                ", lastTimeOrName='" + lastTimeOrName + '\'' +
                '}';
    }
}
