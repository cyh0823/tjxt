package com.tianji.promotion.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class ChangeStatusMessage {
    Long couponId;
    boolean isBegin;
}
