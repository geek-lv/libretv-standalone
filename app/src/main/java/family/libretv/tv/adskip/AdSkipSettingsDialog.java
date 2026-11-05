package family.libretv.tv.adskip;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 广告跳过设置对话框（手机 / 电视共用）：自定义触发关键词 + 跳过时长。
 *
 * <p>移植自 TVBoxOSC 的「关键词对话框 + 跳过时长对话框」两件套，合并为一个入口。
 * 保存后立即通过 {@link AdSkipController#onConfigChanged} 重建识别流，无需重启播放。
 */
public final class AdSkipSettingsDialog {

    /** 可选的跳过时长（秒）；选 0 即关闭该功能 */
    private static final int[] DURATIONS = {0, 15, 30, 45, 60, 90};

    private AdSkipSettingsDialog() {
    }

    public static void show(final Activity activity) {
        AdSkipConfig.init(activity);
        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Math.round(20 * density), Math.round(10 * density),
                Math.round(20 * density), Math.round(10 * density));
        final Runnable[] saver = {bind(activity, root, null)};

        // 独立对话框沿用原来的「保存即生效」；页内保存按钮只在合并页出现
        new AlertDialog.Builder(activity)
                .setTitle("广告跳过设置")
                .setView(root)
                .setPositiveButton("保存", (d, w) -> saver[0].run())
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 把广告跳过控件渲染进调用方给的容器，返回「保存当前输入」的动作。
     *
     * <p>独立对话框交给对话框按钮执行；合并设置页没有对话框按钮，
     * 传入 {@code onSaved} 时会在页内加一个显式保存按钮。
     */
    public static Runnable bind(final Activity activity, final LinearLayout root, final Runnable onSaved) {
        AdSkipConfig.init(activity);
        final float density = activity.getResources().getDisplayMetrics().density;

        final TextView caption = new TextView(activity);
        caption.setText("命中以下口播关键词时自动向前跳过（口播广告烧录在音轨里，本地离线识别，不上传任何音频）");
        caption.setTextSize(13);
        caption.setTextColor(0xFF8899AA);
        caption.setPadding(0, 0, 0, Math.round(10 * density));
        root.addView(caption);

        final EditText keywords = new EditText(activity);
        keywords.setHint("广告关键词，多个用逗号分隔\n如：澳门新葡京,打开APP领取");
        keywords.setText(AdSkipConfig.getKeywordsRaw());
        keywords.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keywords.setMinLines(2);
        keywords.setMaxLines(4);
        keywords.setGravity(Gravity.TOP);
        keywords.setSelection(keywords.getText().length());
        root.addView(keywords);

        final TextView durationLabel = new TextView(activity);
        durationLabel.setText("命中后跳过时长：当前 " + describeDuration(AdSkipConfig.getSkipSeconds()));
        durationLabel.setTextSize(14);
        durationLabel.setTextColor(Color.WHITE);
        durationLabel.setPadding(0, Math.round(12 * density), 0, Math.round(6 * density));
        root.addView(durationLabel);

        final int[] selected = {AdSkipConfig.getSkipSeconds()};
        LinearLayout durationRow = new LinearLayout(activity);
        durationRow.setOrientation(LinearLayout.HORIZONTAL);
        final Button[] chips = new Button[DURATIONS.length];
        for (int i = 0; i < DURATIONS.length; i++) {
            final int value = DURATIONS[i];
            Button chip = new Button(activity, null, android.R.attr.borderlessButtonStyle);
            chip.setText(value == 0 ? "关" : value + "s");
            chip.setTextSize(13);
            chip.setMinWidth(0);
            chip.setMinimumWidth(0);
            chip.setPadding(Math.round(10 * density), Math.round(6 * density),
                    Math.round(10 * density), Math.round(6 * density));
            chip.setTextColor(value == selected[0] ? Color.WHITE : 0xFF8899AA);
            chip.setOnClickListener(v -> {
                selected[0] = value;
                durationLabel.setText("命中后跳过时长：当前 " + describeDuration(value));
                for (int j = 0; j < chips.length; j++) {
                    chips[j].setTextColor(DURATIONS[j] == value ? Color.WHITE : 0xFF8899AA);
                }
            });
            chips[i] = chip;
            durationRow.addView(chip, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        root.addView(durationRow);

        TextView note = new TextView(activity);
        note.setText("提示：跳过后若仍在广告中会连续再跳，直到回到正片；拖动进度条会重新校准。");
        note.setTextSize(12);
        note.setTextColor(0xFF667788);
        note.setPadding(0, Math.round(8 * density), 0, 0);
        root.addView(note);

        Runnable saver = () -> saveNow(activity, keywords.getText().toString(), selected[0]);
        if (onSaved != null) {
            Button save = new Button(activity, null, android.R.attr.borderlessButtonStyle);
            save.setText("保存广告跳过设置");
            save.setTextSize(14);
            save.setTextColor(Color.WHITE);
            save.setMinWidth(0);
            save.setMinimumWidth(0);
            save.setPadding(Math.round(6 * density), Math.round(10 * density),
                    Math.round(6 * density), Math.round(10 * density));
            save.setOnClickListener(v -> {
                saver.run();
                onSaved.run();
            });
            root.addView(save);
        }
        return saver;
    }

    /** 写入关键词与跳过时长并立即重建识别流；0 秒即关闭 */
    private static void saveNow(Activity activity, String raw, int seconds) {
        AdSkipConfig.setKeywordsRaw(raw.trim());
        AdSkipConfig.setSkipSeconds(seconds);
        AdSkipController.get().onConfigChanged(activity);
        int count = AdSkipConfig.getKeywordCount();
        Toast.makeText(activity, seconds == 0 ? "广告跳过已关闭"
                : "已保存：" + count + " 个关键词 · 跳过 " + seconds + " 秒", Toast.LENGTH_SHORT).show();
    }

    private static String describeDuration(int seconds) {
        return seconds == 0 ? "关闭" : seconds + " 秒";
    }
}
