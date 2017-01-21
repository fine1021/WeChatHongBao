package com.yxkang.android.wechathongbao;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.PendingIntent;
import android.graphics.Rect;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.os.BuildCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class LuckyMoneyService extends AccessibilityService {

    private static final String TAG = "LuckyMoneyService";

    private static final String NOTIFICATION_KEYWORD = "[微信红包]";
    private static final String LAUNCHER_UI = "com.tencent.mm.ui.LauncherUI";
    private static final String RECEIVE_UI = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI";
    private static final String DETAIL_UI = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI";

    private static final String[] HONGBAO_KEYWORD = new String[]{"领取红包", "查看红包"};
    private static final String[] OVER_KEYWORD = new String[]{"手慢了", "已超过24小时"};

    private boolean luckyMoneyAutoOpened;
    private boolean isAtLeastN;
    private LuckyMoneyUID luckyMoneyUID = new LuckyMoneyUID();
    private String currentActivityName = LAUNCHER_UI;
    private boolean traverseMutex = false;

    @Override
    public void onCreate() {
        super.onCreate();
        isAtLeastN = BuildCompat.isAtLeastN();
        Log.i(TAG, "onCreate: initialize ok");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                handleNotificationChanged(event);
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                handleWindowStateChanged(event);
                break;
            case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
                handleWindowContentChanged();
                break;
        }
    }

    private void handleNotificationChanged(AccessibilityEvent event) {
        List<CharSequence> charSequences = event.getText();
        if (charSequences != null && !charSequences.isEmpty()) {
            for (CharSequence charSequence : charSequences) {
                String text = charSequence.toString();
                if (text.contains(NOTIFICATION_KEYWORD)) {
                    Parcelable parcelable = event.getParcelableData();
                    if (parcelable != null && parcelable instanceof Notification) {
                        PendingIntent pendingIntent = ((Notification) parcelable).contentIntent;
                        try {
                            luckyMoneyUID.clear();
                            pendingIntent.send();
                            Log.i(TAG, "openNotification: send ok");
                        } catch (PendingIntent.CanceledException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private void handleWindowStateChanged(AccessibilityEvent event) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {         // 如果根节点为空，就不用做任何事情了
            Log.w(TAG, "handleWindowStateChanged: rootNode == null");
            return;
        }
        String className = event.getClassName().toString();
        currentActivityName = className;
        Log.i(TAG, "handleWindowStateChanged: currentActivityName = " + currentActivityName);
        if (LAUNCHER_UI.equals(className)) {
            if (traverseMutex) {
                return;
            }
            traverseMutex = true;
            AccessibilityNodeInfo hongbaoNode = findHongbaoNode(rootNode);
            if (hongbaoNode != null) {
                AccessibilityNodeInfo parent = findHongbaoParentNode(hongbaoNode);
                if (parent != null) {
                    if (getLuckyMoneyUID(parent)) {
                        Log.i(TAG, "handleWindowStateChanged: parent performClick");
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        setLuckyMoneyAutoOpened(true);
                    } else {
                        Log.v(TAG, "handleWindowStateChanged: assume hongbao has been opened");
                    }
                    parent.recycle();
                }
                hongbaoNode.recycle();
            }
            traverseMutex = false;
        } else if (RECEIVE_UI.equals(className)) {
            if (!isAtLeastN) {
                AccessibilityNodeInfo button = findOpenButton(rootNode);
                if (button != null) {
                    Log.i(TAG, "handleWindowStateChanged: button performClick");
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                } else {
                    if (luckyMoneyAutoOpened) {   // 如果是自动打开的情况就自动关闭，否则就不用关闭了
                        if (hasOneOfOverNodes(rootNode)) {
                            Log.i(TAG, "handleWindowStateChanged: LuckyMoneyReceiveUI performGlobalActionBack");
                            performGlobalAction(GLOBAL_ACTION_BACK);
                            setLuckyMoneyAutoOpened(false);
                        } else {
                            Log.w(TAG, "handleWindowStateChanged: can't close LuckyMoneyReceiveUI automatically");
                        }
                    }
                }
            } else {
                Log.w(TAG, "handleWindowStateChanged: not support Android N+ open");
            }
        } else if (DETAIL_UI.equals(className)) {
            if (luckyMoneyAutoOpened) {         // 如果是自动打开的情况就自动关闭，否则就不用关闭了
                Log.i(TAG, "handleWindowStateChanged: LuckyMoneyDetailUI performGlobalActionBack");
                performGlobalAction(GLOBAL_ACTION_BACK);
                setLuckyMoneyAutoOpened(false);
            }
        }
    }

    private void handleWindowContentChanged() {
        if (LAUNCHER_UI.equals(currentActivityName)) {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {         // 如果根节点为空，就不用做任何事情了
                Log.w(TAG, "handleWindowContentChanged: rootNode == null");
                return;
            }
            if (traverseMutex) {
                return;
            }
            traverseMutex = true;
            AccessibilityNodeInfo hongbaoNode = findHongbaoNode(rootNode);
            if (hongbaoNode != null) {
                AccessibilityNodeInfo parent = findHongbaoParentNode(hongbaoNode);
                if (parent != null) {
                    if (getLuckyMoneyUID(parent)) {
                        Log.i(TAG, "handleWindowContentChanged: parent performClick");
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        setLuckyMoneyAutoOpened(true);
                    } else {
                        Log.v(TAG, "handleWindowContentChanged: assume hongbao has been opened");
                    }
                    parent.recycle();
                }
                hongbaoNode.recycle();
            }
            traverseMutex = false;
        }
    }

    private boolean hasOneOfOverNodes(@NonNull AccessibilityNodeInfo node) {
        List<AccessibilityNodeInfo> nodes;
        for (String text : OVER_KEYWORD) {
            nodes = node.findAccessibilityNodeInfosByText(text);
            if (nodes != null && !nodes.isEmpty()) {
                final int size = nodes.size();
                for (int i = 0; i < size; i++) {
                    nodes.get(i).recycle();      // 回收不需要使用的节点
                }
                return true;
            }
        }
        return false;
    }

    private boolean getLuckyMoneyUID(@NonNull AccessibilityNodeInfo node) {
        try {
            AccessibilityNodeInfo parentNode = node.getParent();
            Log.i(TAG, "getLuckyMoneyUID: start");
            traverseNode(parentNode);
            boolean hasPickedTextView = false;
            boolean hasPickedImageView = false;
            int childCount = parentNode.getChildCount();
            Log.d(TAG, "getLuckyMoneyUID: childCount = " + childCount);
            /*
             * 当点对点发红包或者自己在群聊里发红包时，会有2个child，一个ImageView(头像布局)和一个LinearLayout(红包布局)
             * 当群聊发红包时，会有3个或者4个child，一个ImageView(头像布局)、一个LinearLayout(红包布局)、一个TextView(昵称)、一个TextView(时间)
             * 当群聊里发红包有3个child时，也有可能时自己发红包时突然多出一个TextView(时间)
             */
            if (childCount == 2) {
                luckyMoneyUID.setTimeOrNameNone();
            }
            for (int i = 0; i < childCount; i++) {
                CharSequence className = parentNode.getChild(i).getClassName();
                if ("android.widget.TextView".equals(className)) {
                    CharSequence timeOrName = parentNode.getChild(i).getText();
                    if (!TextUtils.isEmpty(timeOrName) && !hasPickedTextView) {
                        luckyMoneyUID.setTimeOrName(timeOrName.toString());
                        hasPickedTextView = true;
                    }
                }
                if ("android.widget.ImageView".equals(className)) {
                    CharSequence sender = parentNode.getChild(i).getContentDescription();
                    if (!TextUtils.isEmpty(sender) && !hasPickedImageView) {
                        // 用头像的内容描述作为发送者，一般的格式为“人名+头像”
                        // 为了避免额外的开销，此处就不再做修改了
                        luckyMoneyUID.setSender(sender.toString());
                        hasPickedImageView = true;
                    }
                }
            }
            return luckyMoneyUID.isSenderChanged() || luckyMoneyUID.isContentDescChanged() ||
                    luckyMoneyUID.isTimeOrNameChanged();
        } catch (Exception e) {
            return false;
        } finally {
            Log.d(TAG, "getLuckyMoneyUID: " + luckyMoneyUID.toString());
        }
    }

    private AccessibilityNodeInfo findHongbaoParentNode(@NonNull AccessibilityNodeInfo node) {
        try {
            Log.i(TAG, "findHongbaoParentNode: start");
            traverseNode(node);
            AccessibilityNodeInfo parentNode = node.getParent();
            if (parentNode == null) {
                return null;
            }
            CharSequence className = parentNode.getClassName();
            if (!"android.widget.LinearLayout".equals(className)) {
                return null;        // 红包的一级父布局为线性布局
            }

            /*
             * 此处应该有3个child，第一个是红包描述，第二个是“领取红包”或者“查看红包”，第三个为“微信红包”
             */
            String contentDesc = parentNode.getChild(0).getText().toString();
            luckyMoneyUID.setContentDesc(contentDesc);

            /*
             * 如果是普通的文本消息，是找不到一个可点击的父节点的
             */
            while (parentNode != null) {
                traverseNode(parentNode);
                if (parentNode.isClickable()) {
                    break;
                }
                parentNode = parentNode.getParent();
            }
            return parentNode == null ? null : parentNode;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    private void traverseNode(AccessibilityNodeInfo node) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        int childCount = node.getChildCount();
        CharSequence className = node.getClassName();
        Log.i(TAG, "traversalNode: className = " + className);
        Log.d(TAG, "traversalNode: childCount = " + childCount);
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
            if (childNode == null) {
                continue;
            }
            CharSequence childClassName = childNode.getClassName();
            Log.v(TAG, "traversalNode: child className = " + childClassName);
            if ("android.widget.TextView".equals(childClassName)) {
                Log.d(TAG, "traversalNode: child text = " + childNode.getText());
            }
            Log.d(TAG, "traversalNode: child contentDescription = " + childNode.getContentDescription());
        }
    }

    @Nullable
    private AccessibilityNodeInfo findHongbaoNode(@NonNull AccessibilityNodeInfo node) {
        AccessibilityNodeInfo hongbaoNode;
        for (String text : HONGBAO_KEYWORD) {
            hongbaoNode = findLatestHongbaoNode(node, text);
            if (hongbaoNode != null) {
                return hongbaoNode;
            }
        }
        return null;
    }

    @Nullable
    private AccessibilityNodeInfo findLatestHongbaoNode(@NonNull AccessibilityNodeInfo node, String text) {
        List<AccessibilityNodeInfo> list = node.findAccessibilityNodeInfosByText(text);
        if (list != null && !list.isEmpty()) {
            final int size = list.size();
            if (size == 1) {
                return checkHongbaoNode(list.get(0));
            } else if (size > 1) {
                for (int i = 0; i < size - 1; i++) {
                    list.get(i).recycle();      // 回收不需要使用的节点
                }
                return checkHongbaoNode(list.get(size - 1));
            }
        }
        return null;
    }

    private AccessibilityNodeInfo checkHongbaoNode(@Nullable AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (bounds.bottom > 0) {    // 只处理处在当前屏幕之内的红包
            return node;
        } else {
            node.recycle();
        }
        return null;
    }

    @Nullable
    private AccessibilityNodeInfo findOpenButton(@Nullable AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }
        if (node.getChildCount() == 0) {
            if ("android.widget.Button".equals(node.getClassName())) {
                return node;
            } else {
                return null;
            }
        }
        AccessibilityNodeInfo button;
        for (int i = 0; i < node.getChildCount(); i++) {
            button = findOpenButton(node.getChild(i));
            if (button != null) {
                return button;
            }
        }
        return null;
    }

    public void setLuckyMoneyAutoOpened(boolean luckyMoneyAutoOpened) {
        if (isAtLeastN) {       // Android 7.0及以上暂时不支持
            return;
        }
        this.luckyMoneyAutoOpened = luckyMoneyAutoOpened;
    }

    @Override
    public void onInterrupt() {

    }
}
