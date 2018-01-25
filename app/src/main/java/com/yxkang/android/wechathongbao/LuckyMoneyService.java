package com.yxkang.android.wechathongbao;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class LuckyMoneyService extends AccessibilityService {

    private static final String TAG = "LuckyMoneyService";

    private static final String NOTIFICATION_KEYWORD = "[微信红包]";
    /**
     * 聊天界面
     */
    private static final String LAUNCHER_UI = "com.tencent.mm.ui.LauncherUI";
    /**
     * 点击红包弹出的界面
     */
    private static final String RECEIVE_UI = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI";
    /**
     * 红包详情界面
     */
    private static final String DETAIL_UI = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI";

    /**
     * 查看红包：自己发出去的红包，群聊可以自己领取，点对点自己无法领取
     * 领取红包：别人发的红包，群聊或者点对点的方式，自己都可以领取
     */
    private static final String[] HONGBAO_KEYWORD = new String[]{"领取红包", "查看红包"};
    private static final String[] HONGBAO_KEYWORD2 = new String[]{"领取红包"};
    private static final String[] OVER_KEYWORD = new String[]{"手慢了", "已超过24小时"};

    /**
     * {@link SharedPreferences}的key值，检查自己发出去的红包
     */
    static final String PREF_CHECK_SELF = "pref_check_self";

    private boolean luckyMoneyAutoOpened;
    private String currentActivityName = LAUNCHER_UI;
    private boolean traverseMutex = false;
    private SharedPreferences mPreferences;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate: Ok");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "onServiceConnected: Ok");
        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
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
            AccessibilityNodeInfo hongbaoNode = findKeywordNode(rootNode);
            if (hongbaoNode != null) {
                AccessibilityNodeInfo parent = findClickableParentNode(hongbaoNode);
                if (parent != null) {
                    // TODO: 2018/1/25 点对点聊天（跟某个好友聊天）的时候，抢自己发的红包会陷入无限循环中
                    Log.i(TAG, "handleWindowStateChanged: parent performClick");
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    setLuckyMoneyAutoOpened(true);
                    parent.recycle();
                }
                hongbaoNode.recycle();
            }
            traverseMutex = false;
        } else if (RECEIVE_UI.equals(className)) {
            AccessibilityNodeInfo button = findOpenButton(rootNode);
            if (button != null) {
                Log.i(TAG, "handleWindowStateChanged: button performClick");
                button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            } else {
                // 未找到开红包的按钮，可能是红包已经抢完了，也有可能是红包过期了
                if (luckyMoneyAutoOpened) {   // 如果是自动打开的情况就自动关闭，否则就不用关闭了
                    // 如果找到了一个带有“手慢了”、“已超过24小时”的节点，说明红包已经没了
                    if (hasOneOfOverNodes(rootNode)) {
                        Log.i(TAG, "handleWindowStateChanged: LuckyMoneyReceiveUI performGlobalActionBack");
                        performGlobalAction(GLOBAL_ACTION_BACK);
                        setLuckyMoneyAutoOpened(false);
                    }
                }
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
            AccessibilityNodeInfo hongbaoNode = findKeywordNode(rootNode);
            if (hongbaoNode != null) {
                AccessibilityNodeInfo parent = findClickableParentNode(hongbaoNode);
                if (parent != null) {
                    // TODO: 2018/1/25 点对点聊天（跟某个好友聊天）的时候，抢自己发的红包会陷入无限循环中
                    Log.i(TAG, "handleWindowContentChanged: parent performClick");
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    setLuckyMoneyAutoOpened(true);
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

    /**
     * 微信6.6.1发红包布局：
     * <pre class="prettyprint">
     *   RelativeLayout  ---1，顶层的布局
     *     TextView ---2，时间（可能没有）
     *     LinearLayout  ---3，红包和微信用户头像布局
     *       LinearLayout  ---4，红包布局（可点击，点击此布局打开红包）
     *         LinearLayout  ---5，红包的上半部分布局（包括红包图标，红包的描述，红包的状态）
     *           ImageView ---6，红包图标
     *           RelativeLayout ---7，红包的上半部分子布局的嵌套布局
     *             LinearLayout  ---8，红包的上半部分子布局（包括红包的描述，红包的状态）
     *               TextView  ---9，红包的描述
     *               TextView  ---10，红包的状态，例如“红包已领取”，“红包已被领完”，“领取红包”，“查看红包”（自己发红包的时候显示查看红包）
     *         RelativeLayout  ---11，红包的下半部分布局（只有一个“微信红包”字样的说明）
     *           TextView  ---12，“微信红包”字样的说明
     *       RelativeLayout  ---13，微信用户头像布局
     *         ImageView  ---14，微信用户头像布局的嵌套布局
     *         View  ---15，占空间的布局
     *         ImageView  ---16，真实的微信用户头像布局（content-desc为昵称+头像）
     *   RelativeLayout
     * </pre>
     * 微信6.6.1收到红包布局：
     * <pre class="prettyprint">
     *   RelativeLayout  ---1，顶层的布局
     *     TextView ---2，时间（可能没有）
     *     LinearLayout  ---3，红包和微信用户头像布局
     *       RelativeLayout  ---4，微信用户头像布局
     *         ImageView  ---5，微信用户头像布局的嵌套布局
     *         View  ---6，占空间的布局
     *         ImageView  ---7，真实的微信用户头像布局（content-desc为昵称+头像）
     *       LinearLayout  ---8，红包布局的外嵌套布局（比发红包多的一层布局）
     *         LinearLayout  ---9，红包布局（可点击，点击此布局打开红包）
     *           LinearLayout  ---10，红包的上半部分布局（包括红包图标，红包的描述，红包的状态）
     *             ImageView ---11，红包图标
     *             RelativeLayout ---12，红包的上半部分子布局的嵌套布局
     *               LinearLayout  ---13，红包的上半部分子布局（包括红包的描述，红包的状态）
     *                 TextView  ---14，红包的描述
     *                 TextView  ---15，红包的状态，例如“红包已领取”，“红包已被领完”，“领取红包”，“查看红包”（自己发红包的时候显示查看红包）
     *           RelativeLayout  ---16，红包的下半部分布局（只有一个“微信红包”字样的说明）
     *             TextView  ---17，“微信红包”字样的说明
     *   RelativeLayout
     * </pre>
     * <p>此函数的功能即找到发红包布局中的4布局或者收红包中的9布局</p>
     */
    private AccessibilityNodeInfo findClickableParentNode(@NonNull AccessibilityNodeInfo node) {
        try {
            Log.i(TAG, "findClickableParentNode: enter");
            traverseNode(node);
            AccessibilityNodeInfo parentNode = node.getParent();
            if (parentNode == null) {
                return null;
            }
            CharSequence className = parentNode.getClassName();
            if (!"android.widget.LinearLayout".equals(className)) {
                return null;        // 上级父布局为线性布局
            }

            // 如果是普通的文本消息，是找不到一个可点击的父节点的
            while (parentNode != null) {
                traverseNode(parentNode);
                if (parentNode.isClickable()) {
                    break;
                }
                parentNode = parentNode.getParent();
            }
            return parentNode;

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
        Log.i(TAG, "traversalNode: childCount = " + childCount);
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo childNode = node.getChild(i);
            if (childNode == null) {
                continue;
            }
            CharSequence childClassName = childNode.getClassName();
            Log.i(TAG, "traversalNode: child " + i + " className = " + childClassName);
            if ("android.widget.TextView".equals(childClassName)) {
                Log.w(TAG, "traversalNode: child " + i + " text = " + childNode.getText());
            }
            Log.i(TAG, "traversalNode: child " + i + " contentDescription = " + childNode.getContentDescription());
        }
    }

    /**
     * 微信6.6.1版本针对红包当前的状态新增了一个TextView来标识，红包的状态都会通过该TextView的text来标识
     */
    @Nullable
    private AccessibilityNodeInfo findKeywordNode(@NonNull AccessibilityNodeInfo node) {
        AccessibilityNodeInfo hongbaoNode;
        String[] keywords = mPreferences.getBoolean(PREF_CHECK_SELF, false)
                ? HONGBAO_KEYWORD : HONGBAO_KEYWORD2;
        for (String text : keywords) {
            hongbaoNode = findLatestKeywordNode(node, text);
            if (hongbaoNode != null) {
                return hongbaoNode;
            }
        }
        return null;
    }


    @Nullable
    private AccessibilityNodeInfo findLatestKeywordNode(@NonNull AccessibilityNodeInfo node, String text) {
        List<AccessibilityNodeInfo> list = node.findAccessibilityNodeInfosByText(text);
        if (list != null && !list.isEmpty()) {
            final int size = list.size();
            if (size == 1) {
                return checkKeywordNode(list.get(0));
            } else if (size > 1) {
                for (int i = 0; i < size - 1; i++) {
                    list.get(i).recycle();      // 回收不需要使用的节点
                }
                return checkKeywordNode(list.get(size - 1));
            }
        }
        return null;
    }

    private AccessibilityNodeInfo checkKeywordNode(@Nullable AccessibilityNodeInfo node) {
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

    private void setLuckyMoneyAutoOpened(boolean luckyMoneyAutoOpened) {
        this.luckyMoneyAutoOpened = luckyMoneyAutoOpened;
    }

    @Override
    public void onInterrupt() {

    }
}
