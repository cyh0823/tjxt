package com.tianji.learning.controller;

import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.models.auth.In;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
@RequestMapping("sign-records")
@Api(tags = "签到相关接口")
@RequiredArgsConstructor
public class SignRecordController {

    private final ISignRecordService signRecordService;

    @PostMapping
    @ApiOperation("签到")
    public SignResultVO addSignRecords(){
        return signRecordService.addSignRecords();
    }

    @GetMapping
    @ApiOperation("查询本月签到记录")
    public List<Integer> getSignDays(){
        return signRecordService.getSignDays();
    }
}
