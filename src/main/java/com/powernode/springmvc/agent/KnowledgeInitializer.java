package com.powernode.springmvc.agent;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeInitializer {

    private final VectorStore vectorStore;

    public KnowledgeInitializer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void init() {
        List<Document> documents = List.of(
                new Document("【西安-兵马俑】位于西安市临潼区，是世界八大奇迹之一，规模宏大的秦代陶俑军阵。适合历史爱好者与全家出游，门票120元，建议游览3小时。特色美食有肉夹馍、羊肉泡馍。"),
                new Document("【西安-大唐不夜城】位于西安市雁塔区，以盛唐文化为主题的步行街，夜景璀璨。适合喜欢古风与夜景的游客，门票免费，建议游览2小时。特色美食有甑糕、油泼面。"),
                new Document("【重庆-洪崖洞】位于重庆市渝中区，依山而建的吊脚楼建筑群，夜景酷似千与千寻场景。适合拍照打卡与夜景爱好者，门票免费，建议游览2小时。特色美食有火锅、小面。"),
                new Document("【重庆-磁器口古镇】位于重庆市沙坪坝区，明清风格的古镇，充满巴渝民俗风情。适合喜欢慢逛与小吃的游客，门票免费，建议游览2小时。特色美食有陈麻花、鸡杂。"),
                new Document("【杭州-西湖】位于杭州市西湖区，中国十大名胜之一，湖光山色风景秀丽。适合休闲散步与自然爱好者，门票免费，建议游览半天。特色美食有西湖醋鱼、龙井虾仁。"),
                new Document("【杭州-灵隐寺】位于杭州市西湖区，历史悠久的佛教古刹，香火旺盛环境清幽。适合祈福与禅意文化爱好者，门票+飞来峰75元，建议游览2小时。特色美食有素面、桂花糕。"),
                new Document("【成都-成都大熊猫繁育研究基地】位于成都市成华区，是全球知名的大熊猫科研繁育与保护基地。适合亲子、动物爱好者，门票55元，建议游览3小时。特色美食有蛋烘糕、冷吃兔。"),
                new Document("【成都-武侯祠】位于成都市武侯区，是中国唯一的君臣合祀祠庙，三国文化的核心地标。适合三国迷、人文爱好者，门票50元，建议游览2小时。特色美食有三大炮、张飞牛肉。"),
                new Document("【成都-锦里古街】位于成都市武侯区，紧邻武侯祠的川西民俗古街，夜景灯火璀璨。适合拍照打卡、体验民俗，门票免费，建议游览2小时。特色美食有糖油果子、钵钵鸡。")
        );
        vectorStore.add(documents);
        System.out.println("知识库初始化完成，共加载 " + documents.size() + " 条数据");
    }
}
