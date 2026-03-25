package com.tianji.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.SignResultVO;

import java.util.List;


public interface ISignRecordService{
    SignResultVO addSignRecords();

    List<Integer> getSignDays();
}
