package family.libretv.tv.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 判定规则的正负样本。
 *
 * <p>负样本比正样本重要：误杀一部正经剧会让整个功能不可用，所以把几部容易撞词的国产剧
 * 和讲成人产业的正剧都钉在这里。
 */
public class AdultRulesTest {

    @Test
    public void ordinaryTitlesPass() {
        assertNull(AdultRules.matchItem("流浪地球", "电影", "", "中国大陆", null, true, null));
        assertNull(AdultRules.matchItem("琅琊榜", "电视剧", "已完结", "中国大陆", null, true, null));
        assertNull(AdultRules.matchItem("激情燃烧的岁月", "电视剧", "", "中国大陆", null, true, null));
        assertNull(AdultRules.matchItem("裸婚时代", "电视剧", "", "中国大陆", null, true, null));
        assertNull(AdultRules.matchItem("复仇者联盟4", "电影", "HD", "美国", null, true, null));
        // 最严档：带「伦理」的分类一律拦（用户明确要求，家庭伦理剧也不看）
        assertNotNull(AdultRules.matchItem("某剧", "家庭伦理", "", "中国大陆", null, true, null));
        assertNotNull(AdultRules.matchItem("某剧", "都市伦理剧", "", "中国大陆", null, true, null));
    }

    @Test
    public void adultCategoriesBlocked() {
        assertNotNull(AdultRules.matchItem("某片", "伦理片", "", "", null, true, null));
        assertNotNull(AdultRules.matchItem("某片", "福利视频", "", "", null, true, null));
        assertNotNull(AdultRules.matchItem("某片", "无码专区", "", "", null, true, null));
    }

    @Test
    public void descriptionWordsBlockedOnlyWhenEnabled() {
        String desc = "本片讲述一段不堪回首的往事";
        assertNull(AdultRules.matchItem("某片", "电影", "", "", desc, true, null));
        assertNotNull(AdultRules.matchItem("某片", "电影", "", "", "片中大量性爱场面", true, null));
    }

    @Test
    public void numberStyleDetected() {
        assertNotNull(AdultRules.matchNumber("SSIS-123 中文字幕"));
        assertNotNull(AdultRules.matchNumber("FC2-PPV-1234567"));
        assertNull(AdultRules.matchNumber("复仇者联盟4-2019"));
        assertNull(AdultRules.matchNumber("HD-1080P 高清版"));
        assertNull(AdultRules.matchNumber("WEB-DL 1080P"));
        assertNull(AdultRules.matchItem("HD-1080P 高清版", "电影", "", "", null, true, null));
    }

    @Test
    public void exemptTitlesPass() {
        assertNull(AdultRules.matchItem("AV帝王", "电视剧", "剧情", "日本", "讲述成人影像行业的兴衰", true, null));
        assertNull(AdultRules.matchItem("全裸导演", "电视剧", "", "日本", null, true, null));
    }

    @Test
    public void ordinarySiteClassesPass() {
        List<String> classes = Arrays.asList("电影", "电视剧", "综艺", "动漫", "纪录片");
        assertNull(AdultRules.matchClasses(classes));
        assertNull(AdultRules.matchClasses(Arrays.asList("动作片", "喜剧片", "爱情片")));
        assertNull(AdultRules.matchClasses(null));
    }

    @Test
    public void adultSiteClassesBlocked() {
        assertNotNull(AdultRules.matchClasses(Arrays.asList("电影", "伦理片", "福利")));
        assertNotNull(AdultRules.matchClasses(Arrays.asList("伦理", "自拍", "偷拍", "写真")));
        // 两个软命中且占比超过四分之一
        assertNotNull(AdultRules.matchClasses(Arrays.asList("电影", "电视剧", "综艺", "主播", "人妻")));
        // 单个软命中不足以定性
        assertNull(AdultRules.matchClasses(Arrays.asList("电影", "电视剧", "综艺", "主播")));
    }

    @Test
    public void userKeywordsApply() {
        List<String> extra = Arrays.asList("某某厂牌", "特供");
        assertNotNull(AdultRules.matchItem("特供影片", "电影", "", "", null, false, extra));
        assertNull(AdultRules.matchItem("流浪地球", "电影", "", "", null, false, extra));
    }

    @Test
    public void sourceNameRules() {
        assertNull(AdultRules.matchSourceName("影视仓接口", null));
        assertNull(AdultRules.matchSourceName("", null));
        assertNotNull(AdultRules.matchSourceName("含羞草资源", null));
    }

    @Test
    public void reasonIsHumanReadable() {
        String reason = AdultRules.matchItem("某片", "伦理片", "", "", null, true, null);
        assertNotNull(reason);
        // 命中原因要能直接展示给用户
        assertEquals(true, reason.contains("伦理") || reason.contains("分类"));
    }

    // ---------------------------------------------------- 低俗倾向词（组合判定）

    @Test
    public void singleSuggestiveWordBlocked() {
        // 最严档：标题命中 1 个低俗词就拦
        assertNotNull(AdultRules.matchItem("性感都市", "电影", "HD", "香港", null, true, null));
        assertNotNull(AdultRules.matchItem("美女的诱惑", "电影", "HD", "国产", null, true, null));
        // 极少数只剩低俗词的正当片名，靠白名单兜住
        assertNull(AdultRules.matchItem("极品家丁", "国产剧", "全40集", "大陆", null, true, null));
        assertNull(AdultRules.matchItem("美女与野兽", "电影", "HD", "美国", null, true, null));
    }

    @Test
    public void suggestiveWordsBlockWhenStacked() {
        // 低俗标题是堆词堆出来的，命中多个不同词就定性
        assertNotNull(AdultRules.matchItem("极品美女勾引我", "电影", "HD", "国产", null, true, null));
        assertNotNull(AdultRules.matchItem("少妇的诱惑", "电影", "HD", "国产", null, true, null));
        assertNotNull(AdultRules.matchItem("性感空姐的秘密", "电影", "HD", "国产", null, true, null));
    }

    @Test
    public void strictModeBlocksSingleWord() {
        // 严格档为唯一档：7 参调用与显式 true 行为一致
        assertNotNull(AdultRules.matchItem("空姐的私密日记", "电影", "", "国产", null, true, null));
        assertNotNull(AdultRules.matchItem("空姐的私密日记", "电影", "", "国产", null, true, null, true));
    }

    @Test
    public void descriptionNeedsTwoSuggestiveWords() {
        // 简介是长文本，偶发一个倾向词很正常；2 个不同词才判
        assertNull(AdultRules.matchItem("某剧", "国产剧", "", "",
                "讲述一个美女在职场成长的故事", true, null));
        assertNotNull(AdultRules.matchItem("某剧", "国产剧", "", "",
                "美女勾引上司，性感诱惑不断，极品少妇的私密往事", true, null));
    }

    @Test
    public void exemptTitlesStillPass() {
        // 白名单里的正经片不受新规则影响
        assertNull(AdultRules.matchItem("AV帝王", "日剧", "", "日本", null, true, null));
    }
}
