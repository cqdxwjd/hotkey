package com.jd.platform.hotkey.worker.netty.pusher;

import cn.hutool.core.collection.CollectionUtil;
import com.google.common.collect.Queues;
import com.jd.platform.hotkey.common.model.HotKeyModel;
import com.jd.platform.hotkey.common.tool.FastJsonUtils;
import com.jd.platform.hotkey.worker.netty.dashboard.DashboardHolder;
import com.jd.platform.hotkey.worker.netty.pusher.store.HotkeyTempStore;
import com.jd.platform.hotkey.worker.tool.AsyncPool;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 定时批量往dashboard发送热key，供入库
 * @author wuweifeng
 * @version 1.0
 * @date 2020-08-31
 */
@Component
public class KeyUploader {

    @PostConstruct
    public void uploadToDashboard() {
        AsyncPool.asyncDo(() -> {
            while (true) {
                try {
                    //要么key达到1千个，要么达到1秒，就汇总上报给etcd一次
                    List<HotKeyModel> tempModels = new ArrayList<>();
                    Queues.drain(HotkeyTempStore.getQueue(), tempModels, 1000, 1, TimeUnit.SECONDS);
                    if (CollectionUtil.isEmpty(tempModels)) {
                        continue;
                    }

                    //将热key推到dashboard
                    DashboardHolder.flushToDashboard(FastJsonUtils.convertObjectToJSON(tempModels));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
