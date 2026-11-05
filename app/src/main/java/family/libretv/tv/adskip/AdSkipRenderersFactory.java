package family.libretv.tv.adskip;

import android.content.Context;
import android.util.Log;

import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

/**
 * 带广告识别探针的渲染器工厂（移植自 TVBoxOSC 的挂载方式）。
 *
 * <p>在音频输出链上挂一个旁路 PCM 探针用于内嵌口播广告识别。
 * 探针只做旁路拷贝，不改变任何音频输出；功能未开启或引擎不可用时完全走默认链路。
 *
 * <p>media3 与 exo2 的差异：{@code buildAudioSink} 去掉了 offload 参数；
 * {@link androidx.media3.common.audio.AudioProcessor} 已移到 common 包。
 */
@androidx.media3.common.util.UnstableApi
public final class AdSkipRenderersFactory extends DefaultRenderersFactory {

    private static final String TAG = "AdSkip";

    public AdSkipRenderersFactory(Context context) {
        super(context);
    }

    @Override
    protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
                                       boolean enableAudioTrackPlaybackParams) {
        try {
            androidx.media3.common.audio.AudioProcessor tap = AdSkipController.get().createTapProcessor();
            if (tap != null) {
                return new DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessorChain(new DefaultAudioSink.DefaultAudioProcessorChain(tap))
                        .build();
            }
        } catch (Throwable t) {
            Log.w(TAG, "buildAudioSink: 探针挂载失败，回退默认音频链路", t);
        }
        return super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams);
    }
}
