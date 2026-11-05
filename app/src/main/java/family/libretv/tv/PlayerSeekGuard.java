package family.libretv.tv;

import android.os.SystemClock;

/**
 * 区分「功能自己发起的跳转」和「用户手动跳转」。
 *
 * <p>广告跳过和片头片尾都会调用 {@code player.seekTo()}，又都监听同一个播放器的
 * {@code onPositionDiscontinuity}，回调本身看不出是谁发起的。各自在 seek 前 {@link #mark()}，
 * 对端用 {@link #isProgrammatic()} 判断，就不会把对方的跳转当成用户拖动去重置自己的状态
 * ——广告侧会清空已识别区间并重建音频流，片头片尾侧会取消正在走的片尾倒计时。
 *
 * <p>用时间窗而不是待处理计数：播放器对「跳到当前位置」这类无效 seek 不会回调，
 * 计数会永久残留，进而吞掉下一次真正的用户 seek。
 */
final class PlayerSeekGuard {
    /** seek 在同一线程同步回调，这里只需覆盖分发耗时；留余量给换源重准备 */
    private static final long WINDOW_MS = 1000L;

    private long until;

    /** 在调用 {@code player.seekTo()} 之前登记 */
    void mark() {
        until = SystemClock.elapsedRealtime() + WINDOW_MS;
    }

    /** 当前这一次位置跳变是否由功能发起（即不是用户拖动） */
    boolean isProgrammatic() {
        return SystemClock.elapsedRealtime() < until;
    }
}
