package com.tianji.aigc.constant;

public interface Constant {

    String USER_ID = "userId";
    String REQUEST_ID = "requestId";
    String STOP = "STOP";
    String ID = "ID";

    interface Tools {
        String QUERY_COURSE_BY_ID = "根据课程id查询课程详细信息";
        String PRE_PLACE_ORDER = "购买课程预下单操作";
    }

    interface ToolParams {
        String COURSE_ID = "课程id";
        String COURSE_IDS = "课程id列表";
    }

    interface Prefix {
        String COURSE_PREFIX = "courseId_";
        String ORDER_PREFIX = "orderId_";
        String MEMORY_PREFIX = "memory_";
    }

}
