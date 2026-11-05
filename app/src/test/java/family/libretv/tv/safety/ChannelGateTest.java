package family.libretv.tv.safety;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * 直播频道判定：主要钉"正常频道不能被误杀"。
 *
 * <p>成人直播源大多整组命名，所以分组允许用软词；单个频道名只认强词。
 */
public class ChannelGateTest {

    @Test
    public void 央视卫视凤凰都不应命中() {
        assertNull(AdultRules.matchChannel("CCTV-1 综合", "央视"));
        assertNull(AdultRules.matchChannel("湖南卫视", "卫视"));
        assertNull(AdultRules.matchChannel("凤凰中文", "港澳台"));
        assertNull(AdultRules.matchChannel("高清电影", "电影"));
    }

    @Test
    public void 整组命名的成人分组应命中() {
        assertNotNull(AdultRules.matchChannel("某频道", "福利"));
        assertNotNull(AdultRules.matchChannel("某频道", "激情直播"));
    }

    @Test
    public void 频道名里的硬词应命中() {
        assertNotNull(AdultRules.matchChannel("伦理片影院", "电影"));
    }

    @Test
    public void 厂牌名频道应命中() {
        assertNotNull(AdultRules.matchChannel("麻豆直播", "其他"));
    }
}
