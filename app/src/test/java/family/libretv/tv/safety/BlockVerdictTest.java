package family.libretv.tv.safety;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 整块判定（先判后出）的边界测试。
 *
 * <p>重点不是"成人内容能不能被抓到"，而是"正经站点会不会被整站误杀"——
 * 误杀的代价是用户少了整整一个来源，比漏一条更刺眼。
 */
public class BlockVerdictTest {

    private static AdultSourceStats.Block block(int seen, int hits, int strong) {
        AdultSourceStats.Block block = new AdultSourceStats.Block();
        block.seen = seen;
        block.hits = hits;
        block.strongHits = strong;
        return block;
    }

    @Test
    public void 大目录站点偶有一条成人条目不应整站屏蔽() {
        // 综合站搜「嫁金钗」返回 20 条，其中 1 条分类写着伦理
        assertNull(AdultSourceStats.strictVerdict(block(20, 1, 1)));
    }

    @Test
    public void 整站卖成人内容一次搜索一片命中应立刻屏蔽() {
        assertNotNull(AdultSourceStats.strictVerdict(block(10, 6, 4)));
    }

    @Test
    public void 结果很少但两条硬证据也应屏蔽() {
        // 伪装分类的站只返回 2 条，但都是番号/厂牌样式
        assertNotNull(AdultSourceStats.strictVerdict(block(2, 2, 2)));
    }

    @Test
    public void 三条里有一条强命中达到三分之一即屏蔽() {
        assertNotNull(AdultSourceStats.strictVerdict(block(3, 1, 1)));
    }

    @Test
    public void 只有简介软命中不应整站屏蔽() {
        // 剧情简介里出现露骨词，属于软证据，不足以定性整个来源
        assertNull(AdultSourceStats.strictVerdict(block(4, 1, 0)));
    }

    @Test
    public void 干净响应不应屏蔽() {
        assertNull(AdultSourceStats.strictVerdict(block(15, 0, 0)));
    }

    @Test
    public void 强信号分类正确() {
        assertTrue(AdultRules.isStrongReason("分类含「伦理」"));
        assertTrue(AdultRules.isStrongReason("片名像番号「SSIS-123」"));
        assertTrue(AdultRules.isStrongReason("片名或标签含「麻豆」"));
        assertTrue(!AdultRules.isStrongReason("简介含「xxx」"));
        assertTrue(!AdultRules.isStrongReason(null));
    }
}
