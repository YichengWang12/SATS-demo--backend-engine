package com.ethan.db;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.impl.factory.Sets;
import org.eclipse.collections.impl.map.mutable.primitive.LongLongHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static thirdpart.bean.MsgConstants.MULTI_FACTOR;

@Log4j2
@AllArgsConstructor
public class DbQuery {

    @NonNull
    private QueryRunner runner;

    /**
     * 查询会员ID
     *
     * @return
     * @throws Exception
     */
    public short[] queryAllMemberIds() throws Exception {
        List<Map<String, Object>> listmap = runner.query("select id from t_member where status=1", new MapListHandler());
        if (CollectionUtils.isEmpty(listmap)) {
            throw new Exception("member empty");
        }

        short[] memberIds = new short[listmap.size()];
        int i = 0;
        for (Map<String, Object> map : listmap) {
            memberIds[i] = Short.parseShort(map.get("id").toString());
            i++;
        }
        return memberIds;
    }


    /**
     * 查询资金
     *
     * @return
     * @throws Exception
     */
    public LongLongHashMap queryAllBalance() throws Exception {
        List<Map<String, Object>> listmap = runner.query("select uid,balance from t_user", new MapListHandler());
        if (CollectionUtils.isEmpty(listmap)) {
            throw new Exception("user data empty");
        }

        LongLongHashMap uidBalanceMap = new LongLongHashMap();
        for (Map<String, Object> map : listmap) {
            uidBalanceMap.put(
                    Long.parseLong(map.get("uid").toString()),
                    Long.parseLong(map.get("balance").toString()) * MULTI_FACTOR
            );
        }
        return uidBalanceMap;
    }


    /**
     * 查询股票
     *
     * @return
     * @throws Exception
     */
    public MutableSet<String> queryAllStockCode() throws Exception {
        List<Map<String, Object>> listmap = runner.query("select code from t_stock_us where status=1", new MapListHandler());
        if (CollectionUtils.isEmpty(listmap)) {
            throw new Exception("stock empty");
        }

        MutableSet<String> codes = Sets.mutable.empty();
        for (Map<String, Object> map : listmap) {
            codes.add(map.get("code").toString());
        }
        return codes;
    }




}
