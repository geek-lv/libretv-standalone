package family.libretv.tv.safety;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 儿童锁的 PIN 输入框。
 *
 * <p>只做「验证已有 PIN」这一件事：真正的临时解锁由 {@link ContentFilterConfig#unlock()}
 * 记录，有效期 30 分钟且只存在于内存，进程结束自动重新锁定。
 */
public final class PinDialog {

    private PinDialog() {
    }

    /** 已解锁则直接执行；否则弹出 PIN 输入框 */
    public static void requireUnlock(Activity activity, Runnable onUnlocked) {
        ContentFilterConfig.init(activity);
        if (ContentFilterConfig.unlocked()) {
            onUnlocked.run();
            return;
        }
        if (!ContentFilterConfig.hasPin()) {
            Toast.makeText(activity, "请先在 设置 → 内容过滤 中设置 PIN", Toast.LENGTH_LONG).show();
            return;
        }
        show(activity, "内容已锁定", "输入 PIN 可临时解锁 30 分钟（仅本机生效）", onUnlocked);
    }

    /**
     * 只验证 PIN，不进入临时解锁状态。
     *
     * <p>用于关闭全局拦截这类"高危操作"：验证通过才执行，验证完仍然保持锁定。
     */
    public static void requireVerify(Activity activity, String title, String message, Runnable onSuccess) {
        ContentFilterConfig.init(activity);
        if (!ContentFilterConfig.hasPin()) {
            Toast.makeText(activity, "请先设置 PIN，关闭保护需要验证", Toast.LENGTH_LONG).show();
            return;
        }
        show(activity, title, message, onSuccess, false);
    }

    public static void show(final Activity activity, String title, String message, final Runnable onSuccess) {
        show(activity, title, message, onSuccess, true);
    }

    private static void show(final Activity activity, String title, String message,
                             final Runnable onSuccess, final boolean unlockAfter) {
        ContentFilterConfig.init(activity);
        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Math.round(22 * density), Math.round(8 * density),
                Math.round(22 * density), 0);

        TextView caption = new TextView(activity);
        caption.setText(message);
        caption.setTextSize(13);
        caption.setTextColor(0xFF8899AA);
        caption.setPadding(0, 0, 0, Math.round(10 * density));
        root.addView(caption);

        final EditText input = new EditText(activity);
        input.setHint("4–6 位数字");
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setGravity(Gravity.CENTER);
        input.setTextSize(22);
        input.setSingleLine(true);
        root.addView(input);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(root)
                .setNegativeButton("取消", null)
                .setPositiveButton("解锁", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String pin = input.getText().toString().trim();
                    if (ContentFilterConfig.verifyPin(pin)) {
                        if (unlockAfter) {
                            ContentFilterConfig.unlock();
                            Toast.makeText(activity, "已临时解锁 " + (ContentFilterConfig.UNLOCK_MILLIS / 60000) + " 分钟",
                                    Toast.LENGTH_SHORT).show();
                        }
                        dialog.dismiss();
                        if (onSuccess != null) onSuccess.run();
                    } else {
                        input.setText("");
                        input.setHint("PIN 不正确，请重试");
                        input.setHintTextColor(Color.parseColor("#ff7a7a"));
                    }
                }));
        dialog.show();
    }
}
