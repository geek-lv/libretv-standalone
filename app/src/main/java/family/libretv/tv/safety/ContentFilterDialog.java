package family.libretv.tv.safety;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 内容过滤设置（手机 / 电视共用）。
 *
 * <p>与广告跳过设置保持同一套原生控件风格：整体是本地判定开关 + 儿童锁管理 + 判定记录。
 * 所有操作只改本机 SharedPreferences，不触发任何网络请求。
 */
public final class ContentFilterDialog {

    private ContentFilterDialog() {
    }

    public static void show(Activity activity) {
        show(activity, null);
    }

    /**
     * @param onChange 对话框关闭后回调，用于让调用方重新读取来源列表
     *                 （解锁/取消标记后，原本被隐藏的来源要能出现）
     */
    public static void show(final Activity activity, final Runnable onChange) {
        ContentFilterConfig.init(activity);
        AdultSourceStats.init(activity);
        final float density = activity.getResources().getDisplayMetrics().density;

        final ScrollView scroll = new ScrollView(activity);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Math.round(20 * density), Math.round(6 * density),
                Math.round(20 * density), Math.round(6 * density));
        scroll.addView(root);

        bind(activity, root, onChange);
        showContainer(activity, scroll, onChange);
    }

    /**
     * 把内容过滤控件渲染进调用方给的容器。
     *
     * <p>设置页的合并入口（内容过滤 + 广告跳过 + 片头片尾）复用这一份实现，
     * 独立对话框只在外面套一层滚动容器和标题。
     */
    public static void bind(final Activity activity, final LinearLayout root, final Runnable onChange) {
        ContentFilterConfig.init(activity);
        AdultSourceStats.init(activity);
        final float density = activity.getResources().getDisplayMetrics().density;

        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            root.removeAllViews();

            root.addView(caption(activity, density,
                    "判定强度固定为最严档，不提供调档：分类、片名、备注、简介、番号样式、直播频道"
                            + "全部参与判定，宁可错杀也不放过。全部在本机完成，不联网、不上传。"
                            + "关闭这个开关需要 PIN。"));

            final boolean on = ContentFilterConfig.enabled();
            Switch master = toggle(activity, density, on ? "NSFW 拦截：已开启" : "NSFW 拦截：已关闭", on);
            master.setOnCheckedChangeListener((view, checked) -> {
                if (checked == ContentFilterConfig.enabled()) return;
                if (checked) {
                    ContentFilterConfig.setEnabled(true);
                    render[0].run();
                    return;
                }
                // 关闭保护是高危操作：先把开关拨回去，PIN 验证通过才真正关闭
                view.setChecked(true);
                if (!ContentFilterConfig.hasPin()) {
                    confirm(activity, "需要先设置 PIN",
                            "关闭 NSFW 拦截会让过滤彻底失效，因此关闭前必须设置 PIN。",
                            () -> promptNewPin(activity, density, render));
                    return;
                }
                PinDialog.requireVerify(activity, "关闭 NSFW 拦截",
                        "输入 PIN 确认关闭。关闭后本机不再拦截任何内容，直到你重新打开。",
                        () -> {
                            ContentFilterConfig.setEnabled(false);
                            Toast.makeText(activity, "已关闭 NSFW 拦截", Toast.LENGTH_SHORT).show();
                            render[0].run();
                        });
            });
            root.addView(master);

            String lockState;
            if (ContentFilterConfig.unlocked()) {
                lockState = "已临时解锁 · 剩余约 " + ContentFilterConfig.unlockMinutesLeft() + " 分钟";
            } else if (ContentFilterConfig.hasPin()) {
                lockState = "已锁定 · 疑似成人内容不可见";
            } else {
                lockState = "未设置 PIN · 疑似成人内容默认隐藏";
            }
            TextView status = new TextView(activity);
            status.setText("已标记 " + ContentFilterConfig.adultCount() + " 个来源 · " + lockState);
            status.setTextSize(14);
            status.setTextColor(ContentFilterConfig.unlocked()
                    ? Color.parseColor("#ffd15c") : Color.parseColor("#7fd1a8"));
            status.setPadding(0, Math.round(12 * density), 0, Math.round(6 * density));
            root.addView(status);

            root.addView(action(activity, density,
                    ContentFilterConfig.hasPin() ? "修改 PIN" : "设置 PIN",
                    ContentFilterConfig.hasPin() ? "当前已启用儿童锁" : "4–6 位数字，忘记后只能清除重设",
                    () -> promptNewPin(activity, density, render)));
            if (ContentFilterConfig.hasPin()) {
                root.addView(action(activity, density, "清除 PIN", "清除后需要重新设置才能解锁",
                        () -> confirm(activity, "清除 PIN？", "清除后疑似成人内容仍默认隐藏，但你将无法临时解锁。",
                                () -> {
                                    ContentFilterConfig.clearPin();
                                    Toast.makeText(activity, "已清除 PIN", Toast.LENGTH_SHORT).show();
                                    render[0].run();
                                })));
            }

            if (ContentFilterConfig.unlocked()) {
                root.addView(action(activity, density, "立即重新锁定", "结束本次临时解锁",
                        () -> {
                            ContentFilterConfig.lock();
                            Toast.makeText(activity, "已锁定", Toast.LENGTH_SHORT).show();
                            render[0].run();
                        }));
            } else {
                root.addView(action(activity, density, "临时解锁 " + (ContentFilterConfig.UNLOCK_MILLIS / 60000) + " 分钟",
                        ContentFilterConfig.hasPin() ? "输入 PIN 后可见被过滤的来源" : "需要先设置 PIN",
                        () -> PinDialog.requireUnlock(activity, () -> {
                            render[0].run();
                            final Runnable refresh = onChange;
                            if (refresh != null) refresh.run();
                        })));
            }

            root.addView(action(activity, density,
                    "追加关键词（" + ContentFilterConfig.extraKeywordCount() + " 个）",
                    "在默认规则之外额外屏蔽的词，逗号或换行分隔",
                    () -> promptKeywords(activity, density, render)));

            root.addView(action(activity, density,
                    "已标记来源（" + ContentFilterConfig.adultCount() + "）",
                    "查看判定依据，误判可逐个取消",
                    () -> showMarks(activity, density, render)));

            root.addView(action(activity, density, "清空判定记录", "保留来源，只清掉成人标记与累计统计",
                    () -> confirm(activity, "清空判定记录？", "标记与统计都会清空，正常搜索后会重新判定。",
                            () -> {
                                ContentFilterConfig.clearMarks();
                                AdultSourceStats.clearAll();
                                Toast.makeText(activity, "已清空", Toast.LENGTH_SHORT).show();
                                render[0].run();
                            })));

            root.addView(caption(activity, density,
                    "判定只看本机已拿到的分类、片名与简介，不会访问额外接口。"
                            + "「伦理」「写真」「三级」这类边缘分类同样会被拦，"
                            + "带「极品」「美女」「探花」等低俗词的标题也会被拦。"));
        };

        render[0].run();
    }

    private static void showContainer(final Activity activity, android.view.View content, final Runnable onChange) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("内容过滤")
                .setView(content)
                .setPositiveButton("关闭", null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            if (onChange != null) onChange.run();
        });
        dialog.show();
    }

    // ------------------------------------------------------------------ 子对话框

    private static void promptNewPin(Activity activity, float density, Runnable[] render) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(22 * density), Math.round(8 * density), Math.round(22 * density), 0);

        TextView caption = new TextView(activity);
        caption.setText("忘记 PIN 只能清除后重设；临时解锁仅对本机本次运行有效。");
        caption.setTextSize(13);
        caption.setTextColor(0xFF8899AA);
        caption.setPadding(0, 0, 0, Math.round(8 * density));
        box.addView(caption);

        EditText first = pinInput(activity, "输入 4–6 位数字");
        EditText second = pinInput(activity, "再次输入确认");
        box.addView(first);
        box.addView(second);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("设置 PIN")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String one = first.getText().toString().trim();
                    String two = second.getText().toString().trim();
                    if (!one.matches("\\d{4,6}")) {
                        Toast.makeText(activity, "PIN 需为 4–6 位数字", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!one.equals(two)) {
                        second.setText("");
                        Toast.makeText(activity, "两次输入不一致", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ContentFilterConfig.setPin(one);
                    dialog.dismiss();
                    Toast.makeText(activity, "PIN 已保存，当前为锁定状态", Toast.LENGTH_SHORT).show();
                    if (render != null) render[0].run();
                }));
        dialog.show();
    }

    private static void promptKeywords(Activity activity, float density, Runnable[] render) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(22 * density), Math.round(8 * density), Math.round(22 * density), 0);

        TextView caption = new TextView(activity);
        caption.setText("每行一个，或用中英文逗号分隔。命中片名、类型、备注、地区与简介。");
        caption.setTextSize(13);
        caption.setTextColor(0xFF8899AA);
        caption.setPadding(0, 0, 0, Math.round(8 * density));
        box.addView(caption);

        EditText input = new EditText(activity);
        input.setText(ContentFilterConfig.extraKeywordsRaw());
        input.setHint("例如：某厂牌名,某个词");
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setSelection(input.getText().length());
        box.addView(input);

        new AlertDialog.Builder(activity)
                .setTitle("追加屏蔽关键词")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    ContentFilterConfig.setExtraKeywordsRaw(input.getText().toString());
                    if (render != null) render[0].run();
                })
                .show();
    }

    private static void showMarks(Activity activity, float density, Runnable[] render) {
        Set<String> keys = ContentFilterConfig.adultKeys();
        if (keys.isEmpty()) {
            Toast.makeText(activity, "还没有被标记的来源", Toast.LENGTH_SHORT).show();
            return;
        }
        final List<String> keyList = new ArrayList<>(keys);
        final List<String> labels = new ArrayList<>();
        for (String key : keyList) {
            String name = ContentFilterConfig.markName(key);
            if (name.isEmpty()) name = key;
            String why = ContentFilterConfig.markReason(key);
            labels.add(why.isEmpty() ? name : name + "\n" + why);
        }

        new AlertDialog.Builder(activity)
                .setTitle("已标记来源")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    String key = keyList.get(which);
                    String name = ContentFilterConfig.markName(key);
                    confirm(activity, "取消标记？",
                            "取消后该来源会重新出现在来源列表，并重新参与搜索；后续也不会再自动标记它。",
                            () -> {
                                ContentFilterConfig.dismiss(key, name);
                                AdultSourceStats.reset(key);
                                Toast.makeText(activity, "已取消标记", Toast.LENGTH_SHORT).show();
                                if (render != null) render[0].run();
                            });
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    // ------------------------------------------------------------------ 小组件

    private static TextView caption(Activity activity, float density, String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(0xFF8899AA);
        view.setPadding(0, Math.round(6 * density), 0, Math.round(10 * density));
        return view;
    }

    private static Switch toggle(Activity activity, float density, String text, boolean checked) {
        Switch view = new Switch(activity);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(Color.WHITE);
        view.setChecked(checked);
        view.setPadding(Math.round(4 * density), Math.round(10 * density),
                Math.round(4 * density), Math.round(10 * density));
        return view;
    }

    private static Button action(Activity activity, float density, String title, String subtitle, Runnable action) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setText(subtitle.isEmpty() ? title : title + "\n" + subtitle);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(Math.round(14 * density), Math.round(8 * density),
                Math.round(14 * density), Math.round(8 * density));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Math.round(4 * density);
        button.setLayoutParams(params);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private static EditText pinInput(Activity activity, String hint) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setSingleLine(true);
        input.setTextSize(18);
        return input;
    }

    private static void confirm(Activity activity, String title, String message, Runnable onConfirm) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> onConfirm.run())
                .show();
    }

    /** 供设置页展示的状态副标题 */
    public static String summary(Activity activity) {
        ContentFilterConfig.init(activity);
        if (!ContentFilterConfig.enabled()) return "已关闭 · 无保护";
        StringBuilder builder = new StringBuilder("最严档");
        builder.append(" · ").append(ContentFilterConfig.unlocked() ? "已临时解锁" : "已锁定");
        if (!ContentFilterConfig.hasPin() && !ContentFilterConfig.unlocked()) builder.append("（未设 PIN）");
        builder.append(" · 已标记 ").append(ContentFilterConfig.adultCount()).append(" 个来源");
        if (ContentFilterConfig.extraKeywordCount() > 0)
            builder.append(" · 自定词 ").append(ContentFilterConfig.extraKeywordCount());
        return builder.toString();
    }

    /** 供来源列表展示：解锁后给成人源加标识 */
    public static String sourceLabel(JSONObject source) {
        if (source == null || !source.optBoolean("adult", false)) return "";
        return " · 18+";
    }
}
