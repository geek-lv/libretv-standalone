package family.libretv.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import family.libretv.tv.adskip.AdSkipSettingsDialog;
import family.libretv.tv.safety.ContentFilterDialog;

/**
 * 设置里的合并入口：内容过滤 / 广告跳过 / 片头片尾。
 *
 * <p>三块原来各占一个入口，功能上互不依赖，合到一页只是把它们放在同一个滚动页里，
 * 每块仍保留自己的开关与存储（{@code content_filter} / {@code adskip} / {@code segment-skip}）。
 * 这一页刻意不设「一键全关」：关闭内容过滤必须先过 PIN，广告跳过与片头片尾没有这层限制。
 *
 * <p>内容过滤与广告跳过的控件分别来自 {@link ContentFilterDialog#bind} 与
 * {@link AdSkipSettingsDialog#bind}，与独立对话框共用同一份实现，避免两套界面走偏。
 */
public final class GuardSettingsDialog {

    private GuardSettingsDialog() {
    }

    /**
     * @param onChange 关闭后回调，用于让调用方重新读取来源列表
     *                 （解锁或取消成人标记后，原本被隐藏的来源要能出现）
     */
    public static void show(final Activity activity, final Runnable onChange) {
        final ScrollView scroll = new ScrollView(activity);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 20), dp(activity, 4), dp(activity, 20), dp(activity, 12));
        scroll.addView(root);

        root.addView(caption(activity,
                "内容过滤决定「能看到什么」，广告跳过与片头片尾只影响播放过程。三块各自独立开关，"
                        + "全部判定只在本机完成，不联网、不上传。"));

        LinearLayout filterBody = section(activity, root, "内容过滤 · NSFW 拦截",
                "来源标记 + 儿童锁 · 关闭需要 PIN");
        ContentFilterDialog.bind(activity, filterBody, onChange);

        divider(activity, root);

        LinearLayout adBody = section(activity, root, "广告跳过",
                "口播广告烧录在音轨里，本地离线识别，不上传音频");
        // 传非空回调，bind() 才会在页内给出保存按钮
        AdSkipSettingsDialog.bind(activity, adBody, () -> { });

        divider(activity, root);

        LinearLayout segmentBody = section(activity, root, "片头片尾",
                "播放时在控制栏标记 · 同一剧名全集、换源沿用");
        renderSegment(activity, segmentBody);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("内容与播放保护")
                .setView(scroll)
                .setPositiveButton("关闭", null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            if (onChange != null) onChange.run();
        });
        dialog.show();
    }

    /** 设置页那一行右侧的摘要，随状态即时变化 */
    public static String summary(Activity activity) {
        // 手机端进设置页时还没初始化过广告配置，不 init 会一直显示"未配置"
        family.libretv.tv.adskip.AdSkipConfig.init(activity);
        String filter = family.libretv.tv.safety.ContentFilterDialog.summary(activity);
        int keywords = family.libretv.tv.adskip.AdSkipConfig.getKeywordCount();
        int seconds = family.libretv.tv.adskip.AdSkipConfig.getSkipSeconds();
        String ad = seconds == 0 || keywords == 0 ? "广告跳过未配置" : "广告跳过 " + keywords + " 词 · " + seconds + "s";
        int titles = SegmentSkipStore.titleCount(activity);
        String segment = titles == 0 ? "片头片尾未设置" : "片头片尾 " + titles + " 个剧名";
        return filter + " · " + ad + " · " + segment;
    }

    private static void renderSegment(final Activity activity, final LinearLayout body) {
        body.removeAllViews();
        int count = SegmentSkipStore.titleCount(activity);
        body.addView(caption(activity, count == 0
                ? "还没有保存过片头片尾。播放时点「设置片头」「设置片尾」，之后同一剧名自动跳过。"
                : "已记忆 " + count + " 个剧名的片头片尾，同一剧名的全集与换源都沿用。"));
        Button clear = new Button(activity);
        clear.setText("清除全部片头片尾记忆");
        clear.setAllCaps(false);
        clear.setTextSize(14);
        clear.setTextColor(0xFFFF8FA8);
        clear.setMinWidth(0);
        clear.setMinimumWidth(0);
        clear.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        // 用带焦点态的底：电视遥控选中时要有可见反馈，纯色 GradientDrawable 会看不出焦点
        clear.setBackground(PiankeUi.focus(activity, false));
        clear.setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setTitle("清除片头片尾记忆？")
                .setMessage("已标记的片头、片尾秒数会全部清空；历史进度与收藏不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (d, w) -> {
                    SegmentSkipStore.clearAll(activity);
                    Toast.makeText(activity, "已清除片头片尾记忆", Toast.LENGTH_SHORT).show();
                    renderSegment(activity, body);
                })
                .show());
        body.addView(clear);
    }

    private static LinearLayout section(Activity activity, LinearLayout root, String title, String subtitle) {
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextSize(activity.getResources().getConfiguration().screenWidthDp >= 900 ? 21 : 18);
        heading.setTextColor(Color.WHITE);
        heading.setPadding(0, dp(activity, 14), 0, dp(activity, 2));
        root.addView(heading);
        root.addView(caption(activity, subtitle));

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body);
        return body;
    }

    private static void divider(Activity activity, LinearLayout root) {
        View line = new View(activity);
        line.setBackgroundColor(0xFF1E2A3A);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(activity, 1));
        params.setMargins(0, dp(activity, 14), 0, 0);
        root.addView(line, params);
    }

    private static TextView caption(Activity activity, String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(activity.getResources().getConfiguration().screenWidthDp >= 900 ? 15 : 13);
        view.setTextColor(0xFF8899AA);
        view.setPadding(0, dp(activity, 2), 0, dp(activity, 6));
        return view;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
