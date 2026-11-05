package family.libretv.tv.adskip;

import android.content.Context;
import android.util.Log;

import family.libretv.tv.adskip.AdSkipConfig;
import com.k2fsa.sherpa.onnx.SherpaKwsEngine;

/**
 * 内嵌广告跳过的总协调器，进程内单例。
 *
 * <p>职责：
 * <ul>
 *   <li>持有识别引擎（模型只加载一次）与状态机（每集状态）；</li>
 *   <li>给播放器提供 PCM 探针；</li>
 *   <li>把"命中"翻译成一次 seek，并保证状态机同步感知这次跳转。</li>
 * </ul>
 */
public class AdSkipController {

    private static final String TAG = "AdSkip";

    public interface SeekHandler {
        /** 执行跳转 */
        void seekTo(long positionMs);

        /** 可选：给用户一个提示 */
        void onAdSkipped(long fromMs, long toMs, String keyword);
    }

    private static volatile AdSkipController instance;

    private final AdSkipStateMachine machine = new AdSkipStateMachine();
    private SherpaKwsEngine engine;
    private volatile SeekHandler seekHandler;
    private volatile boolean inited = false;

    private AdSkipController() {
    }

    public static AdSkipController get() {
        if (instance == null) {
            synchronized (AdSkipController.class) {
                if (instance == null) {
                    instance = new AdSkipController();
                }
            }
        }
        return instance;
    }

    /** 首次使用时初始化，重复调用无副作用 */
    public void init(Context context) {
        if (inited) return;
        synchronized (this) {
            if (inited) return;
            try {
                // 必须先读配置再建流：预热调用（入口 Activity）可能早于任何界面读取 prefs，
                // 若不在此处初始化配置，会以空关键词建流，之后 init 又因 inited=true 被跳过，
                // 表现为"引擎就绪但永远不命中"
                AdSkipConfig.init(context);
                Log.i(TAG, "init: 配置 enabled=" + AdSkipConfig.isEnabled()
                        + " 时长=" + AdSkipConfig.getSkipMillis() + "ms"
                        + " 关键词数=" + AdSkipConfig.getKeywordCount()
                        + " 关键词=" + AdSkipConfig.getKeywordList());
                engine = SherpaKwsEngine.get();
                engine.init(context);
                Log.i(TAG, "init: 引擎本体 ready=" + engine.isReadyRaw());
                engine.applyKeywords();
                Log.i(TAG, "init: 引擎含流 ready=" + engine.isReady());
                machine.attach(engine, new AdSkipStateMachine.Callback() {
                    @Override
                    public void onAdDetected(long fromMs, long toMs, String keyword) {
                        Log.i(TAG, ">>> 命中广告: 关键词=[" + keyword + "] 从 " + fromMs + "ms 跳到 " + toMs + "ms");
                        SeekHandler handler = seekHandler;
                        if (handler == null) {
                            Log.w(TAG, ">>> 命中但 SeekHandler 为空，无法跳转！");
                        } else {
                            handler.onAdSkipped(fromMs, toMs, keyword);
                            handler.seekTo(toMs);
                        }
                        // 以"程序化跳转"通知状态机：重置时钟但保留已跳区间，保证幂等
                        machine.onSeekTo(toMs, false);
                    }
                });
                inited = true;
            } catch (Throwable t) {
                Log.e(TAG, "init 异常", t);
                t.printStackTrace();
            }
        }
    }

    public boolean isEnabled() {
        return AdSkipConfig.isEnabled();
    }

    /**
     * 给播放器创建一个 PCM 探针。
     *
     * <p>注意：这里<b>不检查引擎是否就绪</b>。因为换集时播放器实例会被复用，
     * 不会再走 buildAudioSink，若首集引擎尚未加载完就返回 null，后续就永远补不上探针了。
     * 引擎采取异步懒加载，加载完成后自动生效；未就绪时状态机会跳过识别，不影响播放。
     */
    public androidx.media3.common.audio.AudioProcessor createTapProcessor() {
        // 注意：这里不看 enabled。播放器实例在换集时会复用，buildAudioSink 不会再次执行，
        // 若此刻因"功能未开启"返回 null，探针将永久缺失，用户之后在设置里打开功能也无法生效。
        // 探针本身只是透传 + 旁路拷贝，开销可忽略；是否真正识别由状态机根据 enabled 决定。
        Log.i(TAG, "createTapProcessor: 挂载探针 enabled=" + AdSkipConfig.isEnabled()
                + " inited=" + inited
                + " engineRawReady=" + (engine != null && engine.isReadyRaw()));
        return new AdSkipAudioProcessor(machine);
    }

    public void setSeekHandler(SeekHandler handler) {
        Log.i(TAG, "setSeekHandler: " + (handler != null ? "已绑定" : "置空"));
        this.seekHandler = handler;
    }

    // ------------------------------------------------------------ 生命周期

    /** 开始一次播放（含换集后的重新起播） */
    public void onPlaybackStart(long positionMs) {
        if (!inited) return;
        machine.start(positionMs);
    }

    /** 换集 / 换源：清空每集状态，但保留引擎 */
    public void onEpisodeChange() {
        if (!inited) return;
        machine.resetForNewMedia();
    }

    /** 播放位置跳转 */
    public void onSeek(long positionMs, boolean userInitiated) {
        if (!inited) return;
        machine.onSeekTo(positionMs, userInitiated);
    }

    /** 播放位置更新，用于校正音频时钟漂移 */
    public void onPositionUpdate(long positionMs) {
        if (!inited) return;
        machine.onPositionUpdate(positionMs);
    }

    public void onPaused() {
        if (!inited) return;
        machine.onPaused();
    }

    /** 用户修改了关键词或跳过时长 */
    public void onConfigChanged(Context context) {
        if (!inited) {
            init(context);
            return;
        }
        engine.applyKeywords();
        machine.onConfigChanged();
    }

    /** 退出播放页：销毁每集状态。引擎为进程级重量资源，保留以便下次秒进 */
    public void destroy() {
        machine.destroy();
        // 不再释放引擎：模型加载要数秒，且退出/进入播放页很频繁。
        // 只置 inited=false，下次进入播放页时重新绑定回调即可。
        inited = false;
    }
}
