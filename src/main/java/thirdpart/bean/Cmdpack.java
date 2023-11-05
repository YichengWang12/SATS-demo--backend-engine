package thirdpart.bean;

import lombok.*;
import thirdpart.order.OrderCmd;

import java.io.Serializable;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Cmdpack implements Serializable {

    private long packNo;

    private List<OrderCmd> orderCmds;
}
