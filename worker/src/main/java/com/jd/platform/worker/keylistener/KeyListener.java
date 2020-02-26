package com.jd.platform.worker.keylistener;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.base.Joiner;
import com.jd.platform.common.model.HotKeyModel;
import com.jd.platform.common.rule.IKeyRule;
import com.jd.platform.worker.model.KeyRuleHolder;
import com.jd.platform.worker.netty.pusher.IPusher;
import com.jd.platform.worker.tool.SlidingWindow;
import com.jd.platform.worker.tool.SystemClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * key的新增、删除处理
 *
 * @author wuweifeng wrote on 2019-12-12
 * @version 1.0
 */
@Component
public class KeyListener implements IKeyListener {
    @Resource(name = "allKeyCache")
    private Cache<String, Object> cache;
    @Resource(name = "hotKeyCache")
    private Cache<String, Object> hotCache;
    @Resource
    private List<IPusher> IPushers;

    private Logger logger = LoggerFactory.getLogger(getClass());


    @Override
    public void newKey(HotKeyModel hotKeyModel, KeyEventOriginal original) {
        //cache里的key
        String key = buildKey(hotKeyModel);
        //判断是不是刚热不久
        Object o = hotCache.getIfPresent(key);
        if (o != null) {
            return;
        }

        SlidingWindow slidingWindow = checkWindow(hotKeyModel, key);
        //看看hot没
        boolean hot = slidingWindow.addCount(hotKeyModel.getCount());
        if (!hot) {
            //如果没hot，重新put，cache会自动刷新过期时间
            cache.put(key, slidingWindow);
        } else {
            hotCache.put(key, 1);

            //开启推送
            hotKeyModel.setCreateTime(SystemClock.now());
            logger.info("new key created event key : " + hotKeyModel.toString());

            for (IPusher pusher : IPushers) {
                pusher.push(hotKeyModel);
            }

        }

    }

    @Override
    public void removeKey(HotKeyModel hotKeyModel, KeyEventOriginal original) {
        //cache里的key
        String key = buildKey(hotKeyModel);

        hotCache.invalidate(key);
        cache.invalidate(key);

        //推送所有client删除
        hotKeyModel.setCreateTime(SystemClock.now());
        logger.info("key delete event key : " + hotKeyModel.toString());

        for (IPusher pusher : IPushers) {
            pusher.remove(hotKeyModel);
        }

    }

    /**
     * 生成或返回该key的滑窗
     */
    private SlidingWindow checkWindow(HotKeyModel hotKeyModel, String key) {
        //取该key的滑窗
        SlidingWindow slidingWindow = (SlidingWindow) cache.getIfPresent(key);
        if (slidingWindow == null) {
            //是个新key，获取它的规则
            IKeyRule keyRule = KeyRuleHolder.getRuleByAppAndKey(hotKeyModel);
            slidingWindow = new SlidingWindow(keyRule.getKeyRule().getInterval(), keyRule.getKeyRule().getThreshold());
        }
        return slidingWindow;
    }

    private String buildKey(HotKeyModel hotKeyModel) {
        return Joiner.on("-").join(hotKeyModel.getAppName(), hotKeyModel.getKeyType(), hotKeyModel.getKey());
    }

}
