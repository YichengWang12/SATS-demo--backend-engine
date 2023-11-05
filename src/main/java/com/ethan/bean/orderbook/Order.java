package com.ethan.bean.orderbook;

import lombok.*;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import thirdpart.order.OrderDirection;

import java.util.Objects;

//只有在orderbook中使用
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@ToString
public final class Order {
    // 会员id
    private short mid;
    //用户id
    private long uid;
    // 股票代码
    private String code;
    // 买卖方向
    private OrderDirection direction;
    // 价格
    private long price;
    // 数量
    private long volume;
    // 已成交的量
    private long tradedVolume;
    // 委托编号
    private long oid;
    // 内部排序顺序
    private long innerOid;
    // 时间戳
    private long timestamp;

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17,37)
                .append(mid)
                .append(uid)
                .append(code)
                .append(direction)
                .append(price)
                .append(volume)
                .append(tradedVolume)
                .append(oid)
                .toHashCode();
    }

    @Override
    public boolean equals(Object o){
        if(o == this) return true;
        if(!(o instanceof Order)) return false;
        Order order = (Order) o;
        return new EqualsBuilder()
                .append(mid,order.mid)
                .append(uid,order.uid)
                .append(code,order.code)
                .append(direction,order.direction)
                .append(price,order.price)
                .append(volume,order.volume)
                .append(tradedVolume,order.tradedVolume)
                .append(oid,order.oid)
                .isEquals();
    }

}
