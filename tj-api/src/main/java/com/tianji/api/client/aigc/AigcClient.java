package com.tianji.api.client.aigc;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(value = "aigc-client", contextId = "aigc")
public interface AigcClient {

    @PostMapping("/text")
    String chatText(@RequestBody String question);
}
