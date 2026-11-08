package com.tianji.aigc.tools.result;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.tianji.api.dto.course.CourseBaseInfoDTO;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.NumberUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseInfo {

    @JsonPropertyDescription("课程id")
    private Long CourseId;
    @JsonPropertyDescription("课程名字")
    private String name;
    @JsonPropertyDescription("课程价格，单位为元，货币为人民币")
    private double price;
    @JsonPropertyDescription("课程学习有效期，单位：月")
    private Integer validDuration;
    @JsonPropertyDescription("适用人群，例如：初学者")
    private String usePeople;
    @JsonPropertyDescription("课程详细介绍")
    private String detail;

    public static CourseInfo of(CourseBaseInfoDTO courseBaseInfo) {
        if (BeanUtils.isEmpty(courseBaseInfo)) {
            return null;
        }
        CourseInfo courseInfo = BeanUtils.copyBean(courseBaseInfo, CourseInfo.class);
        courseInfo.setPrice(Optional.ofNullable(courseBaseInfo.getPrice())
                .map(num -> num.doubleValue() / 100d)
                .map(num -> NumberUtils.round(num, 2).doubleValue())
                .orElse(0.0d));

        return courseInfo;
    }
}
